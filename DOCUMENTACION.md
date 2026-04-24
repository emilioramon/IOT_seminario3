# Documentación de la solución

Sistema de tráfico inteligente para la simulación de una smart city (PTPaterna)
sobre el broker MQTT de la UPV. La plataforma modela vehículos, señales,
semáforos físicos y un servicio de orquestación que coordina corredores de
emergencia y reacciona ante accidentes.

Todos los dispositivos y servicios se comunican exclusivamente sobre MQTT.
No se expone ninguna API REST: aunque la librería `org.restlet` está
disponible en `lib/`, en la implementación actual no se levanta ningún
endpoint HTTP. La "API" de cada dispositivo se define por los tópicos MQTT
a los que publica y a los que se suscribe, y por el contrato JSON de los
mensajes que intercambia. A lo largo del documento cada dispositivo/servicio
se describe en términos de esos tópicos y mensajes.

A partir del trabajo previo, la solución se extiende con una integración
con **AWS IoT Core** a través de **Device Shadow**, que mantiene en la nube
un reflejo del estado del semáforo físico sin desplazar el control real
fuera de tambori.

---

## 1. Arquitectura general

El ecosistema se organiza en cuatro grandes familias de componentes:

- **Vehículos**: `SmartCar` (privado) y `EmergencyVehicle` (ambulancias,
  policía, bomberos). Publican su entrada y salida de vías, notifican
  incidentes y escuchan señales de tráfico relevantes para su ruta.
- **Señales de tráfico**: `SpeedLimitSign` (límite de velocidad) y
  `TrafficLightSign` (semáforo). Ambas descienden de `TrafficSign` y
  publican su estado al canal de señales de la vía.
- **Dispositivo físico**: `TrafficLightDeviceApp`, que se ejecuta en una
  Raspberry Pi con pi4j. Controla tres LEDs reales (rojo, ámbar, verde) y
  escucha comandos de cambio de estado.
- **Servicios**: `CorridorOrchestrationService`, que detecta accidentes y
  emergencias, reduce velocidad en la vía afectada y abre/cierra corredores
  verdes para vehículos de emergencia.

El broker principal es `tcp://tambori.dsic.upv.es:10083`. Sobre él se
intercambia el 100% del tráfico operativo. En paralelo, los procesos que
lo deseen pueden abrir una segunda conexión TLS con AWS IoT Core para
reflejar el estado del semáforo físico en un Device Shadow, sin que esto
altere la operativa.

### Diagrama de alto nivel

```
          ┌───────────────────────────────────────────────┐
          │              tambori.dsic.upv.es              │
          │                  (broker MQTT)                │
          └───────────────────────────────────────────────┘
               ▲      ▲        ▲        ▲        ▲
               │      │        │        │        │
    ┌──────────┘      │        │        │        └──────────┐
    │                 │        │        │                   │
 SmartCars       EmergencyV.  SpeedLim. TrafficLight   CorridorOrchestr.
    │                 │        │           (device fisico)      │
    │                 │        │               │                │
    └───── publican   └────────┴───── publican  │ publica        │
           VEHICLE_IN         EMERGENCY_        │ TRAFFIC_SIGNAL │
           VEHICLE_OUT        ALERT             │ (LightState)   │ publica
           ROAD_INCIDENT                        │                │ signals
                                                │                │ + EMERGENCY
                                                ▼                ▼
                                              AWS IoT Core Shadow (opcional)
                                              thing: ttmi010
```

---

## 2. Convención de tópicos MQTT

Todos los tópicos cuelgan de un prefijo común:

```
es/upv/pros/tatami/smartcities/traffic/PTPaterna
```

A partir de ahí, los canales relevantes son:

| Tópico | Dirección | Tipo de mensaje | Publicado por | Consumido por |
|---|---|---|---|---|
| `.../road/<road>/traffic` | entrada/salida de vehículos | `TRAFFIC` | SmartCar, EmergencyVehicle | (sin consumidor actual) |
| `.../road/<road>/alerts` | incidentes en la vía | `ROAD_INCIDENT` (o antiguo `{"event":"accident"}`) | SmartCar (`notifyIncident`) | CorridorOrchestrationService |
| `.../road/<road>/signals` | señales dirigidas a la vía | `TRAFFIC_SIGNAL` | SpeedLimitSign, TrafficLightSign, CorridorOrchestrationService | SmartCar (vía `SmartCar_RoadInfoSubscriber`) |
| `.../road/<road>/info` | info de vía (reservado) | libre | — | SmartCar |
| `.../traffic-light/<deviceId>/control` | comandos al semáforo físico | `TRAFFIC_LIGHT_COMMAND` | CorridorOrchestrationService (vía `RemoteTrafficLightSign`) | TrafficLightDeviceApp |
| `.../emergency/alerts` | arranque/fin de emergencia | `EMERGENCY_ALERT` | EmergencyVehicle | CorridorOrchestrationService |

