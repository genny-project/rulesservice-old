package life.genny.rules;

public enum Status {

	NEEDS_NO_ACTION("#5CB85C"),
    NEEDS_ACTION("#FFA500"),
    NEEDS_IMMEDIATE_ACTION("#FF0000");

    private String value;

    Status(String color) {
        this.value = color;
    }

    public String value() {
        return value;
    }

}
