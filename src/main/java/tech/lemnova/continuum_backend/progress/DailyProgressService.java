package tech.lemnova.continuum_backend.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.lemnova.continuum_backend.exception.ResourceNotFoundException;
import tech.lemnova.continuum_backend.exception.UnauthorizedException;
import tech.lemnova.continuum_backend.habit.Habit;
import tech.lemnova.continuum_backend.habit.HabitRepository;

@Service
public class DailyProgressService {

    private static final Logger logger = LoggerFactory.getLogger(
        DailyProgressService.class
    );

    private final DailyProgressRepository dailyProgressRepository;
    private final HabitRepository habitRepository;

    public DailyProgressService(
        DailyProgressRepository dailyProgressRepository,
        HabitRepository habitRepository
    ) {
        this.dailyProgressRepository = dailyProgressRepository;
        this.habitRepository = habitRepository;
    }

    // ✅ Validação de ownership
    private void validateHabitOwnership(String userId, String habitId) {
        Habit habit = habitRepository
            .findById(habitId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Habit not found with id: " + habitId
                )
            );

        if (!habit.getUserId().equals(userId)) {
            logger.warn(
                "User {} attempted to access habit {} owned by {}",
                userId,
                habitId,
                habit.getUserId()
            );
            throw new UnauthorizedException(
                "You don't have permission to access this habit"
            );
        }
    }

    // GET PROGRESS FOR HABIT (com validação)
    public List<DailyProgressDTO> getProgressForHabit(
        String userId,
        String habitId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        logger.debug(
            "User {} fetching progress for habit {} from {} to {}",
            userId,
            habitId,
            startDate,
            endDate
        );

        validateHabitOwnership(userId, habitId);

        return dailyProgressRepository
            .findByHabitIdAndDateBetween(habitId, startDate, endDate)
            .stream()
            .map(DailyProgressDTO::from)
            .collect(Collectors.toList());
    }

    // GET PROGRESS FOR USER
    public List<DailyProgressDTO> getProgressForUser(
        String userId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        logger.debug(
            "User {} fetching all progress from {} to {}",
            userId,
            startDate,
            endDate
        );

        return dailyProgressRepository
            .findByUserIdAndDateBetween(userId, startDate, endDate)
            .stream()
            .map(DailyProgressDTO::from)
            .collect(Collectors.toList());
    }

    // GET TODAY'S PROGRESS FOR USER
    public List<DailyProgressDTO> getTodayProgressForUser(String userId) {
        LocalDate today = LocalDate.now();

        logger.debug("User {} fetching today's progress", userId);

        return dailyProgressRepository
            .findByUserIdAndDate(userId, today)
            .stream()
            .map(DailyProgressDTO::from)
            .collect(Collectors.toList());
    }

    // TOGGLE PROGRESS (com validação)
    @Transactional
    public DailyProgressDTO toggleProgress(
        String userId,
        String habitId,
        LocalDate date
    ) {
        logger.info(
            "User {} toggling progress for habit {} on {}",
            userId,
            habitId,
            date
        );

        validateHabitOwnership(userId, habitId);

        DailyProgress progress = dailyProgressRepository
            .findByHabitIdAndDate(habitId, date)
            .orElse(new DailyProgress());

        if (progress.getId() == null) {
            // Criar novo registro
            progress.setHabitId(habitId);
            progress.setUserId(userId);
            progress.setDate(date);
            progress.setCompleted(true);
            progress.setCreatedAt(Instant.now());

            logger.debug(
                "Creating new progress entry for habit {} on {}",
                habitId,
                date
            );
        } else {
            // Toggle
            progress.setCompleted(!progress.getCompleted());

            logger.debug(
                "Toggling progress for habit {} on {} to {}",
                habitId,
                date,
                progress.getCompleted()
            );
        }

        progress.setUpdatedAt(Instant.now());
        DailyProgress saved = dailyProgressRepository.save(progress);

        return DailyProgressDTO.from(saved);
    }

    // CALCULATE DAILY SCORE FOR USER
    public Integer calculateDailyScore(String userId, LocalDate date) {
        logger.debug("Calculating daily score for user {} on {}", userId, date);

        List<DailyProgress> progressList =
            dailyProgressRepository.findByUserIdAndDate(userId, date);

        long completed = progressList
            .stream()
            .filter(DailyProgress::getCompleted)
            .count();

        long total = progressList.size();

        if (total == 0) {
            return 0;
        }

        int score = (int) ((completed * 100) / total);

        logger.debug(
            "Daily score for user {} on {}: {} ({}/{})",
            userId,
            date,
            score,
            completed,
            total
        );

        return score;
    }
}
