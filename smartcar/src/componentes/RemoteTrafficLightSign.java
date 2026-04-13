package componentes;

import org.json.JSONObject;
import utils.MySimpleLogger;

/**
 * Proxy remoto de un semáforo físico desplegado en un dispositivo (Raspberry Pi).
 *
 * Tiene la misma interfaz que TrafficLightSign, por lo que es compatible con
 * CorridorRoute y CorridorOrchestrationService sin cambios.
 *
 * Diferencia clave:
 *   - TrafficLightSign.setState() → publica la señal TRAFFIC_SIGNAL directamente
 *   - RemoteTrafficLightSign.setState() → publica un COMANDO al tópico de control
 *     del dispositivo remoto; el dispositivo recibe el comando y publica la señal.
 *
 * Tópico de control:
 *   es/upv/pros/tatami/smartcities/traffic/PTPaterna/traffic-light/{deviceId}/control
 *
 * Formato del comando:
 * {
 *   "type"      : "TRAFFIC_LIGHT_COMMAND",
 *   "command"   : "SET_STATE",
 *   "state"     : "GREEN",
 *   "timestamp" : 1234567890
 * }
 */
public class RemoteTrafficLightSign extends TrafficLightSign {

    private static final String CONTROL_TOPIC_PATTERN =
        "es/upv/pros/tatami/smartcities/traffic/PTPaterna/traffic-light/%s/control";

    /** ID lógico del dispositivo remoto (usado para construir el tópico de control). */
    private final String deviceId;
    private final String controlTopic;

    /**
     * @param clientId    ID del cliente MQTT de este proxy (debe ser distinto al del dispositivo)
     * @param deviceId    ID lógico del dispositivo remoto (e.g. "TL.R3s1.main")
     * @param brokerURL   URL del broker MQTT
     * @param road        carretera (e.g. "R3")
     * @param roadSegment segmento (e.g. "R3s1")
     */
    public RemoteTrafficLightSign(String clientId,
                                  String deviceId,
                                  String brokerURL,
                                  String road,
                                  String roadSegment,
                                  int    startingPosition,
                                  int    endingPosition,
                                  LightState initialState) {

        super(clientId, null, brokerURL, road, roadSegment,
              startingPosition, endingPosition, initialState);
        this.deviceId     = deviceId;
        this.controlTopic = String.format(CONTROL_TOPIC_PATTERN, deviceId);
    }

    /**
     * En vez de publicar la señal directamente, envía un comando SET_STATE
     * al dispositivo remoto a través del tópico de control.
     */
    @Override
    public void setState(LightState newState) {
        try {
            JSONObject command = new JSONObject();
            command.put("type",      "TRAFFIC_LIGHT_COMMAND");
            command.put("command",   "SET_STATE");
            command.put("state",     newState.name());
            command.put("timestamp", System.currentTimeMillis());

            this.publish(controlTopic, command.toString());
            MySimpleLogger.info(clientId,
                "[REMOTE → " + deviceId + "] Comando enviado: " + newState.getDescription());

        } catch (Exception e) {
            MySimpleLogger.error(clientId,
                "Error enviando comando remoto: " + e.getMessage());
        }
    }

    /**
     * No-op: el dispositivo remoto se encarga de publicar la señal TRAFFIC_SIGNAL.
     */
    @Override
    public void publishSignal() {
        // El dispositivo remoto publica la señal cuando recibe el comando
    }
}
