package componentes;


import org.eclipse.paho.client.mqttv3.MqttMessage;
import utils.MySimpleLogger;
import org.json.JSONObject;

public class SmartCar_RoadInfoSubscriber extends MyMqttClient {

	protected SmartCar theSmartCar;
	
	public SmartCar_RoadInfoSubscriber(String clientId, SmartCar smartcar, String MQTTBrokerURL) {
		super(clientId, smartcar, MQTTBrokerURL);
		this.smartcar = smartcar;
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		super.messageArrived(topic, message);		
		String payload = new String(message.getPayload());
		
		try {
            JSONObject json = new JSONObject(payload);
			MySimpleLogger.debug(this.smartcar.getSmartCarID(), "Received message: " + json.toString());
 
			// Ejercicio 5.4: Simulación de notificación de cambio de estado de una señal de tráfico por parte del SmartCar
            if (json.has("type") && json.getString("type").equals("TRAFFIC_SIGNAL")) {
								JSONObject msg = json.getJSONObject("msg");
								if (msg.getString("signal-type").equals("SPEED_LIMIT")) {
									int signSpeedLimit = Integer.parseInt(msg.getString("value"));
									int roadSpeedLimit = msg.getInt("road-speed-limit");
									this.smartcar.adjustSpeed(signSpeedLimit, roadSpeedLimit);
								} 
            }
			// Ejercicio 5.5: Simulación de notificación de incidente por parte del SmartCar
            if (json.has("event") && json.getString("event").equals("accident")) {
                String road = json.optString("road");
                int kp = json.optInt("kp");
                MySimpleLogger.error(this.smartcar.getSmartCarID(), "*** ALERTA DE ACCIDENTE en road " + road + " km " + kp );
            }
        } catch (Exception e) {
						MySimpleLogger.error(this.smartcar.getSmartCarID(), "Error processing message: " + e.getMessage());
						e.printStackTrace();
        }

	}

	public void subscribeToRoad(String road) {
		String topic = "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + road + "/signals";
		this.subscribe(topic);
		topic = "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + road + "/info";
		this.subscribe(topic);

	}

}
