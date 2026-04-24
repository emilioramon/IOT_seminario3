# AWS IoT Device Shadow - Semaforo ttmi010

Integracion de AWS IoT Core mediante **Device Shadow** sobre el Thing `ttmi010`.
Tambori sigue siendo el broker operativo; AWS IoT mantiene un shadow sincronizado
con el estado `desired` (lo que el servicio pide) y `reported` (lo que el semaforo
fisico confirma).

## Arquitectura

```
CorridorOrchestrationService ──► tambori ──► TrafficLightDeviceApp (GPIO)
           │                                         │
           │ publica "desired"                       │ publica "reported"
           └─────────────► AWS IoT Shadow ◄──────────┘
                           (thing: ttmi010)
```

## Configuracion (una sola vez)

### 1. Actualizar la policy en AWS IoT

Reemplazar la policy del certificado con el contenido de `aws-iot-policy.json`
(añade permisos sobre `$aws/things/ttmi010/shadow/*`).

### 2. Generar el keystore PKCS#12 desde los PEMs

```bash
./scripts/generate-aws-keystore.sh
```

Produce `certificates/aws-iot-client.p12` (password por defecto: `changeit`).

## Arranque con Shadow activado

Exporta las variables de entorno antes de lanzar el servicio y/o el dispositivo:

```bash
export AWS_IOT_ENDPOINT="a1xkezf8guqg9c-ats.iot.us-east-1.amazonaws.com"
export AWS_IOT_KEYSTORE="$(pwd)/certificates/aws-iot-client.p12"
export AWS_IOT_KEYSTORE_PASSWORD="changeit"
export AWS_IOT_CA_CERT="$(pwd)/certificates/AmazonRootCA1.pem"
export AWS_IOT_THING_NAME="ttmi010"
export AWS_IOT_CLIENT_ID_PREFIX="mucnap-iot26"
```

**Servicio de orquestacion** (publica `desired`):
```bash
java -cp "bin:lib/*" CombinedSimulationApp tcp://tambori.dsic.upv.es:10083
```

**Dispositivo fisico** (publica `reported`, se ejecuta en la Raspberry Pi):
```bash
java -cp "bin:lib/*" TrafficLightDeviceApp TL.R3s1.main \
    tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED
```

Si las variables `AWS_IOT_*` no estan definidas, ambos procesos funcionan
exactamente como antes (solo tambori, sin shadow).

## Verificar en vivo

En la consola de AWS:

1. **IoT Core → Manage → Things → ttmi010 → Device Shadows → Classic Shadow**
   Ves el JSON con `desired` y `reported` en tiempo real.

2. **IoT Core → Test → MQTT test client → Subscribe**
   Suscribete a `$aws/things/ttmi010/shadow/update/#` para ver el trafico.

## Escenario de demo

1. Arrancar dispositivo + servicio con shadow activo.
2. Shadow inicial: `desired: null, reported: RED`.
3. Provocar accidente → ambulancia pide emergencia → servicio publica
   `desired: GREEN`.
4. Tambori entrega el comando al dispositivo → GPIO cambia a verde →
   dispositivo publica `reported: GREEN`.
5. Shadow sincronizado: `desired = reported = GREEN`. Si el dispositivo
   se desconecta, el `desired` queda pendiente hasta que vuelva.
