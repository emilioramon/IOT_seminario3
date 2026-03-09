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


public class SmartCar_TrafficNotifier extends MyMqttClient {
	
	public SmartCar_TrafficNotifier(String clientId, SmartCar smartcar, String brokerURL) {
		super(clientId, smartcar, brokerURL);
	}
	
	public void enterRoad(String road, int km) {
		String topic="es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + road + "/traffic";
		JSONObject msg = new JSONObject();
		JSONObject m4 = new JSONObject();
		String timestap = String.valueOf(System.currentTimeMillis());
		try {

			msg.put("action", "VEHICLE_IN");
			msg.put("road", road);
			msg.put("road-segment", road );
			msg.put("vehicle-id", smartcar.getSmartCarID());
			msg.put("position", String.valueOf(km));
			msg.put("role", "PrivateVehicle");
			m4.put("msg", msg);
			m4.put("id", "MSG_1477473530870");
			m4.put("type","TRAFFIC");
			m4.put("timestamp", timestap);
			this.publish(topic, m4.toString());
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			MySimpleLogger.error(this.clientId, "Error publishing message: " + e.getMessage());
		}
	}

	public void outRoad(String road, int km) {
		String topic="es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + road + "/traffic";
		JSONObject msg = new JSONObject();
		String timestap = String.valueOf(System.currentTimeMillis());
		JSONObject m4 = new JSONObject();
		try {

			msg.put("action", "VEHICLE_OUT");
			msg.put("road", road);
			msg.put("road-segment", road );
			msg.put("vehicle-id", smartcar.getSmartCarID());
			msg.put("position", String.valueOf(km));
			msg.put("role", "PrivateVehicle");
			m4.put("msg", msg);
			m4.put("id", "MSG_1477473530870");
			m4.put("type","TRAFFIC");
			m4.put("timestamp", timestap);
			this.publish(topic, m4.toString());
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			MySimpleLogger.error(this.clientId, "Error publishing message: " + e.getMessage());
		}
	}
	
	public void alert(String smartCarID, String notificationType, RoadPlace place) {

		String myTopic =  "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/" + place.getRoad() + "/alerts";

		MqttTopic topic = myClient.getTopic(myTopic);


		JSONObject json = new JSONObject();
		JSONObject msg = new JSONObject();
		try {	
			msg.put("rt", "traffic::alert");
			msg.put("incident-type", "TRAFFIC_ACCIDENT");
			msg.put("id", smartCarID);
			msg.put("road", place.getRoad());
			msg.put("road-segment", place.getRoad());
			msg.put("starting-position", place.getKm());
			msg.put("ending-position", place.getKm());
			msg.put("description", "Vehicle Crash");
			msg.put("status", "Active");
			json.put("id", "MSG_1477472671831");
			json.put("type", "ROAD_INCIDENT");
			json.put("timestamp", System.currentTimeMillis());  
			json.put("msg", msg);
		
	   		} catch (JSONException e1) {
				MySimpleLogger.error(this.clientId, "Error creating JSON message: " + e1.getMessage());
				e1.printStackTrace();
		}
		
   		int pubQoS = 0;
			MqttMessage message = new MqttMessage(json.toString().getBytes());
    	message.setQos(pubQoS);
    	message.setRetained(false);

    	// Publish the message
    	MySimpleLogger.trace(this.clientId, "Publishing to topic " + topic + " qos " + pubQoS);
    	MqttDeliveryToken token = null;
    	try {
    		// publish message to broker
			token = topic.publish(message);
			MySimpleLogger.trace(this.clientId, json.toString());
	    	// Wait until the message has been delivered to the broker
			token.waitForCompletion();
			Thread.sleep(100);
		} catch (Exception e) {
			e.printStackTrace();
		}
	    		    	

	}
	
}