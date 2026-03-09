package componentes;

import org.json.JSONObject;

import utils.MySimpleLogger;


public class SmartCar {


	protected String brokerURL = null;

	protected String smartCarID = null;
	protected RoadPlace rp = null;
	protected SmartCar_RoadInfoSubscriber subscriber = null;
	protected SmartCar_TrafficNotifier notifier = null;
	protected int speed = 0;
	
	public SmartCar(String id, String brokerURL) {
		
		this.setSmartCarID(id);
		this.brokerURL = brokerURL;

		this.setCurrentRoadPlace(new RoadPlace("R5s1", 0));
		
		this.notifier = new SmartCar_TrafficNotifier(id + ".traffic-notifier", this, this.brokerURL);
		this.notifier.connect();

	}
	
	public void setSmartCarID(String smartCarID) {
		this.smartCarID = smartCarID;
	}
	
	public String getSmartCarID() {
		return smartCarID;
	}

	public void setSpeed(int speed) {
		this.speed = speed;
	}

	public int getSpeed() {
		return speed;
	}
	public void adjustSpeed(int signSpeedLimit, int roadSpeedLimit) {
        int targetSpeed = Math.min(signSpeedLimit, roadSpeedLimit);
 
        if (targetSpeed < this.speed) {
            MySimpleLogger.warn(this.smartCarID,
                "REDUCCION de velocidad de " + this.speed + " a " + targetSpeed + " km/h" +
                " (sign: " + signSpeedLimit + ", road limit: " + roadSpeedLimit + ")");
        } else if (targetSpeed > this.speed) {
            MySimpleLogger.info(this.smartCarID,
                "INCREMENTO de velocidad de " + this.speed + " a " + targetSpeed + " km/h" +
                " (sign: " + signSpeedLimit + ", road limit: " + roadSpeedLimit + ")");
        } 
 
        this.speed = targetSpeed;
    }
	public void setCurrentRoadPlace(RoadPlace rp) {
		this.rp = rp;
	}

	public RoadPlace getCurrentPlace() {
		return rp;
	}

	public void changeKm(int km) {
		this.getCurrentPlace().setKm(km);
	}
	
	
	public void notifyIncident(String incidentType) {
		if ( this.notifier == null )
			return;
		
		this.notifier.alert(this.getSmartCarID(), incidentType, this.getCurrentPlace());
		
	}

	public void getIntoRoad(String road, int km) {

		this.notifier.enterRoad(road, km);
		this.subscriber = new SmartCar_RoadInfoSubscriber(this.getSmartCarID() + ".road-info-subscriber", this, this.brokerURL);
		this.subscriber.connect();
		this.subscriber.subscribeToRoad(road);

	}

	public void getOutRoad(String road, int km) {

		this.notifier.outRoad(road, km);
		this.subscriber.disconnect();

	}

}

