package tech.lemnova.continuum.domain.plan;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PlanConfiguration {

    private static final Map<PlanType, PlanLimits> LIMITS = Map.of(
        PlanType.FREE,   PlanLimits.free(),
        PlanType.PLUS,   PlanLimits.plus(),
        PlanType.PRO,    PlanLimits.pro(),
        PlanType.VISION, PlanLimits.vision()
    );

    public PlanLimits getLimits(PlanType plan) {
        return LIMITS.getOrDefault(plan, PlanLimits.free());
    }

    public boolean canCreateEntity(PlanType plan, long currentCount) {
        int max = getLimits(plan).maxEntities();
        return max == Integer.MAX_VALUE || currentCount < max;
    }

    public boolean canCreateNote(PlanType plan, long currentCount) {
        int max = getLimits(plan).maxNotes();
        return max == Integer.MAX_VALUE || currentCount < max;
    }

    public boolean canCreateHabit(PlanType plan, long currentHabits) {
        int max = getLimits(plan).maxHabits();
        return max == Integer.MAX_VALUE || currentHabits < max;
    }

    public boolean canAccessAdvancedMetrics(PlanType plan) {
        return getLimits(plan).advancedMetrics();
    }

    public boolean canExportData(PlanType plan) {
        return getLimits(plan).dataExport();
    }

    public boolean canSyncCalendar(PlanType plan) {
        return getLimits(plan).calendarSync();
    }

    public int getHistoryDays(PlanType plan) {
        return getLimits(plan).maxHistoryDays();
    }
}
