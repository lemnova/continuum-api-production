package tech.lemnova.continuum_backend.subscription;

public enum PlanType {
    FREE("free", 5, 0.0),
    PRO("pro", 999, 9.90);

    private final String name;
    private final int habitsLimit;
    private final double price;

    PlanType(String name, int habitsLimit, double price) {
        this.name = name;
        this.habitsLimit = habitsLimit;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getHabitsLimit() {
        return habitsLimit;
    }

    public double getPrice() {
        return price;
    }

    public static PlanType fromString(String text) {
        for (PlanType plan : PlanType.values()) {
            if (plan.name.equalsIgnoreCase(text)) {
                return plan;
            }
        }
        return FREE;
    }
}
