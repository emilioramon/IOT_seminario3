import componentes.MyMqttClient;
import componentes.TrafficLightSign;
import componentes.TrafficLightSign.LightState;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import utils.MySimpleLogger;

/**
 * Aplicación del semáforo para desplegar en dispositivo físico (Raspberry Pi).
 *
 * Esta app reemplaza a un TrafficLightSign local: se conecta al broker MQTT,
 * escucha comandos en un tópico de control, y cuando recibe un comando
 * SET_STATE cambia el estado del semáforo y publica la señal TRAFFIC_SIGNAL
 * en el tópico estándar de la carretera.
 *
 * Tópico de control (suscripción):
 *   es/upv/pros/tatami/smartcities/traffic/PTPaterna/traffic-light/{deviceId}/control
 *
 * Tópico de señales (publicación, automático vía TrafficLightSign):
 *   es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/{road}/signals
 *
 * Uso:
 *   java -cp "device.jar:lib/*" TrafficLightDeviceApp <deviceId> <brokerURL> <road> <roadSegment> <startPos> <endPos> [initialState]
 *
 * Ejemplo:
 *   java -cp "device.jar:lib/*" TrafficLightDeviceApp TL.R3s1.main tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED
 */
public class TrafficLightDeviceApp {

    private static final String CONTROL_TOPIC_PATTERN =
        "es/upv/pros/tatami/smartcities/traffic/PTPaterna/traffic-light/%s/control";

    private static final String LOGGER_TAG = "DeviceApp";

    public static void main(String[] args) throws Exception {

        if (args.length < 6) {
            System.out.println("Uso: TrafficLightDeviceApp <deviceId> <brokerURL> <road> <roadSegment> <startPos> <endPos> [initialState]");
            System.out.println("  initialState: RED (default), GREEN, AMBER");
            System.out.println();
            System.out.println("Ejemplo:");
            System.out.println("  java -cp \"device.jar:lib/*\" TrafficLightDeviceApp TL.R3s1.main tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED");
            System.exit(1);
        }

        String deviceId    = args[0];
        String brokerURL   = args[1];
        String road        = args[2];
        String roadSegment = args[3];
        int    startPos    = Integer.parseInt(args[4]);
        int    endPos      = Integer.parseInt(args[5]);

        LightState initialState = LightState.RED;
        if (args.length >= 7) {
            initialState = LightState.valueOf(args[6].toUpperCase());
        }

        MySimpleLogger.level = MySimpleLogger.INFO;

        // ------------------------------------------------------------------
        // 1. Crear el semáforo real (publica señales TRAFFIC_SIGNAL)
        // ------------------------------------------------------------------
        TrafficLightSign trafficLight = new TrafficLightSign(
            deviceId, null, brokerURL, road, roadSegment,
            startPos, endPos, initialState);

        trafficLight.connect();
        trafficLight.publishSignal(); // Publicar estado inicial

        MySimpleLogger.info(LOGGER_TAG,
            "Semaforo creado: id=" + deviceId +
            " road=" + road + " segment=" + roadSegment +
            " estado=" + initialState.getDescription());

        // ------------------------------------------------------------------
        // 2. Crear el suscriptor de control (recibe comandos SET_STATE)
        // ------------------------------------------------------------------
        String controlTopic = String.format(CONTROL_TOPIC_PATTERN, deviceId);

        ControlSubscriber controller = new ControlSubscriber(
            deviceId + ".ctrl", brokerURL, trafficLight);
        controller.connect();
        controller.subscribe(controlTopic);

        MySimpleLogger.info(LOGGER_TAG,
            "Escuchando comandos en: " + controlTopic);

        // ------------------------------------------------------------------
        // 3. Mantener la app corriendo
        // ------------------------------------------------------------------
        MySimpleLogger.warn(LOGGER_TAG,
            "========================================");
        MySimpleLogger.warn(LOGGER_TAG,
            "  DISPOSITIVO LISTO - " + deviceId);
        MySimpleLogger.warn(LOGGER_TAG,
            "  Broker: " + brokerURL);
        MySimpleLogger.warn(LOGGER_TAG,
            "  Ctrl-C para detener");
        MySimpleLogger.warn(LOGGER_TAG,
            "========================================");

        // Shutdown hook para desconexión limpia
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MySimpleLogger.info(LOGGER_TAG, "Apagando dispositivo...");
            controller.disconnect();
            trafficLight.disconnect();
            MySimpleLogger.info(LOGGER_TAG, "Dispositivo desconectado.");
        }));

        // Mantener vivo
        while (true) {
            Thread.sleep(60000);
        }
    }

    // ------------------------------------------------------------------
    // Suscriptor de control: recibe comandos y controla el semáforo
    // ------------------------------------------------------------------
    static class ControlSubscriber extends MyMqttClient {

        private final TrafficLightSign trafficLight;

        public ControlSubscriber(String clientId, String brokerURL,
                                 TrafficLightSign trafficLight) {
            super(clientId, null, brokerURL);
            this.trafficLight = trafficLight;
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            super.messageArrived(topic, message);

            String payload = new String(message.getPayload());
            try {
                JSONObject command = new JSONObject(payload);
                String type = command.optString("type", "");

                if ("TRAFFIC_LIGHT_COMMAND".equals(type)) {
                    handleCommand(command);
                } else {
                    MySimpleLogger.warn(clientId,
                        "Mensaje desconocido ignorado: type=" + type);
                }
            } catch (Exception e) {
                MySimpleLogger.error(clientId,
                    "Error procesando comando: " + e.getMessage());
            }
        }

        private void handleCommand(JSONObject command) {
            String cmd = command.optString("command", "");

            if ("SET_STATE".equals(cmd)) {
                String stateStr = command.optString("state", "");
                try {
                    LightState newState = LightState.valueOf(stateStr);
                    MySimpleLogger.warn(clientId,
                        ">>> COMANDO RECIBIDO: SET_STATE → " + newState.getDescription());
                    trafficLight.setState(newState);
                } catch (IllegalArgumentException e) {
                    MySimpleLogger.error(clientId,
                        "Estado desconocido: '" + stateStr + "'. Valores validos: RED, AMBER, GREEN");
                }
            } else {
                MySimpleLogger.warn(clientId,
                    "Comando desconocido: " + cmd);
            }
        }
    }
}
