package tech.lemnova.continuum.application.service;

import org.springframework.stereotype.Service;
import tech.lemnova.continuum.application.exception.BadRequestException;
import tech.lemnova.continuum.application.exception.NotFoundException;
import tech.lemnova.continuum.controller.dto.tracking.TrackEventRequest;
import tech.lemnova.continuum.domain.entity.Entity;
import tech.lemnova.continuum.domain.plan.PlanConfiguration;
import tech.lemnova.continuum.domain.tracking.TrackingEvent;
import tech.lemnova.continuum.domain.user.User;
import tech.lemnova.continuum.domain.user.UserRepository;
import tech.lemnova.continuum.infra.vault.VaultDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrackingService {

    private final UserRepository userRepo;
    private final VaultDataService vaultData;
    private final EntityService entityService;
    private final PlanConfiguration planConfig;

    public TrackingService(UserRepository userRepo,
                           VaultDataService vaultData,
                           EntityService entityService,
                           PlanConfiguration planConfig) {
        this.userRepo       = userRepo;
        this.vaultData      = vaultData;
        this.entityService  = entityService;
        this.planConfig     = planConfig;
    }

    public TrackingEvent track(String userId, String entityId, TrackEventRequest req) {
        User user = getUser(userId);
        Entity entity = entityService.get(userId, entityId);
        if (!entity.isTrackable())
            throw new BadRequestException("Entity is not trackable. Enable tracking first.");

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        List<TrackingEvent> events = vaultData.readTrackingEvents(user.getVaultId());

        // Upsert: se já existe para (entityId, date), atualiza
        Optional<TrackingEvent> existing = events.stream()
                .filter(e -> e.getEntityId().equals(entityId) && date.equals(e.getDate()))
                .findFirst();

        TrackingEvent event;
        if (existing.isPresent()) {
            event = existing.get();
        } else {
            event = new TrackingEvent();
            event.setId(UUID.randomUUID().toString().replace("-", ""));
            event.setUserId(userId);
            event.setEntityId(entityId);
            event.setDate(date);
            event.setCreatedAt(Instant.now());
            events.add(event);
        }

        event.setValue(req.value() != null ? req.value() : 1);
        event.setDecimalValue(req.decimalValue());
        event.setNote(req.note());
        event.setUpdatedAt(Instant.now());

        vaultData.writeTrackingEvents(user.getVaultId(), events);
        return event;
    }

    public void untrack(String userId, String entityId, LocalDate date) {
        User user = getUser(userId);
        List<TrackingEvent> events = vaultData.readTrackingEvents(user.getVaultId());
        events.removeIf(e -> e.getEntityId().equals(entityId) && date.equals(e.getDate()));
        vaultData.writeTrackingEvents(user.getVaultId(), events);
    }

    public Map<LocalDate, Double> getHeatmap(String userId, String entityId) {
        User user = getUser(userId);
        int days = planConfig.getHistoryDays(user.getPlan());
        LocalDate end   = LocalDate.now();
        LocalDate start = days == Integer.MAX_VALUE ? end.minusYears(10) : end.minusDays(days);
        return getHeatmap(userId, entityId, start, end);
    }

    public Map<LocalDate, Double> getHeatmap(String userId, String entityId,
                                               LocalDate start, LocalDate end) {
        User user = getUser(userId);
        return vaultData.readTrackingEvents(user.getVaultId()).stream()
                .filter(e -> e.getEntityId().equals(entityId)
                        && !e.getDate().isBefore(start)
                        && !e.getDate().isAfter(end))
                .collect(Collectors.toMap(
                        TrackingEvent::getDate,
                        e -> e.getNumericValue().doubleValue()));
    }

    public TrackingStats getStats(String userId, String entityId) {
        User user = getUser(userId);
        List<TrackingEvent> all = vaultData.readTrackingEvents(user.getVaultId()).stream()
                .filter(e -> e.getEntityId().equals(entityId))
                .sorted(Comparator.comparing(TrackingEvent::getDate, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (all.isEmpty()) return new TrackingStats(0, 0, 0.0, 0.0);

        int streak        = computeStreak(all);
        int longestStreak = computeLongestStreak(all);
        double avg        = all.stream()
                .mapToDouble(e -> e.getNumericValue().doubleValue()).average().orElse(0.0);

        // weeklyCompletionRate: dias de eventos nesta semana / 7
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        Set<LocalDate> datesThisWeek = all.stream()
                .map(TrackingEvent::getDate)
                .filter(d -> !d.isBefore(weekStart) && !d.isAfter(today))
                .collect(Collectors.toSet());
        double weeklyCompletionRate = datesThisWeek.size() / 7.0;

        return new TrackingStats(streak, longestStreak, avg, weeklyCompletionRate);
    }

    public List<TrackingEvent> getTodayEvents(String userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();
        return vaultData.readTrackingEvents(user.getVaultId()).stream()
                .filter(e -> today.equals(e.getDate()))
                .collect(Collectors.toList());
    }

    // ── private ───────────────────────────────────────────────────────────────

    private int computeStreak(List<TrackingEvent> events) {
        Set<LocalDate> dates = events.stream().map(TrackingEvent::getDate).collect(Collectors.toSet());
        LocalDate today = LocalDate.now();
        int streak = 0;
        LocalDate check = dates.contains(today) ? today : today.minusDays(1);
        while (dates.contains(check)) { streak++; check = check.minusDays(1); }
        return streak;
    }

    private int computeLongestStreak(List<TrackingEvent> events) {
        if (events.isEmpty()) return 0;
        List<LocalDate> sorted = events.stream()
                .map(TrackingEvent::getDate).distinct().sorted().collect(Collectors.toList());
        int longest = 1, current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)) == 1) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    private User getUser(String userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public record TrackingStats(
            int currentStreak, int longestStreak,
            double averageValue, double weeklyCompletionRate) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// APPLICATION — MetricsService [ARCH-6][V11-ARCH]
// Lê NoteReferences do vault B2.
// ─────────────────────────────────────────────────────────────────────────────
