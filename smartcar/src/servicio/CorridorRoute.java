package servicio;

import componentes.TrafficLightSign;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa una ruta como lista ordenada de segmentos de carretera,
 * cada uno con su semáforo asociado y (opcionalmente) semáforos conflictivos
 * del cruce perpendicular.
 *
 * El orquestador la consulta para saber qué semáforos poner en verde
 * (los del corredor) y cuáles en rojo (los conflictivos del cruce).
 *
 * Ejemplo de uso:
 *   CorridorRoute route = new CorridorRoute("hospital-route");
 *   route.addSegment(new RouteSegment("R3s1", tlR3s1, List.of(tlCruce1)));
 *   route.addSegment(new RouteSegment("R3s2", tlR3s2, List.of(tlCruce2)));
 *   route.addSegment(new RouteSegment("R5s1", tlR5s1, List.of(tlCruce3)));
 */
public class CorridorRoute {

    /** Un segmento de la ruta con su semáforo principal y los conflictivos del cruce. */
    public static class RouteSegment {
        private final String           roadSegment;
        private final TrafficLightSign corridorLight;       // semáforo del corredor (se pone verde)
        private final List<TrafficLightSign> conflictingLights; // semáforos del cruce (se ponen rojo)

        public RouteSegment(String roadSegment,
                            TrafficLightSign corridorLight,
                            List<TrafficLightSign> conflictingLights) {
            this.roadSegment      = roadSegment;
            this.corridorLight    = corridorLight;
            this.conflictingLights = conflictingLights != null
                ? conflictingLights
                : Collections.emptyList();
        }

        public String              getRoadSegment()      { return roadSegment; }
        public TrafficLightSign    getCorridorLight()    { return corridorLight; }
        public List<TrafficLightSign> getConflictingLights() { return conflictingLights; }
    }

    private final String            routeId;
    private final List<RouteSegment> segments = new ArrayList<>();

    public CorridorRoute(String routeId) {
        this.routeId = routeId;
    }

    public void addSegment(RouteSegment segment) {
        segments.add(segment);
    }

    /**
     * Devuelve los segmentos que van desde el índice `fromIndex` (inclusive)
     * hasta `fromIndex + count` (exclusive), sin salirse del rango.
     * Se usa para obtener los N segmentos por delante del vehículo.
     */
    public List<RouteSegment> getAheadSegments(int fromIndex, int count) {
        int end = Math.min(fromIndex + count, segments.size());
        if (fromIndex >= segments.size() || fromIndex < 0) return Collections.emptyList();
        return segments.subList(fromIndex, end);
    }

    /**
     * Devuelve el índice del segmento cuyo roadSegment coincide con el dado,
     * o -1 si no se encuentra.
     */
    public int indexOf(String roadSegment) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getRoadSegment().equals(roadSegment)) return i;
        }
        return -1;
    }

    public List<RouteSegment> getAllSegments() { return Collections.unmodifiableList(segments); }
    public String getRouteId()                 { return routeId; }
    public int    size()                       { return segments.size(); }
}
