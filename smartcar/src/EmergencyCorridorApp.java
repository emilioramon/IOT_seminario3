import componentes.EmergencyLevel;
import componentes.EmergencyVehicle;
import componentes.TrafficLightSign;
import componentes.TrafficLightSign.LightState;
import componentes.VehicleType;
import java.util.List;
import servicio.CorridorOrchestrationService;
import servicio.CorridorRoute;
import servicio.CorridorRoute.RouteSegment;
import utils.MySimpleLogger;

/**
 * Aplicación principal del sistema de corredor de emergencia.
 *
 * Uso:
 *   java EmergencyCorridorApp <vehicleID> <brokerURL>
 *
 * Ejemplo:
 *   java -cp "bin:lib/*" EmergencyCorridorApp AMB-001 tcp://tambori.dsic.upv.es:10083
 *
 * Simula el siguiente escenario:
 *   1. El orquestador arranca y registra la ruta R3s1 → R3s2 → R5s1.
 *   2. Una ambulancia entra en R3s1 e inicia una emergencia hacia R5.
 *   3. El orquestador abre el corredor: verde en R3s1, R3s2, R5s1;
 *      rojo en los semáforos conflictivos de cada cruce.
 *   4. Tras 10 segundos, la ambulancia llega y cierra la emergencia.
 *   5. El orquestador restaura todos los semáforos al estado normal.
 */
public class EmergencyCorridorApp {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Uso: EmergencyCorridorApp <vehicleID> <brokerURL>");
            System.exit(1);
        }

        String vehicleId = args[0];
        String brokerURL = args[1];

        MySimpleLogger.level = MySimpleLogger.INFO;

        // ------------------------------------------------------------------
        // 1. Crear semáforos del corredor (carretera R3 hacia R5)
        //    Los semáforos son instanciados y controlados por el orquestador.
        //    Estado inicial: ROJO (estado seguro).
        // ------------------------------------------------------------------
        TrafficLightSign tlR3s1Main = new TrafficLightSign(
            "TL.R3s1.main", null, brokerURL, "R3", "R3s1", 100, 100, LightState.RED);

        TrafficLightSign tlR3s1Cross = new TrafficLightSign(
            "TL.R3s1.cross", null, brokerURL, "R3", "R3s1-cruce", 100, 100, LightState.GREEN);

        TrafficLightSign tlR3s2Main = new TrafficLightSign(
            "TL.R3s2.main", null, brokerURL, "R3", "R3s2", 200, 200, LightState.RED);

        TrafficLightSign tlR3s2Cross = new TrafficLightSign(
            "TL.R3s2.cross", null, brokerURL, "R3", "R3s2-cruce", 200, 200, LightState.GREEN);

        TrafficLightSign tlR5s1Main = new TrafficLightSign(
            "TL.R5s1.main", null, brokerURL, "R5", "R5s1", 50, 50, LightState.RED);

        TrafficLightSign tlR5s1Cross = new TrafficLightSign(
            "TL.R5s1.cross", null, brokerURL, "R5", "R5s1-cruce", 50, 50, LightState.GREEN);

        // Conectar semáforos al broker
        tlR3s1Main.connect();
        tlR3s1Cross.connect();
        tlR3s2Main.connect();
        tlR3s2Cross.connect();
        tlR5s1Main.connect();
        tlR5s1Cross.connect();

        // ------------------------------------------------------------------
        // 2. Definir la ruta: R3s1 → R3s2 → R5s1
        // ------------------------------------------------------------------
        CorridorRoute route = new CorridorRoute("ruta-hospital");
        route.addSegment(new RouteSegment("R3s1", tlR3s1Main, List.of(tlR3s1Cross)));
        route.addSegment(new RouteSegment("R3s2", tlR3s2Main, List.of(tlR3s2Cross)));
        route.addSegment(new RouteSegment("R5s1", tlR5s1Main, List.of(tlR5s1Cross)));

        // ------------------------------------------------------------------
        // 3. Arrancar el servicio de orquestación
        // ------------------------------------------------------------------
        CorridorOrchestrationService orchestrator =
            new CorridorOrchestrationService("corridor-orchestrator", brokerURL);
        orchestrator.registerRoute(route);
        orchestrator.connect();
        orchestrator.startListening();

        MySimpleLogger.info("EmergencyCorridorApp", "Orquestador listo. Esperando alertas...");
        Thread.sleep(2000); // Dar tiempo a que las suscripciones se establezcan

        // ------------------------------------------------------------------
        // 4. Simular una ambulancia que inicia emergencia
        // ------------------------------------------------------------------
        EmergencyVehicle ambulancia = new EmergencyVehicle(
            vehicleId, brokerURL, VehicleType.AMBULANCE, EmergencyLevel.HIGH);

        ambulancia.getIntoRoad("R3s1", 100);
        Thread.sleep(1000);

        // Inicia la emergencia: el orquestador abrirá el corredor
        ambulancia.startEmergency("R5s1");

        MySimpleLogger.info("EmergencyCorridorApp", "Ambulancia en camino... (10 segundos)");
        Thread.sleep(10000);

        // ------------------------------------------------------------------
        // 5. La ambulancia llega: cierra el corredor
        // ------------------------------------------------------------------
        ambulancia.endEmergency();
        ambulancia.getOutRoad("R5s1", 50);

        Thread.sleep(2000);

        // ------------------------------------------------------------------
        // 6. Desconexión limpia
        // ------------------------------------------------------------------
        tlR3s1Main.disconnect();
        tlR3s1Cross.disconnect();
        tlR3s2Main.disconnect();
        tlR3s2Cross.disconnect();
        tlR5s1Main.disconnect();
        tlR5s1Cross.disconnect();

        MySimpleLogger.info("EmergencyCorridorApp", "Simulación finalizada.");
    }
}
