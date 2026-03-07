package componentes;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.json.JSONException;
import org.json.JSONObject;
import utils.MySimpleLogger;

public class Signal_ChangeNotifier extends MyMqttClient {
	
	public Signal_ChangeNotifier(String clientId, SmartCar smartcar, String brokerURL) {
		super(clientId, smartcar, brokerURL);
	}
	
	public void changeState(String road, String roadSegment, String signalType, int km, String value) {
		String topic="es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + roadSegment + "/traffic";
		JSONObject msg = new JSONObject();
		JSONObject m1 = new JSONObject();
		String timestap = String.valueOf(System.currentTimeMillis());
		try {

			msg.put("road", road);
			msg.put("road-segment", roadSegment);
			msg.put("signal-type", signalType);
			msg.put("starting-position", km);
			msg.put("ending-position", km);
			msg.put("value", value);
			m1.put("msg", msg);
			m1.put("id", "MSG_1477473530871");
			m1.put("type","TRAFFIC_SIGNAL");
			m1.put("timestamp", timestap);
			this.publish(topic, m1.toString());
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			MySimpleLogger.error(this.clientId, "Error publishing message: " + e.getMessage());
		}
	}

}