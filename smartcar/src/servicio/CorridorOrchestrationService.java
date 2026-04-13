package servicio;

import componentes.EmergencyAlertPublisher;
import componentes.MyMqttClient;
import componentes.TrafficLightSign;
import componentes.TrafficLightSign.LightState;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import utils.MySimpleLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CorridorOrchestrationService extends MyMqttClient {

    private static final int AHEAD_SEGMENTS = 3;

    /** Tópico para recibir alertas de accidente de cualquier vía. */
    private static final String ACCIDENT_ALERTS_TOPIC =
        "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/+/alerts";

    /** Tópico base para publicar señales de velocidad a una vía concreta. */
    private static final String SIGNALS_TOPIC_PATTERN =
        "es/upv/pros/tatami/smartcities/traffic/PTPaterna/road/%s/signals";

    /** Velocidad máxima (km/h) que se impone en la vía tras un accidente. */
    private static final int ACCIDENT_SPEED_LIMIT = 30;

    private final Map<String, CorridorRoute> routes         = new HashMap<>();
    private final Map<String, String>        activeCorridors = new HashMap<>();

    public interface AccidentListener {
        void onAccident(String road, int km);
    }
    private AccidentListener accidentListener = null;

    public CorridorOrchestrationService(String clientId, String brokerURL) {
        super(clientId, null, brokerURL);
    }

    public void setAccidentListener(AccidentListener listener) {
        this.accidentListener = listener;
    }

    public void registerRoute(CorridorRoute route) {
        routes.put(route.getRouteId(), route);
        MySimpleLogger.info(clientId, "Ruta registrada: " + route.getRouteId() +
            " (" + route.size() + " segmentos)");
    }

    public void startListening() {
        this.subscribe(EmergencyAlertPublisher.EMERGENCY_TOPIC);
        this.subscribe(ACCIDENT_ALERTS_TOPIC);
        MySimpleLogger.info(clientId, "Escuchando emergencias en : " + EmergencyAlertPublisher.EMERGENCY_TOPIC);
        MySimpleLogger.info(clientId, "Escuchando accidentes en  : " + ACCIDENT_ALERTS_TOPIC);
    }


    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        super.messageArrived(topic, message);

        String payload = new String(message.getPayload());
        try {
            JSONObject envelope = new JSONObject(payload);
            String type = envelope.optString("type");

            if ("EMERGENCY_ALERT".equals(type)) {
                handleEmergencyAlert(envelope);
            } else if ("ROAD_INCIDENT".equals(type)) {
                handleRoadIncident(envelope);
            }
            else if (envelope.has("event") && "accident".equals(envelope.optString("event"))) {
                String road = envelope.optString("road", "unknown");
                int    km   = envelope.optInt("kp", 0);
                handleAccidentOnRoad(road, km);
            }

        } catch (Exception e) {
            MySimpleLogger.error(clientId, "Error procesando mensaje: " + e.getMessage());
        }
    }


    private void handleEmergencyAlert(JSONObject envelope) {
        try {
            JSONObject msg     = envelope.getJSONObject("msg");
            String event       = msg.getString("event");
            String vehicleId   = msg.getString("vehicle-id");
            String vehicleType = msg.getString("vehicle-type");
            String level       = msg.getString("level");
            String currentRoad = msg.getString("current-road");
            String destination = msg.getString("destination");

            MySimpleLogger.info(clientId,
                "EMERGENCIA: " + event + " | vehículo=" + vehicleId +
                " tipo=" + vehicleType + " nivel=" + level +
                " en=" + currentRoad + " destino=" + destination);

            if (EmergencyAlertPublisher.EVENT_START.equals(event)) {
                openCorridor(vehicleId, currentRoad, destination);
            } else if (EmergencyAlertPublisher.EVENT_END.equals(event)) {
                closeCorridor(vehicleId);
            }
        } catch (Exception e) {
            MySimpleLogger.error(clientId, "Error en handleEmergencyAlert: " + e.getMessage());
        }
    }

    private void openCorridor(String vehicleId, String currentRoad, String destination) {
        CorridorRoute route = findRoute(currentRoad, destination);
        if (route == null) {
            MySimpleLogger.warn(clientId,
                "No se encontró ruta de '" + currentRoad + "' a '" + destination + "'");
            return;
        }

        int startIdx = route.indexOf(currentRoad);
        if (startIdx < 0) startIdx = 0;

        List<CorridorRoute.RouteSegment> ahead = route.getAheadSegments(startIdx, AHEAD_SEGMENTS);

        MySimpleLogger.warn(clientId,
            "ABRIENDO CORREDOR : ruta=" + route.getRouteId() +
            " vehículo=" + vehicleId + " | " + ahead.size() + " segmentos en verde");

        for (CorridorRoute.RouteSegment seg : ahead) {
            TrafficLightSign corridorLight = seg.getCorridorLight();
            if (corridorLight != null) {
                corridorLight.setState(LightState.GREEN);
                MySimpleLogger.info(clientId, "  [VERDE] " + seg.getRoadSegment() +
                    " => " + corridorLight.getSignId());
            }
            for (TrafficLightSign conflict : seg.getConflictingLights()) {
                conflict.setState(LightState.RED);
                MySimpleLogger.info(clientId, "  [ROJO ] cruce " + seg.getRoadSegment() +
                    " => " + conflict.getSignId());
            }
        }

        activeCorridors.put(vehicleId, route.getRouteId());
    }

    private void closeCorridor(String vehicleId) {
        String routeId = activeCorridors.remove(vehicleId);
        if (routeId == null) {
            MySimpleLogger.warn(clientId, "EMERGENCY_END sin corredor activo para: " + vehicleId);
            return;
        }

        CorridorRoute route = routes.get(routeId);
        if (route == null) return;

        MySimpleLogger.info(clientId,
            "CERRANDO CORREDOR : ruta=" + routeId + " vehículo=" + vehicleId);

        for (CorridorRoute.RouteSegment seg : route.getAllSegments()) {
            TrafficLightSign corridorLight = seg.getCorridorLight();
            if (corridorLight != null) {
                corridorLight.setState(LightState.RED);
                MySimpleLogger.info(clientId, "  [REST=>ROJO ] " + seg.getRoadSegment() +
                    " => " + corridorLight.getSignId());
            }
            for (TrafficLightSign conflict : seg.getConflictingLights()) {
                conflict.setState(LightState.GREEN);
                MySimpleLogger.info(clientId, "  [REST=>VERDE] cruce " + seg.getRoadSegment() +
                    " => " + conflict.getSignId());
            }
        }
    }

    private void handleRoadIncident(JSONObject envelope) {
        try {
            JSONObject msg = envelope.getJSONObject("msg");
            String incidentType = msg.optString("incident-type", "");
            if (!"TRAFFIC_ACCIDENT".equals(incidentType)) return;

            String road = msg.optString("road", "unknown");
            int    km   = msg.optInt("starting-position", 0);
            handleAccidentOnRoad(road, km);

        } catch (Exception e) {
            MySimpleLogger.error(clientId, "Error en handleRoadIncident: " + e.getMessage());
        }
    }

    private void handleAccidentOnRoad(String road, int km) {
        MySimpleLogger.warn(clientId,
            "ACCIDENTE detectado en vía=" + road + " km=" + km +
            " => reduciendo velocidad a " + ACCIDENT_SPEED_LIMIT + " km/h");

        final AccidentListener listener = accidentListener;
        Thread t = new Thread(() -> {
            publishSpeedReduction(road, ACCIDENT_SPEED_LIMIT, 100);
            if (listener != null) {
                try {
                    listener.onAccident(road, km);
                } catch (Throwable ex) {
                    MySimpleLogger.error(clientId,
                        "Error en accidentListener: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        }, "accident-handler-thread");
        t.setDaemon(false);
        t.start();
    }


    private void publishSpeedReduction(String road, int reducedSpeed, int roadSpeedLimit) {
        String topic = String.format(SIGNALS_TOPIC_PATTERN, road);
        long   now   = System.currentTimeMillis();
        try {
            JSONObject msg = new JSONObject();
            msg.put("rt",               "traffic-signal");
            msg.put("id",               "SL_ACCIDENT_" + road + "_" + now);
            msg.put("road",             road);
            msg.put("road-segment",     road);
            msg.put("signal-type",      "SPEED_LIMIT");
            msg.put("starting-position", 0);
            msg.put("ending-position",  9999);
            msg.put("value",            String.format("%03d", reducedSpeed));
            msg.put("road-speed-limit", roadSpeedLimit);

            JSONObject envelope = new JSONObject();
            envelope.put("id",        "MSG_" + now);
            envelope.put("type",      "TRAFFIC_SIGNAL");
            envelope.put("timestamp", now);
            envelope.put("msg",       msg);

            this.publish(topic, envelope.toString());
            MySimpleLogger.info(clientId,
                "Señal de velocidad reducida publicada: " + reducedSpeed + " km/h => " + topic);

        } catch (Exception e) {
            MySimpleLogger.error(clientId, "Error publicando reducción de velocidad: " + e.getMessage());
        }
    }

    private CorridorRoute findRoute(String currentRoad, String destination) {
        for (CorridorRoute route : routes.values()) {
            int fromIdx = route.indexOf(currentRoad);
            int toIdx   = route.indexOf(destination);
            if (fromIdx >= 0 || toIdx >= 0) return route;
        }
        if (routes.size() == 1) return routes.values().iterator().next();
        return null;
    }
}
