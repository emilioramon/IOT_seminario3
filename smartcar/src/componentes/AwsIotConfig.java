package componentes;

import javax.net.ssl.SSLSocketFactory;
import utils.MySimpleLogger;

/**
 * Lee la configuracion de AWS IoT desde variables de entorno y construye
 * (opcionalmente) un cliente TrafficLightShadow ya conectado.
 *
 * Variables soportadas:
 *   AWS_IOT_ENDPOINT            - ej. a1xkezf8guqg9c-ats.iot.us-east-1.amazonaws.com
 *   AWS_IOT_KEYSTORE            - ruta al .p12 generado con scripts/generate-aws-keystore.sh
 *   AWS_IOT_KEYSTORE_PASSWORD   - password del .p12 (default "changeit")
 *   AWS_IOT_CA_CERT             - ruta a AmazonRootCA1.pem
 *   AWS_IOT_THING_NAME          - nombre del Thing (ej. ttmi010)
 *   AWS_IOT_CLIENT_ID_PREFIX    - prefijo obligatorio por la policy (ej. mucnap-iot26)
 *
 * Si falta cualquiera de ellas, build(...) devuelve null y los callers
 * funcionan como antes (sin shadow, solo tambori).
 */
public class AwsIotConfig {

    private static final String TAG = "AwsIotConfig";

    public static TrafficLightShadow buildShadowOrNull(String clientSuffix) {
        String endpoint  = System.getenv("AWS_IOT_ENDPOINT");
        String keystore  = System.getenv("AWS_IOT_KEYSTORE");
        String ksPass    = System.getenv().getOrDefault("AWS_IOT_KEYSTORE_PASSWORD", "changeit");
        String caCert    = System.getenv("AWS_IOT_CA_CERT");
        String thingName = System.getenv("AWS_IOT_THING_NAME");
        String prefix    = System.getenv().getOrDefault("AWS_IOT_CLIENT_ID_PREFIX", "mucnap-iot26");

        if (isBlank(endpoint) || isBlank(keystore) || isBlank(caCert) || isBlank(thingName)) {
            MySimpleLogger.info(TAG,
                "AWS IoT Shadow deshabilitado (faltan variables AWS_IOT_*). Operando solo sobre tambori.");
            return null;
        }

        String brokerUrl = "ssl://" + endpoint + ":8883";
        String clientId  = prefix + "-" + sanitize(clientSuffix);

        try {
            SSLSocketFactory factory = AwsTlsConfig.buildSocketFactory(keystore, ksPass, caCert);
            TrafficLightShadow shadow = new TrafficLightShadow(clientId, brokerUrl, thingName);
            shadow.setSslSocketFactory(factory);
            shadow.connect();
            shadow.subscribeToShadow();
            MySimpleLogger.warn(TAG,
                "AWS IoT Shadow conectado: clientId=" + clientId + " thing=" + thingName);
            return shadow;

        } catch (Exception e) {
            MySimpleLogger.error(TAG,
                "No se pudo conectar con AWS IoT Shadow: " + e.getMessage() +
                ". Continuando solo con tambori.");
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** ClientId en AWS IoT: solo ASCII, sin espacios ni caracteres raros. */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
