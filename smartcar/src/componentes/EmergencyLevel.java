package componentes;

public enum EmergencyLevel {
    HIGH   ("HIGH",   "Máxima urgencia"),
    MEDIUM ("MEDIUM", "Urgencia media");

    private final String code;
    private final String description;

    EmergencyLevel(String code, String description) {
        this.code        = code;
        this.description = description;
    }

    public String getCode()        { return code; }
    public String getDescription() { return description; }
}
