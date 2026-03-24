package tech.lemnova.continuum.domain.plan;

public record PlanLimits(
    int maxEntities,
    int maxNotes,
    int maxHabits,
    int maxHistoryDays,
    int maxMetadataSizeKb,
    boolean advancedMetrics,
    boolean dataExport,
    boolean calendarSync
) {
    public static PlanLimits free() {
        return new PlanLimits(20, 50, 3, 30, 10, false, false, false);
    }

    public static PlanLimits plus() {
        return new PlanLimits(100, 500, 10, 180, 50, true, false, false);
    }

    public static PlanLimits pro() {
        return new PlanLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 730, 500, true, true, true);
    }

    public static PlanLimits vision() {
        return new PlanLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 2048, true, true, true);
    }
}