El parámetro `<road>` es el identificador de la vía (por ejemplo `R3` o el
tramo `R3s1`). El parámetro `<deviceId>` identifica al semáforo físico
(por ejemplo `TL.R3s1.main`).

Los tópicos de AWS IoT Device Shadow se documentan en su propia sección
(ver §6).

---

## 3. Formato general de los mensajes

Casi todos los mensajes comparten un mismo sobre JSON:

```json
{
  "id":        "MSG_<epoch_ms>",
  "type":      "<TIPO_DE_MENSAJE>",
  "timestamp": 1777052374000,
  "msg": {
    ...
  }
}
```

Los campos `id`, `type` y `timestamp` son obligatorios para todos los
tipos de mensajes que usan este sobre. El contenido específico va dentro
de `msg`. La excepción es el antiguo formato de incidente (se verá abajo),
que es un único objeto plano sin sobre.

### Tipos de mensaje

- `TRAFFIC` — movimiento de un vehículo (entrada o salida).
- `ROAD_INCIDENT` — accidente u otro incidente en la vía.
- `TRAFFIC_SIGNAL` — estado actual de una señal o un semáforo.
- `TRAFFIC_LIGHT_COMMAND` — orden enviada a un semáforo físico.
- `EMERGENCY_ALERT` — inicio o fin de una emergencia con vehículo prioritario.

---

## 4. API MQTT de cada componente

Cada componente se define por los tópicos que publica, los que escucha y
los mensajes que produce o consume.

### 4.1 SmartCar

Vehículo privado normal. Al crearse, abre una conexión MQTT con el broker
a través de `SmartCar_TrafficNotifier`.

**Publica**

- `.../road/<road>/traffic` al entrar en una vía:

  ```json
  {
    "id": "MSG_...",
    "type": "TRAFFIC",
    "timestamp": "1777052374000",
    "msg": {
      "action":       "VEHICLE_IN",
      "road":         "R3s1",
      "road-segment": "R3s1",
      "vehicle-id":   "SmartCar-1234ABC",
      "position":     "100",
      "role":         "PrivateVehicle"
    }
  }
  ```

  Al salir de la vía publica el mismo sobre con `"action": "VEHICLE_OUT"`.

- `.../road/<road>/alerts` al notificar un incidente desde el propio
  vehículo (`notifyIncident`):

  ```json
  {
    "id": "MSG_...",
    "type": "ROAD_INCIDENT",
    "timestamp": 1777052374000,
    "msg": {
      "rt":                "traffic::alert",
      "incident-type":     "TRAFFIC_ACCIDENT",
      "id":                "SmartCar-1234ABC",
      "road":              "R3s1",
      "road-segment":      "R3s1",
      "starting-position": 100,
      "ending-position":   100,
      "description":       "Vehicle Crash",
      "status":            "Active"
    }
  }
  ```

**Se suscribe**

Cuando el vehículo entra en una vía, crea un `SmartCar_RoadInfoSubscriber`
que escucha dos tópicos de esa vía:

- `.../road/<road>/signals` — para reaccionar a las señales de tráfico.
  Si recibe un `TRAFFIC_SIGNAL` con `signal-type: "SPEED_LIMIT"`, ajusta
  su velocidad al mínimo entre `value` (límite de la señal) y
  `road-speed-limit` (límite físico de la vía).
- `.../road/<road>/info` — canal libre para información general de la vía.
  Ahora mismo está reservado y no tiene consumidores específicos.

### 4.2 EmergencyVehicle

Especialización de `SmartCar` para ambulancias, policía o bomberos.
Mantiene todo el protocolo del `SmartCar` (entrada/salida de vía,
alertas) y añade la capacidad de disparar y cerrar corredores verdes.

**Publica adicionalmente**

