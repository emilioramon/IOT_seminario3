package componentes;

public enum VehicleType {
    AMBULANCE   ("ambulance",   "Ambulancia"),
    POLICE      ("police",      "Policía"),
    FIREFIGHTER ("firefighter", "Bomberos");

    private final String code;
    private final String description;

    VehicleType(String code, String description) {
        this.code        = code;
        this.description = description;
    }

    public String getCode()        { return code; }
    public String getDescription() { return description; }
}
