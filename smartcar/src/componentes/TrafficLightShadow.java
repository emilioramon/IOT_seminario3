package componentes;

import componentes.TrafficLightSign.LightState;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import utils.MySimpleLogger;

/**
 * Cliente AWS IoT Device Shadow para un semaforo.
 *
 * Tópicos Shadow clasicos (Thing = ttmi010):
 *   $aws/things/ttmi010/shadow/update                  (pub)
 *   $aws/things/ttmi010/shadow/update/accepted         (sub, log)
 *   $aws/things/ttmi010/shadow/update/rejected         (sub, log)
 *   $aws/things/ttmi010/shadow/update/delta            (sub, log divergencia)
 *
 * Documento de estado publicado:
 *   { "state": { "desired":  { "light": "GREEN" } } }
 *   { "state": { "reported": { "light": "GREEN" } } }
 *
 * Configuracion via variables de entorno (AWS_IOT_*).
 * Si la configuracion no esta completa, isEnabled() devuelve false.
 */
public class TrafficLightShadow extends MyMqttClient {

    private static final String TOPIC_UPDATE           = "$aws/things/%s/shadow/update";
    private static final String TOPIC_UPDATE_ACCEPTED  = "$aws/things/%s/shadow/update/accepted";
    private static final String TOPIC_UPDATE_REJECTED  = "$aws/things/%s/shadow/update/rejected";
    private static final String TOPIC_UPDATE_DELTA     = "$aws/things/%s/shadow/update/delta";

    private final String thingName;
    private final String updateTopic;

    public TrafficLightShadow(String clientId, String brokerURL, String thingName) {
        super(clientId, null, brokerURL);
        this.thingName   = thingName;
        this.updateTopic = String.format(TOPIC_UPDATE, thingName);
    }

    public String getThingName() {
        return thingName;
    }

    public void subscribeToShadow() {
        subscribe(String.format(TOPIC_UPDATE_ACCEPTED, thingName));
        subscribe(String.format(TOPIC_UPDATE_REJECTED, thingName));
        subscribe(String.format(TOPIC_UPDATE_DELTA,    thingName));
    }

    public void updateDesired(LightState state) {
        publishState("desired", state);
    }

    public void updateReported(LightState state) {
        publishState("reported", state);
    }

    private void publishState(String section, LightState state) {
        try {
            JSONObject inner = new JSONObject();
            inner.put("light", state.name());
            inner.put("code",  state.getCode());

            JSONObject stateObj = new JSONObject();
            stateObj.put(section, inner);

            JSONObject doc = new JSONObject();
            doc.put("state", stateObj);

            publish(updateTopic, doc.toString());
            MySimpleLogger.info(clientId,
                "[SHADOW] " + section + " => " + state.name() + " (thing=" + thingName + ")");

        } catch (Exception e) {
            MySimpleLogger.error(clientId,
                "[SHADOW] Error publicando " + section + ": " + e.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        if (topic.endsWith("/delta")) {
            MySimpleLogger.warn(clientId, "[SHADOW DELTA] Divergencia desired vs reported: " + payload);
        } else if (topic.endsWith("/rejected")) {
            MySimpleLogger.error(clientId, "[SHADOW REJECTED] " + payload);
        } else {
            MySimpleLogger.trace(clientId, "[SHADOW ACK] " + payload);
        }
    }
}