- `.../emergency/alerts` con `type: "EMERGENCY_ALERT"`, tanto al iniciar
  como al finalizar la emergencia:

  ```json
  {
    "id": "MSG_...",
    "type": "EMERGENCY_ALERT",
    "timestamp": 1777052374000,
    "msg": {
      "event":        "EMERGENCY_START",
      "vehicle-id":   "AMB-001",
      "vehicle-type": "AMBULANCE",
      "level":        "HIGH",
      "current-road": "R5s1",
      "current-km":   50,
      "destination":  "R3s1"
    }
  }
  ```

  Al cerrar la emergencia se repite el mensaje con `"event":
  "EMERGENCY_END"`.

**Particularidad de comportamiento**

Cuando está en emergencia activa, ignora las reducciones de velocidad que
le lleguen por el canal de señales. Un accidente en la vía no debe frenar
a la propia ambulancia que va a atenderlo.

### 4.3 SpeedLimitSign

Señal de límite de velocidad. Hereda de `TrafficSign` y publica su estado
al canal de señales de la vía.

**Publica**

- `.../road/<road>/signals` con `type: "TRAFFIC_SIGNAL"` y dentro
  `signal-type: "SPEED_LIMIT"`:

  ```json
  {
    "id": "MSG_...",
    "type": "TRAFFIC_SIGNAL",
    "timestamp": 1777052374000,
    "msg": {
      "rt":                "traffic-signal",
      "id":                "SL_atR3s1_100",
      "road":              "R3",
      "road-segment":      "R3s1",
      "signal-type":       "SPEED_LIMIT",
      "starting-position": 100,
      "ending-position":   100,
      "value":             "030",
      "road-speed-limit":  60
    }
  }
  ```

El campo `value` es el límite que impone la señal (en km/h, con 3
dígitos, rellenado con ceros por la izquierda). `road-speed-limit` es el
límite físico de la vía. La clase invariante que sign < road se verifica
en el constructor.

### 4.4 TrafficLightSign

Semáforo. Hereda de `TrafficSign` y publica su estado codificado como
tres caracteres (`H` = encendido, `L` = apagado) correspondientes a
rojo / ámbar / verde:

| Estado | Código | Interpretación |
|---|---|---|
| `RED`   | `HLL` | STOP |
| `AMBER` | `LHL` | precaución |
| `GREEN` | `LLH` | paso libre |

**Publica**

- `.../road/<road>/signals` con `signal-type: "TRAFFIC_LIGHT"`:

  ```json
  {
    "id": "MSG_...",
    "type": "TRAFFIC_SIGNAL",
    "timestamp": 1777052374000,
    "msg": {
      "rt":                "traffic-signal",
      "id":                "TL_atR3s1_100",
      "road":              "R3",
      "road-segment":      "R3s1",
      "signal-type":       "TRAFFIC_LIGHT",
      "starting-position": 100,
      "ending-position":   100,
      "value":             "HLL"
    }
  }
  ```

### 4.5 RemoteTrafficLightSign (proxy)

Variante de `TrafficLightSign` usada por el orquestador cuando el
semáforo es un dispositivo físico (Raspberry Pi). En lugar de publicar
en el canal de señales, envía un comando al dispositivo.

**Publica**

- `.../traffic-light/<deviceId>/control` con `type: "TRAFFIC_LIGHT_COMMAND"`:

  ```json
  {
    "type":      "TRAFFIC_LIGHT_COMMAND",
    "command":   "SET_STATE",
    "state":     "GREEN",
    "timestamp": 1777052374000
  }
  ```

Opcionalmente, si tiene un `TrafficLightShadow` asociado, publica también
`desired: {light: GREEN}` al Device Shadow de AWS IoT (ver §6).

### 4.6 TrafficLightDeviceApp (dispositivo físico)

Proceso Java que se ejecuta en la Raspberry Pi. Controla los tres LEDs
por GPIO con pi4j y actúa como endpoint de los comandos enviados por
`RemoteTrafficLightSign`.

**Se suscribe**

- `.../traffic-light/<deviceId>/control` — escucha comandos
  `TRAFFIC_LIGHT_COMMAND`. Cuando recibe un `SET_STATE`, actualiza el
  estado interno, enciende/apaga los LEDs físicos y publica el nuevo
  estado en el canal de señales de la vía.

**Publica**

- `.../road/<road>/signals` — reutiliza el formato `TRAFFIC_SIGNAL` de
  `TrafficLightSign` (mismo `value` de tres caracteres).
- `$aws/things/<thing>/shadow/update` — opcional, sólo si las variables
  de entorno `AWS_IOT_*` están definidas. Publica el `reported` del
  Shadow para reflejar el estado real en la nube (ver §6).

