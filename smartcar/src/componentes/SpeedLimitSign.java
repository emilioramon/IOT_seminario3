package componentes;

import org.json.JSONObject;


public class SpeedLimitSign extends TrafficSign {

    private final int roadSpeedLimit; 
    private int signSpeedLimit; 

    public SpeedLimitSign(String clientId,
                          SmartCar smartcar,
                          String brokerURL,
                          String road,
                          String roadSegment,
                          int    position,
                          int    roadSpeedLimit,
                          int    signSpeedLimit) {

        super(clientId, smartcar, brokerURL, road, roadSegment, position, position);

        if (signSpeedLimit >= roadSpeedLimit) {
            throw new IllegalArgumentException(
                "El límite de la señal (" + signSpeedLimit +
                " km/h) debe ser INFERIOR al límite de la vía (" + roadSpeedLimit + " km/h).");
        }

        this.roadSpeedLimit = roadSpeedLimit;
        this.signSpeedLimit = signSpeedLimit;
    }

    @Override
    protected String buildSignId() {
        return "SL_at" + roadSegment + "_" + startingPosition;
    }

    @Override
    protected String getSignalType() {
        return "SPEED_LIMIT";
    }

    @Override
    protected String getValue() {
        return String.format("%03d", signSpeedLimit);
    }

    @Override
    protected void enrichMessage(JSONObject msg) throws Exception {
        msg.put("road-speed-limit", roadSpeedLimit);
    }

    public void updateLimits(int newSpeed) {
        this.signSpeedLimit = newSpeed;
    }

    public int getRoadSpeedLimit() { return roadSpeedLimit; }
    public int getSignSpeedLimit() { return signSpeedLimit; }


    public static void main(String[] args) throws InterruptedException {

        final String BROKER = "tcp://localhost:1883";

        SmartCar car = new SmartCar("CAR_001", BROKER);
        car.getIntoRoad("R1", 500);

        SpeedLimitSign sign = new SpeedLimitSign(
            "SpeedLimitSign.R1S3.542", car, BROKER,
            "R1", "R1S3", 542, 120, 80
        );

        sign.connect();

        for (int i = 0; i < 6; i++) {
            sign.publishSignal();
            Thread.sleep(5000);
        }

        sign.disconnect();
    }
}
