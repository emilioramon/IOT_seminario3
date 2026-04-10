package componentes;

import utils.MySimpleLogger;

/**
 * Vehículo de emergencia (ambulancia, policía, bomberos).
 *
 * Extiende SmartCar añadiendo:
 *   - Tipo de vehículo (VehicleType)
 *   - Nivel de urgencia (EmergencyLevel)
 *   - Capacidad de publicar alertas de emergencia al servicio de orquestación
 *
 * El protocolo de uso normal del SmartCar (getIntoRoad / getOutRoad) sigue
 * funcionando igual: es compatible con el simulador existente.
 *
 * Flujo de emergencia:
 *   1. vehicle.getIntoRoad("R3s1", 100)   ← registra presencia en la vía
 *   2. vehicle.startEmergency("R5")        ← abre el corredor
 *   3. ... el vehículo circula ...
 *   4. vehicle.endEmergency()              ← cierra el corredor
 *   5. vehicle.getOutRoad("R3s1", 200)
 */
public class EmergencyVehicle extends SmartCar {

    private final VehicleType    vehicleType;
    private final EmergencyLevel emergencyLevel;

    private EmergencyAlertPublisher alertPublisher;
    private String currentDestination = null;

    public EmergencyVehicle(String id,
                            String brokerURL,
                            VehicleType vehicleType,
                            EmergencyLevel emergencyLevel) {
        super(id, brokerURL);
        this.vehicleType    = vehicleType;
        this.emergencyLevel = emergencyLevel;

        this.alertPublisher = new EmergencyAlertPublisher(
            id + ".emergency-publisher", this, brokerURL);
        this.alertPublisher.connect();

        MySimpleLogger.info(id,
            "Vehículo de emergencia creado: tipo=" + vehicleType.getDescription() +
            " nivel=" + emergencyLevel.getDescription());
    }

    /**
     * Activa el corredor de emergencia hacia el destino indicado.
     * Publica EMERGENCY_START para que el servicio de orquestación actúe.
     */
    public void startEmergency(String destinationRoad) {
        this.currentDestination = destinationRoad;

        String currentRoad = (this.rp != null) ? this.rp.getRoad() : "unknown";
        int    currentKm   = (this.rp != null) ? this.rp.getKm()   : 0;

        MySimpleLogger.warn(smartCarID,
            "*** EMERGENCIA INICIADA *** destino=" + destinationRoad);

        alertPublisher.publishAlert(
            EmergencyAlertPublisher.EVENT_START,
            this.smartCarID,
            this.vehicleType,
            this.emergencyLevel,
            currentRoad,
            currentKm,
            destinationRoad
        );
    }

    /**
     * Cierra el corredor de emergencia.
     * Publica EMERGENCY_END para que el orquestador restaure los semáforos.
     */
    public void endEmergency() {
        if (currentDestination == null) {
            MySimpleLogger.warn(smartCarID, "endEmergency() llamado sin emergencia activa.");
            return;
        }

        String currentRoad = (this.rp != null) ? this.rp.getRoad() : "unknown";
        int    currentKm   = (this.rp != null) ? this.rp.getKm()   : 0;

        MySimpleLogger.info(smartCarID, "*** EMERGENCIA FINALIZADA ***");

        alertPublisher.publishAlert(
            EmergencyAlertPublisher.EVENT_END,
            this.smartCarID,
            this.vehicleType,
            this.emergencyLevel,
            currentRoad,
            currentKm,
            currentDestination
        );

        this.currentDestination = null;
    }

    /**
     * En modo emergencia, la ambulancia IGNORA las señales de reducción de velocidad
     * (los accidentes en la vía no deben ralentizar al propio vehículo de emergencia).
     */
    @Override
    public void adjustSpeed(int signSpeedLimit, int roadSpeedLimit) {
        if (isInEmergency()) {
            MySimpleLogger.info(this.smartCarID,
                "[EMERGENCIA] Ignorando señal de velocidad (" + signSpeedLimit +
                " km/h). Manteniendo velocidad actual: " + this.getSpeed() + " km/h");
            return;
        }
        super.adjustSpeed(signSpeedLimit, roadSpeedLimit);
    }

    public VehicleType    getVehicleType()    { return vehicleType; }
    public EmergencyLevel getEmergencyLevel() { return emergencyLevel; }
    public boolean        isInEmergency()     { return currentDestination != null; }
}