**Argumentos de ejecución**

```
java -jar traffic-light-device.jar <deviceId> <brokerURL> <road> <roadSegment> <startPos> <endPos> [initialState]
```

Ejemplo:

```
java -jar traffic-light-device.jar TL.R3s1.main \
    tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED
```

### 4.7 CorridorOrchestrationService

Servicio central. Escucha la vía en busca de accidentes y emergencias y
actúa sobre los semáforos para abrir el corredor verde que necesita un
vehículo prioritario. También impone un límite de 30 km/h en la vía
afectada por un accidente.

**Se suscribe**

- `.../emergency/alerts` — cuando llega un `EMERGENCY_START`, busca la
  ruta asociada al vehículo y pone en verde los semáforos del corredor
  y en rojo los conflictivos de cada cruce. Con `EMERGENCY_END`
  restablece el estado anterior.
- `.../road/+/alerts` — reacciona a cualquier accidente en cualquier vía.
  Acepta dos formatos:
  - el sobre nuevo con `type: "ROAD_INCIDENT"` y
    `msg.incident-type: "TRAFFIC_ACCIDENT"`,
  - el antiguo formato plano `{"event":"accident", "road":"...", "kp":...}`,
    que se mantiene por compatibilidad.

**Publica**

- `.../road/<road>/signals` — al detectar un accidente, emite un
  `TRAFFIC_SIGNAL` de tipo `SPEED_LIMIT` con `value: 030` y un
  `road-speed-limit` que actúa como tope suave (se pone 100 por
  convención).
- `.../traffic-light/<deviceId>/control` — indirectamente, cuando cambia
  el estado de un semáforo que es un `RemoteTrafficLightSign`.

**API Java expuesta a integradores**

El servicio ofrece un punto de extensión en forma de listener para que
un cliente (por ejemplo el simulador `CombinedSimulationApp`) pueda
reaccionar al evento de accidente más allá de la propia reducción de
velocidad:

```java
orchestrator.setAccidentListener((road, km) -> {
    // despachar ambulancia, registrar en log, etc.
});
```

Este listener es lo que permite encadenar la detección del accidente con
el arranque automático de la ambulancia en la simulación combinada.

---

## 5. Interacciones entre componentes

Las interacciones clave del sistema se resumen en tres escenarios.

### 5.1 Entrada y salida normal de un vehículo

```
SmartCar                             tambori                         (nadie)
   │                                    │
   │  publish VEHICLE_IN                │
   │  road/R3s1/traffic                 │
   ├───────────────────────────────────►│
   │                                    │
   │  subscribe road/R3s1/signals       │
   │  subscribe road/R3s1/info          │
   ├───────────────────────────────────►│
   │                                    │
   │  publish VEHICLE_OUT               │
   │  road/R3s1/traffic                 │
   ├───────────────────────────────────►│
   │  unsubscribe ...                   │
   ├───────────────────────────────────►│
```

La vía se limita a transportar los mensajes. La reacción a esos
`VEHICLE_IN`/`VEHICLE_OUT` se puede añadir en el futuro conectando un
consumidor al tópico `.../road/<road>/traffic`.

### 5.2 Accidente en la vía

```
SmartCar-A           tambori         Orchestration          Otros SmartCars
    │                  │                  │                       │
    │ notifyIncident() │                  │                       │
    │ publish          │                  │                       │
    │ road/R3s1/alerts │                  │                       │
    ├─────────────────►│                  │                       │
    │                  │ deliver          │                       │
    │                  ├─────────────────►│                       │
    │                  │                  │ handleAccidentOnRoad  │
    │                  │                  │ (road=R3s1, km=100)   │
    │                  │                  │                       │
    │                  │    publish TRAFFIC_SIGNAL                │
    │                  │    road/R3s1/signals, value=030          │
    │                  │◄─────────────────┤                       │
    │                  │                  │                       │
    │                  ├──────────────────────────────────────────►│
    │                  │                                          │
    │                                          adjustSpeed(30,100)│
    │                                          => frena a 30 km/h │
```

El `accidentListener` se dispara en paralelo. En la simulación combinada
ese callback es el que despacha la ambulancia.

### 5.3 Corredor verde con ambulancia

