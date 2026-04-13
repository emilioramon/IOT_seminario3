package componentes;

import org.json.JSONObject;
import utils.MySimpleLogger;


public abstract class TrafficSign extends MyMqttClient {

    protected final String signId;
    protected final String road;
    protected final String roadSegment;
    protected final int    startingPosition;
    protected final int    endingPosition;

    private static final String TOPIC_PATTERN = "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/%s/signals";

    public TrafficSign(String clientId,
                       SmartCar smartcar,
                       String brokerURL,
                       String road,
                       String roadSegment,
                       int    startingPosition,
                       int    endingPosition) {

        super(clientId, smartcar, brokerURL);
        this.road             = road;
        this.roadSegment      = roadSegment;
        this.startingPosition = startingPosition;
        this.endingPosition   = endingPosition;
        this.signId           = buildSignId();
    }


    protected abstract String buildSignId();  
    protected abstract String getSignalType();  
    protected abstract String getValue(); 


    public void publishSignal() {

        String topic = String.format(TOPIC_PATTERN, this.road);
        long   now   = System.currentTimeMillis();

        try {
            JSONObject msg = new JSONObject();
            msg.put("rt",                "traffic-signal");
            msg.put("id",                this.signId);
            msg.put("road",              this.road);
            msg.put("road-segment",      this.roadSegment);
            msg.put("signal-type",       this.getSignalType());
            msg.put("starting-position", this.startingPosition);
            msg.put("ending-position",   this.endingPosition);
            msg.put("value",             this.getValue());

            
            enrichMessage(msg);

            JSONObject m3 = new JSONObject();
            m3.put("id",        "MSG_" + now);
            m3.put("type",      "TRAFFIC_SIGNAL");
            m3.put("timestamp", now);
            m3.put("msg",       msg);

            MySimpleLogger.trace(this.clientId,
                "[" + getSignalType() + "] Publicando en topic: " + topic +
                " | valor: " + getValue());

            this.publish(topic, m3.toString());

        } catch (Exception e) {
            e.printStackTrace();
            MySimpleLogger.error(this.clientId, "Error publicando señal: " + e.getMessage());
        }
    }

  
    protected void enrichMessage(JSONObject msg) throws Exception {
        
    }

    public String getSignId()          { return signId; }
    public String getRoad()            { return road; }
    public String getRoadSegment()     { return roadSegment; }
    public int    getStartingPosition(){ return startingPosition; }
    public int    getEndingPosition()  { return endingPosition; }
}
