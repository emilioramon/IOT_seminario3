package componentes;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import componentes.TrafficLightSign.LightState;
import utils.MySimpleLogger;

/**
 * Controla los LEDs físicos de un semáforo conectado a una Raspberry Pi
 * usando Pi4J2 con el proveedor PIGPIO (nomenclatura BCM).
 *
 * Conexión física:
 *   BCM 17 (PIN 11) -> LED Rojo
 *   BCM 27 (PIN 13) -> LED Ambar
 *   BCM 22 (PIN 15) -> LED Verde
 *   GND    (PIN 9)  -> GND del semáforo (-)
 */
public class GpioTrafficLight {

    private static final String TAG = "GPIO";

    // Pines BCM
    private static final int PIN_RED   = 17;
    private static final int PIN_AMBER = 27;
    private static final int PIN_GREEN = 22;

    private Context pi4j;
    private DigitalOutput redOutput;
    private DigitalOutput amberOutput;
    private DigitalOutput greenOutput;

    private boolean initialized = false;

    /**
     * Inicializa Pi4J2 y configura los pines como salidas digitales.
     * Si Pi4J no está disponible (e.g. en desarrollo fuera de la Pi),
     * funciona en modo simulación sin error.
     */
    public void initialize() {
        try {
            pi4j = Pi4J.newAutoContext();

            redOutput = pi4j.digitalOutput().create(
                DigitalOutput.newConfigBuilder(pi4j)
                    .id("red-led").name("LED Rojo")
                    .address(PIN_RED)
                    .shutdown(DigitalState.LOW)
                    .initial(DigitalState.LOW)
                    .provider("pigpio-digital-output")
                    .build()
            );

            amberOutput = pi4j.digitalOutput().create(
                DigitalOutput.newConfigBuilder(pi4j)
                    .id("amber-led").name("LED Ambar")
                    .address(PIN_AMBER)
                    .shutdown(DigitalState.LOW)
                    .initial(DigitalState.LOW)
                    .provider("pigpio-digital-output")
                    .build()
            );

            greenOutput = pi4j.digitalOutput().create(
                DigitalOutput.newConfigBuilder(pi4j)
                    .id("green-led").name("LED Verde")
                    .address(PIN_GREEN)
                    .shutdown(DigitalState.LOW)
                    .initial(DigitalState.LOW)
                    .provider("pigpio-digital-output")
                    .build()
            );

            initialized = true;
            MySimpleLogger.info(TAG,
                "GPIO inicializado: RED=BCM" + PIN_RED +
                " AMBER=BCM" + PIN_AMBER +
                " GREEN=BCM" + PIN_GREEN);

        } catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
            MySimpleLogger.warn(TAG,
                "Pi4J/PIGPIO no disponible. Modo simulacion (sin LEDs fisicos).");
        } catch (Exception e) {
            MySimpleLogger.error(TAG,
                "Error inicializando GPIO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Actualiza los LEDs físicos según el estado del semáforo.
     */
    public void updateLeds(LightState state) {
        if (!initialized) {
            MySimpleLogger.trace(TAG, "[SIM] LEDs -> " + state.getDescription());
            return;
        }

        try {
            String code = state.getCode(); // e.g. "HLL", "LHL", "LLH"

            redOutput.state(code.charAt(0) == 'H' ? DigitalState.HIGH : DigitalState.LOW);
            amberOutput.state(code.charAt(1) == 'H' ? DigitalState.HIGH : DigitalState.LOW);
            greenOutput.state(code.charAt(2) == 'H' ? DigitalState.HIGH : DigitalState.LOW);

            MySimpleLogger.info(TAG, "LEDs actualizados -> " + state.getDescription());

        } catch (Exception e) {
            MySimpleLogger.error(TAG, "Error actualizando LEDs: " + e.getMessage());
        }
    }

    /**
     * Apaga todos los LEDs y libera el contexto Pi4J.
     */
    public void shutdown() {
        if (!initialized) return;

        try {
            redOutput.low();
            amberOutput.low();
            greenOutput.low();
            pi4j.shutdown();
            MySimpleLogger.info(TAG, "GPIO liberado correctamente.");
        } catch (Exception e) {
            MySimpleLogger.error(TAG, "Error apagando GPIO: " + e.getMessage());
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