```
Ambulance (EmergencyVehicle)   tambori    Orchestration   Semáforos del corredor
     │                            │              │                 │
     │ startEmergency("R3s1")     │              │                 │
     │ publish EMERGENCY_START    │              │                 │
     │ emergency/alerts           │              │                 │
     ├───────────────────────────►│              │                 │
     │                            │ deliver      │                 │
     │                            ├─────────────►│                 │
     │                            │              │ openCorridor()  │
     │                            │              │                 │
     │                            │              │ setState GREEN  │
     │                            │              ├────────────────►│ (corredor)
     │                            │              │ setState RED    │
     │                            │              ├────────────────►│ (cruces)
     │  ... ambulancia circula ... │             │                 │
     │                            │              │                 │
     │ endEmergency()             │              │                 │
     │ publish EMERGENCY_END      │              │                 │
     ├───────────────────────────►│              │                 │
     │                            ├─────────────►│ closeCorridor() │
     │                            │              │ setState RED    │
     │                            │              ├────────────────►│ (corredor)
     │                            │              │ setState GREEN  │
     │                            │              ├────────────────►│ (cruces)
```

Si el semáforo del corredor es remoto (Raspberry Pi), el
`setState(...)` no publica directamente en el canal de señales, sino que
envía un `TRAFFIC_LIGHT_COMMAND` al tópico de control del dispositivo.
El dispositivo cambia los LEDs y publica a su vez el nuevo estado en el
canal de señales.

---

## 6. Integración con AWS IoT Core — Device Shadow

Como extensión opcional, el semáforo físico (`TL.R3s1.main`, asociado al
Thing `ttmi010` en AWS) reporta su estado al Device Shadow de AWS IoT.
Tambori sigue siendo el canal operativo: AWS se usa para mantener una
copia fiel del estado en la nube, con soporte nativo para timestamps,
versionado y tolerancia a desconexiones.

El servicio de orquestación publica el estado **deseado** (qué pide al
semáforo). El dispositivo publica el estado **reportado** (qué muestra
realmente). Si ambos convergen, el Shadow queda "sincronizado"; si no,
AWS emite un mensaje `delta` con la diferencia.

### Tópicos Shadow

El Thing al que se asocia es `ttmi010`, de modo que los tópicos
relevantes son:

| Tópico | Dirección | Uso |
|---|---|---|
| `$aws/things/ttmi010/shadow/update` | pub | publicar `desired` y/o `reported` |
| `$aws/things/ttmi010/shadow/update/accepted` | sub | confirmación de cada update |
| `$aws/things/ttmi010/shadow/update/rejected` | sub | error en un update |
| `$aws/things/ttmi010/shadow/update/delta` | sub | aparece cuando `desired` ≠ `reported` |

### Formato del documento Shadow

Ejemplo de update enviado por el dispositivo al cambiar a verde:

```json
{
  "state": {
    "reported": { "light": "GREEN", "code": "LLH" }
  }
}
```

Ejemplo del update enviado por el servicio de orquestación al abrir
corredor:

```json
{
  "state": {
    "desired": { "light": "GREEN", "code": "LLH" }
  }
}
```

Una vez ambos están aplicados, la consola de AWS muestra:

```json
{
  "state": {
    "desired":  { "light": "GREEN", "code": "LLH" },
    "reported": { "light": "GREEN", "code": "LLH" }
  },
  "metadata": {
    "desired":  { "light": { "timestamp": 1777052380 } },
    "reported": { "light": { "timestamp": 1777052381 } }
  },
  "version": 17
}
```

### Activación

La integración se activa sólo si las siguientes variables de entorno
están definidas cuando arrancan `TrafficLightDeviceApp` y/o
`CombinedSimulationApp`:

```
AWS_IOT_ENDPOINT            a1xkezf8guqg9c-ats.iot.us-east-1.amazonaws.com
AWS_IOT_KEYSTORE            ruta al .p12 generado con scripts/generate-aws-keystore.sh
AWS_IOT_KEYSTORE_PASSWORD   password del keystore (por defecto "changeit")
AWS_IOT_CA_CERT             ruta al AmazonRootCA1.pem
AWS_IOT_THING_NAME          ttmi010
AWS_IOT_CLIENT_ID_PREFIX    mucnap-iot26
```

Si faltan, la aplicación arranca en modo "solo tambori" sin ninguna
modificación de comportamiento.

### Policy mínima

Los permisos exigidos al certificado que se asocia al Thing se recogen en
`certificates/aws-iot-policy.json`. En resumen, cubren `iot:Connect` con
un clientId que empieza por `mucnap-iot26-*` y las acciones de
publicación/suscripción sobre los tópicos `$aws/things/ttmi010/shadow/*`
además de los tópicos de la simulación (`road/*/traffic|alert|signals|info`).

