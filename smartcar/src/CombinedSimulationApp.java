import componentes.AwsIotConfig;
import componentes.EmergencyLevel;
import componentes.EmergencyVehicle;
import componentes.RemoteTrafficLightSign;
import componentes.RoadPlace;
import componentes.SmartCar;
import componentes.TrafficLightShadow;
import componentes.TrafficLightSign;
import componentes.TrafficLightSign.LightState;
import componentes.VehicleType;
import java.util.ArrayList;
import java.util.List;
import servicio.CorridorOrchestrationService;
import servicio.CorridorRoute;
import servicio.CorridorRoute.RouteSegment;
import utils.MySimpleLogger;


public class CombinedSimulationApp {

    private static final String LOGGER_TAG = "CombinedSim";

    // ============================================================
    // CONFIGURACIÓN: cambiar a true para usar el dispositivo remoto fisico 
    // ============================================================
    private static final boolean USE_REMOTE_DEVICE = true;
    private static final String  REMOTE_DEVICE_ID  = "TL.R3s1.main";

    // Tramos de la ruta (la ambulancia va de R5s1 => R3s2 => R3s1 para atender el accidente)
    private static final String SEG_ACCIDENTE = "R3s1";  // donde ocurre el accidente
    private static final String SEG_INTER     = "R3s2";  // tramo intermedio
    private static final String SEG_HOSPITAL  = "R5s1";  // base de la ambulancia

