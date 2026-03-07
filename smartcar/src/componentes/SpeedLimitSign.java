package componentes;

import org.json.JSONObject;

/**
 * SpeedLimitSign - Señal de límite de velocidad.
 *
 * Extiende TrafficSign. Publica mensajes con signal-type = "SPEED_LIMIT"
 * e impone un límite inferior al general de la vía.
 *
 * Ejemplo de mensaje publicado:
 * {
 *   "id": "MSG_1477473769511",
 *   "type": "TRAFFIC_SIGNAL",
 *   "timestamp": 1477473769511,
 *   "msg": {
 *     "rt"               : "traffic-signal",
 *     "id"               : "SL_atR1S3_542",
 *     "road"             : "R1",
 *     "road-segment"     : "R1S3",
 *     "signal-type"      : "SPEED_LIMIT",
 *     "starting-position": 542,
 *     "ending-position"  : 542,
 *     "value"            : "080",
 *     "road-speed-limit" : 120
 *   }
 * }
 */
public class SpeedLimitSign extends TrafficSign {

    private final int roadSpeedLimit;   // límite general de la vía (km/h)
    private int signSpeedLimit;   // límite impuesto por la señal (km/h) < roadSpeedLimit

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

    /** Valor: límite impuesto como string de 3 dígitos, e.g. "080" */
    @Override
    protected String getValue() {
        return String.format("%03d", signSpeedLimit);
    }

    /** Añade el límite original de la vía al mensaje */
    @Override
    protected void enrichMessage(JSONObject msg) throws Exception {
        msg.put("road-speed-limit", roadSpeedLimit);
    }

    public void updateLimits(int newSpeed) {
        this.signSpeedLimit = newSpeed;
    }
    // ---------------------------------------------------------------
    // Getters específicos
    // ---------------------------------------------------------------
    public int getRoadSpeedLimit() { return roadSpeedLimit; }
    public int getSignSpeedLimit() { return signSpeedLimit; }

    // ---------------------------------------------------------------
    // Main de prueba
    // ---------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {

        final String BROKER = "tcp://localhost:1883";

        SmartCar car = new SmartCar("CAR_001", BROKER);
        car.getIntoRoad("R1", 500);

        // Carretera R1, tramo R1S3, km 542
        // Límite de vía 120 km/h → señal impone 80 km/h (por obras)
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
