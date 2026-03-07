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

    // ---------------------------------------------------------------
    // Cada subclase define su propio ID y tipo de señal
    // ---------------------------------------------------------------
    protected abstract String buildSignId();    // e.g. "SL_atR1S3_542", "TL_atR1S3_542"
    protected abstract String getSignalType();  // e.g. "SPEED_LIMIT", "TRAFFIC_LIGHT"
    protected abstract String getValue();       // e.g. "080", "HLL"

    // ---------------------------------------------------------------
    // Publicación del mensaje (lógica común)
    // ---------------------------------------------------------------

    /**
     * Construye y publica un mensaje TRAFFIC_SIGNAL con los datos
     * proporcionados por la subclase.
     *
     * Formato:
     * {
     *   "id"        : "MSG_<timestamp>",
     *   "type"      : "TRAFFIC_SIGNAL",
     *   "timestamp" : <epoch_ms>,
     *   "msg": {
     *     "rt"               : "traffic-signal",
     *     "id"               : "<signId>",
     *     "road"             : "<road>",
     *     "road-segment"     : "<roadSegment>",
     *     "signal-type"      : "<SPEED_LIMIT|TRAFFIC_LIGHT|...>",
     *     "starting-position": <int>,
     *     "ending-position"  : <int>,
     *     "value"            : "<valor específico de la señal>"
     *   }
     * }
     */
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

            // Cada subclase puede añadir campos extra al mensaje
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

    /**
     * Hook opcional: las subclases pueden sobreescribir este método
     * para añadir campos extra al objeto "msg" sin duplicar la lógica
     * de publicación.
     */
    protected void enrichMessage(JSONObject msg) throws Exception {
        // vacío por defecto
    }

    public String getSignId()          { return signId; }
    public String getRoad()            { return road; }
    public String getRoadSegment()     { return roadSegment; }
    public int    getStartingPosition(){ return startingPosition; }
    public int    getEndingPosition()  { return endingPosition; }
}