    private static final int INITIAL_SPEED = 100;

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Uso: CombinedSimulationApp <brokerURL>");
            System.exit(1);
        }

        String brokerURL = args[0];
        MySimpleLogger.level = MySimpleLogger.INFO;


        // AWS IoT Shadow opcional (solo si las env vars AWS_IOT_* estan definidas)
        TrafficLightShadow shadow = AwsIotConfig.buildShadowOrNull("corridor-service");

        // TL.R3s1.main: local o remoto (dispositivo fisico)
        TrafficLightSign tlR3s1Main;
        if (USE_REMOTE_DEVICE) {
            log("Usando semaforo REMOTO: " + REMOTE_DEVICE_ID + " (dispositivo fisico)");
            RemoteTrafficLightSign remote = new RemoteTrafficLightSign(
                REMOTE_DEVICE_ID + ".proxy", REMOTE_DEVICE_ID,
                brokerURL, "R3", "R3s1", 100, 100, LightState.RED);
            remote.setShadow(shadow);
            tlR3s1Main = remote;
        } else {
            log("Usando semaforo LOCAL: TL.R3s1.main (simulado)");
            tlR3s1Main = new TrafficLightSign("TL.R3s1.main", null, brokerURL, "R3", "R3s1", 100, 100, LightState.RED);
        }

        TrafficLightSign tlR3s1Cross = new TrafficLightSign("TL.R3s1.cross", null, brokerURL, "R3", "R3s1-cruce", 100, 100, LightState.GREEN);
        TrafficLightSign tlR3s2Main  = new TrafficLightSign("TL.R3s2.main",  null, brokerURL, "R3", "R3s2",       200, 200, LightState.RED);
        TrafficLightSign tlR3s2Cross = new TrafficLightSign("TL.R3s2.cross", null, brokerURL, "R3", "R3s2-cruce", 200, 200, LightState.GREEN);
        TrafficLightSign tlR5s1Main  = new TrafficLightSign("TL.R5s1.main",  null, brokerURL, "R5", "R5s1",        50,  50, LightState.RED);
        TrafficLightSign tlR5s1Cross = new TrafficLightSign("TL.R5s1.cross", null, brokerURL, "R5", "R5s1-cruce",  50,  50, LightState.GREEN);

        tlR3s1Main.connect(); tlR3s1Cross.connect();
        tlR3s2Main.connect(); tlR3s2Cross.connect();
        tlR5s1Main.connect(); tlR5s1Cross.connect();

        // ================================================================
        // Ruta del corredor: R5s1 => R3s2 => R3s1 (ambulancia va hacia el accidente)
        // ================================================================
        CorridorRoute route = new CorridorRoute("ruta-emergencia");
        route.addSegment(new RouteSegment(SEG_HOSPITAL, tlR5s1Main, List.of(tlR5s1Cross)));
        route.addSegment(new RouteSegment(SEG_INTER,    tlR3s2Main, List.of(tlR3s2Cross)));
        route.addSegment(new RouteSegment(SEG_ACCIDENTE,tlR3s1Main, List.of(tlR3s1Cross)));

        // ================================================================
        // Orquestador
        // ================================================================
        CorridorOrchestrationService orchestrator =
            new CorridorOrchestrationService("corridor-orchestrator", brokerURL);
        orchestrator.registerRoute(route);
        orchestrator.connect();

        // ================================================================
        // PRE-CREAR la ambulancia ANTES del accidente.
        //    Evita crear un cliente MQTT nuevo desde dentro del callback MQTT.
        // ================================================================
        log("Creando ambulancia en la base (" + SEG_HOSPITAL + ")");
        EmergencyVehicle ambulancia = new EmergencyVehicle(
            "AMB-001", brokerURL, VehicleType.AMBULANCE, EmergencyLevel.HIGH);
        ambulancia.setCurrentRoadPlace(new RoadPlace(SEG_HOSPITAL, 50));
        ambulancia.getIntoRoad(SEG_HOSPITAL, 50);
        ambulancia.setSpeed(0);

        // Callback: cuando el orquestador detecta un accidente, activa la emergencia
        orchestrator.setAccidentListener((road, km) -> {
            log("*** Callback de accidente recibido: vía=" + road + " km=" + km);
            log("*** DESPACHANDO AMBULANCIA desde " + SEG_HOSPITAL + " hacia " + road);
            try {
                ambulancia.setSpeed(120);
                ambulancia.startEmergency(road);  // publica EMERGENCY_START => orquestador abrirá corredor
            } catch (Exception e) {
                MySimpleLogger.error(LOGGER_TAG, "Error al despachar ambulancia: " + e.getMessage());
            }
        });

        orchestrator.startListening();
        Thread.sleep(1500); // asegurar que las suscripciones están activas

        // ================================================================
        // FASE 1: Tráfico normal en MÚLTIPLES tramos
        // ================================================================
        log("================ FASE 1: Tráfico normal ================");
        List<SmartCar> coches = new ArrayList<>();

        // Coches en R3s1 (donde ocurrirá el accidente)
        coches.add(createCar("CAR-A1", brokerURL, SEG_ACCIDENTE,  80));
        coches.add(createCar("CAR-A2", brokerURL, SEG_ACCIDENTE, 110));

        // Coches en R3s2 (tramo intermedio)
        coches.add(createCar("CAR-B1", brokerURL, SEG_INTER,     150));
        coches.add(createCar("CAR-B2", brokerURL, SEG_INTER,     180));

        // Coches en R5s1 (cerca del hospital)
        coches.add(createCar("CAR-C1", brokerURL, SEG_HOSPITAL,   30));

        log("Total de coches circulando: " + coches.size() + " en 3 tramos");
        Thread.sleep(3000);

        // ================================================================
        // FASE 2: Accidente en R3s1
        // ================================================================
        log("================ FASE 2: ACCIDENTE ================");
        log("CAR-A1 sufre un accidente en " + SEG_ACCIDENTE);
        coches.get(0).notifyIncident("accident");

        // El orquestador:
        //  1) publica SPEED_LIMIT 30 km/h => todos los coches en el tópico frenan
        //  2) dispara el callback (en hilo separado) => ambulance.startEmergency()
        //  3) recibe EMERGENCY_START => abre el corredor (semáforos)
        Thread.sleep(4000);

        // ================================================================
        // FASE 3: Ambulancia en camino con corredor abierto
        // ================================================================
        log("================ FASE 3: Ambulancia en trayecto ================");
        log("La ambulancia avanza con corredor verde. Tiempo estimado: 10 segundos.");
        Thread.sleep(10000);

        // ================================================================
        // FASE 4: Ambulancia llega al accidente
        // ================================================================
        log("================ FASE 4: Ambulancia llega al accidente ================");
        ambulancia.endEmergency();
        ambulancia.getOutRoad(SEG_ACCIDENTE, 100);
        Thread.sleep(2000);

        // ================================================================
        // Limpieza
        // ================================================================
        log("================ Limpieza ================");
        for (int i = 1; i < coches.size(); i++) {
            try { coches.get(i).getOutRoad(SEG_ACCIDENTE, 200); } catch (Exception ignored) {}
        }
        Thread.sleep(2000);

        tlR3s1Main.disconnect(); tlR3s1Cross.disconnect();
        tlR3s2Main.disconnect(); tlR3s2Cross.disconnect();
        tlR5s1Main.disconnect(); tlR5s1Cross.disconnect();

        log("================ Simulación finalizada ================");
    }

    private static SmartCar createCar(String id, String brokerURL, String segment, int km)
            throws InterruptedException {
        SmartCar car = new SmartCar(id, brokerURL);
        car.setCurrentRoadPlace(new RoadPlace(segment, km));
        car.getIntoRoad(segment, km);
        car.setSpeed(INITIAL_SPEED);
        log(id + " circulando por " + segment + " km=" + km + " a " + INITIAL_SPEED + " km/h");
        Thread.sleep(400);
        return car;
    }

    private static void log(String msg) {
        MySimpleLogger.warn(LOGGER_TAG, msg);
    }
}
