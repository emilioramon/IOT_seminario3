package componentes;

/**
 * TrafficLightSign - Semáforo.
 *
 * Extiende TrafficSign. Publica mensajes con signal-type = "TRAFFIC_LIGHT".
 *
 * El estado del semáforo se codifica en el campo "value" con 3 caracteres,
 * uno por cada luz (H=encendida, L=apagada):
 *   Posición 0 => Rojo
 *   Posición 1 => Ámbar
 *   Posición 2 => Verde
 *
 *   "HLL" => Rojo encendido  (stop)
 *   "LHL" => Ámbar encendido (precaución)
 *   "LLH" => Verde encendido (paso libre)
 *
 * Ejemplo de mensaje publicado:
 * {
 *   "id": "MSG_1477473769511",
 *   "type": "TRAFFIC_SIGNAL",
 *   "timestamp": 1477473769511,
 *   "msg": {
 *     "rt"               : "traffic-signal",
 *     "id"               : "TL_atR1S3_542",
 *     "road"             : "R1",
 *     "road-segment"     : "R1S3",
 *     "signal-type"      : "TRAFFIC_LIGHT",
 *     "starting-position": 542,
 *     "ending-position"  : 542,
 *     "value"            : "HLL"
 *   }
 * }
 */
public class TrafficLightSign extends TrafficSign {

    // ---------------------------------------------------------------
    // Estados posibles del semáforo
    // ---------------------------------------------------------------
    public enum LightState {
        RED    ("HLL", "Rojo   - STOP"),
        AMBER  ("LHL", "Ámbar  - PRECAUCIÓN"),
        GREEN  ("LLH", "Verde  - PASO LIBRE");

        private final String code;
        private final String description;

        LightState(String code, String description) {
            this.code        = code;
            this.description = description;
        }

        public String getCode()        { return code; }
        public String getDescription() { return description; }
    }

    // Estado actual del semáforo
    private LightState currentState;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------
    public TrafficLightSign(String clientId,
                            SmartCar smartcar,
                            String brokerURL,
                            String road,
                            String roadSegment,
                            int    startingPosition,
                            int    endingPosition,
                            LightState initialState) {

        super(clientId, smartcar, brokerURL, road, roadSegment, startingPosition, endingPosition);
        this.currentState = initialState;
    }

    // ---------------------------------------------------------------
    // Implementación de métodos abstractos
    // ---------------------------------------------------------------
    @Override
    protected String buildSignId() {
        return "TL_at" + roadSegment + "_" + startingPosition;
    }

    @Override
    protected String getSignalType() {
        return "TRAFFIC_LIGHT";
    }

    /** Valor: código de 3 caracteres del estado actual, ejemplo "HLL" */
    @Override
    protected String getValue() {
        return currentState.getCode();
    }

    // ---------------------------------------------------------------
    // Control del semáforo
    // ---------------------------------------------------------------

    /** Cambia el estado y publica automáticamente el nuevo valor */
    public void setState(LightState newState) {
        this.currentState = newState;
        System.out.println("[" + signId + "] Estado => " + newState.getDescription());
        publishSignal();
    }

    public LightState getState() {
        return currentState;
    }

    /**
     * Simula un ciclo completo de semáforo:
     *   Verde (greenMs) => Ámbar (amberMs) => Rojo (redMs)
     * y repite 'cycles' veces.
     */
    public void runCycle(int cycles, long greenMs, long amberMs, long redMs)
            throws InterruptedException {

        for (int i = 0; i < cycles; i++) {
            setState(LightState.GREEN);
            Thread.sleep(greenMs);

            setState(LightState.AMBER);
            Thread.sleep(amberMs);

            setState(LightState.RED);
            Thread.sleep(redMs);
        }
    }

    // ---------------------------------------------------------------
    // Main de prueba
    // ---------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {

        final String BROKER = "tcp://localhost:1883";

        SmartCar car = new SmartCar("CAR_001", BROKER);
        car.getIntoRoad("R1", 500);   // vehículo circulando por R1

        // Semáforo en R1, tramo R1S3, posición km 542
        // Estado inicial: Rojo (hay vehículos en el tramo)
        TrafficLightSign trafficLight = new TrafficLightSign(
            "TrafficLight.R1S3.542", car, BROKER,
            "R1", "R1S3", 542, 542,
            LightState.RED
        );

        trafficLight.connect();

        // Publica el estado inicial (rojo)
        trafficLight.publishSignal();

        // Ejecuta 3 ciclos completos: 10s verde, 3s ámbar, 10s rojo
        trafficLight.runCycle(3, 10_000, 3_000, 10_000);

        trafficLight.disconnect();
    }
}
