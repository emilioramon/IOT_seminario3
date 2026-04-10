package componentes;

import org.json.JSONObject;
import utils.MySimpleLogger;

/**
 * Publica alertas de emergencia al tópico dedicado:
 *   es/upv/pros/tatami/smartcities/traffic/PTPaterna/emergency/alerts
 *
 * Formato del mensaje:
 * {
 *   "id"        : "MSG_<timestamp>",
 *   "type"      : "EMERGENCY_ALERT",
 *   "timestamp" : <epoch_ms>,
 *   "msg": {
 *     "event"        : "EMERGENCY_START | EMERGENCY_END",
 *     "vehicle-id"   : "AMB-001",
 *     "vehicle-type" : "AMBULANCE",
 *     "level"        : "HIGH",
 *     "current-road" : "R3s1",
 *     "current-km"   : 100,
 *     "destination"  : "R5"
 *   }
 * }
 */
public class EmergencyAlertPublisher extends MyMqttClient {

    public static final String EMERGENCY_TOPIC =
        "es/upv/pros/tatami/smartcities/traffic/PTPaterna/emergency/alerts";

    public static final String EVENT_START = "EMERGENCY_START";
    public static final String EVENT_END   = "EMERGENCY_END";

    public EmergencyAlertPublisher(String clientId, SmartCar smartcar, String brokerURL) {
        super(clientId, smartcar, brokerURL);
    }

    public void publishAlert(String event,
                             String vehicleId,
                             VehicleType vehicleType,
                             EmergencyLevel level,
                             String currentRoad,
                             int currentKm,
                             String destination) {
        long now = System.currentTimeMillis();
        try {
            JSONObject msg = new JSONObject();
            msg.put("event",        event);
            msg.put("vehicle-id",   vehicleId);
            msg.put("vehicle-type", vehicleType.getCode());
            msg.put("level",        level.getCode());
            msg.put("current-road", currentRoad);
            msg.put("current-km",   currentKm);
            msg.put("destination",  destination);

            JSONObject envelope = new JSONObject();
            envelope.put("id",        "MSG_" + now);
            envelope.put("type",      "EMERGENCY_ALERT");
            envelope.put("timestamp", now);
            envelope.put("msg",       msg);

            MySimpleLogger.info(clientId,
                "[" + event + "] vehículo=" + vehicleId +
                " tipo=" + vehicleType.getCode() +
                " nivel=" + level.getCode() +
                " destino=" + destination);

            this.publish(EMERGENCY_TOPIC, envelope.toString());

        } catch (Exception e) {
            MySimpleLogger.error(clientId, "Error publicando alerta de emergencia: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