---

## 7. Arranque

### Simulación combinada (servicio + coches + ambulancia)

```
java -cp "bin:lib/*" CombinedSimulationApp tcp://tambori.dsic.upv.es:10083
```

Con esto arranca el `CorridorOrchestrationService`, registra la ruta de
emergencia `R5s1 → R3s2 → R3s1`, crea seis coches de prueba en tres
tramos, lanza la ambulancia y programa un accidente en `R3s1` a los pocos
segundos. El flujo completo (accidente → reducción de velocidad →
despacho → corredor verde → fin) dura alrededor de 25 segundos.

La flag `USE_REMOTE_DEVICE` en `CombinedSimulationApp.java` decide si el
semáforo de `R3s1` es local (simulado dentro de la app) o remoto (el que
corre en la Raspberry Pi como `TrafficLightDeviceApp`).

### Dispositivo físico (Raspberry Pi)

```
java -jar traffic-light-device.jar TL.R3s1.main \
    tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED
```

El fat jar (`device/traffic-light-device.jar`) se regenera con
`./scripts/build-device-jar.sh` y ya trae dentro Paho MQTT, org.json,
pi4j y slf4j. pigpio requiere permisos de root para acceder a la
memoria GPIO, por lo que en producción se ejecuta con `sudo -E` para
preservar las variables de entorno del shadow.

### Escenarios de `SmartCarStarterApp`

Para pruebas más minimalistas existe `SmartCarStarterApp`, que permite
lanzar uno de los cinco escenarios numerados (`ejercicio = 1..5` en el
código):

| Escenario | Descripción |
|---|---|
| 1 | Un SmartCar entra y sale de `R3s1` |
| 2 | Varios SmartCars entran y salen de `R3s1` |
| 3 | SmartCars + `SpeedLimitSign` publicando un límite |
| 4 | Igual que 3, pero con actualización dinámica del límite |
| 5 | SmartCar notifica un accidente en su posición actual |

```
java -cp "bin:lib/*" SmartCarStarterApp <smartCarID> tcp://tambori.dsic.upv.es:10083
```

---

## 8. Resumen de tipos de mensaje

La siguiente tabla condensa el contrato completo entre componentes.

| `type` | Campos en `msg` | Tópico | Productor | Consumidor |
|---|---|---|---|---|
| `TRAFFIC` | `action`, `road`, `road-segment`, `vehicle-id`, `position`, `role` | `road/<road>/traffic` | SmartCar, EmergencyVehicle | (libre) |
| `ROAD_INCIDENT` | `rt`, `incident-type`, `id`, `road`, `road-segment`, `starting-position`, `ending-position`, `description`, `status` | `road/<road>/alerts` | SmartCar (`notifyIncident`) | CorridorOrchestrationService |
| `TRAFFIC_SIGNAL` (SPEED_LIMIT) | `rt`, `id`, `road`, `road-segment`, `signal-type=SPEED_LIMIT`, `starting-position`, `ending-position`, `value`, `road-speed-limit` | `road/<road>/signals` | SpeedLimitSign, CorridorOrchestrationService | SmartCar |
| `TRAFFIC_SIGNAL` (TRAFFIC_LIGHT) | `rt`, `id`, `road`, `road-segment`, `signal-type=TRAFFIC_LIGHT`, `starting-position`, `ending-position`, `value` | `road/<road>/signals` | TrafficLightSign, TrafficLightDeviceApp | SmartCar |
| `TRAFFIC_LIGHT_COMMAND` | `command=SET_STATE`, `state`, `timestamp` | `traffic-light/<deviceId>/control` | RemoteTrafficLightSign | TrafficLightDeviceApp |
| `EMERGENCY_ALERT` | `event`, `vehicle-id`, `vehicle-type`, `level`, `current-road`, `current-km`, `destination` | `emergency/alerts` | EmergencyVehicle | CorridorOrchestrationService |
| Shadow update | `state.desired.*` o `state.reported.*` | `$aws/things/<thing>/shadow/update` | RemoteTrafficLightSign, TrafficLightDeviceApp | AWS IoT Core |

Con este contrato, cualquier equipo que quiera integrarse con la
plataforma —por ejemplo, añadir un panel de control o un nuevo tipo de
señal— tiene todos los puntos de anclaje: dónde publicar, a qué
suscribirse y qué estructura JSON esperar.
