package componentes;

import org.json.JSONObject;
import utils.MySimpleLogger;

/**
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

    private final String deviceId;
    private final String controlTopic;
    private TrafficLightShadow shadow;

    /**
     * @param clientId    ID del cliente MQTT de este proxy (debe ser distinto al del dispositivo)
     * @param deviceId    ID lógico del dispositivo remoto (ejemplo "TL.R3s1.main")
     * @param brokerURL   URL del broker MQTT
     * @param road        carretera (ejemplo "R3")
     * @param roadSegment segmento (ejemplo "R3s1")
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

    /** Asocia un shadow opcional que recibira actualizaciones de estado "desired". */
    public void setShadow(TrafficLightShadow shadow) {
        this.shadow = shadow;
    }

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
                "[REMOTE => " + deviceId + "] Comando enviado: " + newState.getDescription());

            if (shadow != null) {
                shadow.updateDesired(newState);
            }

        } catch (Exception e) {
            MySimpleLogger.error(clientId,
                "Error enviando comando remoto: " + e.getMessage());
        }
    }


    @Override
    public void publishSignal() {
        
    }
}
