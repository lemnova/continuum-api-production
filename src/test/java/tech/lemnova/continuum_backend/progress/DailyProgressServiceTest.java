package tech.lemnova.continuum_backend.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.lemnova.continuum_backend.exception.UnauthorizedException;
import tech.lemnova.continuum_backend.habit.Habit;
import tech.lemnova.continuum_backend.habit.HabitRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyProgressService Tests")
class DailyProgressServiceTest {

    @Mock
    private DailyProgressRepository dailyProgressRepository;

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private DailyProgressService dailyProgressService;

    private Habit testHabit;
    private DailyProgress testProgress;

    @BeforeEach
    void setUp() {
        testHabit = new Habit();
        testHabit.setId("habit123");
        testHabit.setUserId("user123");

        testProgress = new DailyProgress();
        testProgress.setId("progress123");
        testProgress.setHabitId("habit123");
        testProgress.setUserId("user123");
        testProgress.setDate(LocalDate.now());
        testProgress.setCompleted(false);
    }

    @Test
    @DisplayName("Should toggle progress successfully")
    void shouldToggleProgressSuccessfully() {
        LocalDate today = LocalDate.now();

        when(habitRepository.findById("habit123")).thenReturn(Optional.of(testHabit));
        when(dailyProgressRepository.findByHabitIdAndDate("habit123", today))
            .thenReturn(Optional.of(testProgress));
        when(dailyProgressRepository.save(any(DailyProgress.class))).thenReturn(testProgress);

        DailyProgressDTO result = dailyProgressService.toggleProgress("user123", "habit123", today);

        assertNotNull(result);
        verify(dailyProgressRepository).save(any(DailyProgress.class));
    }

    @Test
    @DisplayName("Should create new progress when toggling for first time")
    void shouldCreateNewProgressWhenTogglingFirstTime() {
        LocalDate today = LocalDate.now();

        when(habitRepository.findById("habit123")).thenReturn(Optional.of(testHabit));
        when(dailyProgressRepository.findByHabitIdAndDate("habit123", today))
            .thenReturn(Optional.empty());
        when(dailyProgressRepository.save(any(DailyProgress.class))).thenReturn(testProgress);

        DailyProgressDTO result = dailyProgressService.toggleProgress("user123", "habit123", today);

        assertNotNull(result);
        verify(dailyProgressRepository).save(argThat(progress -> 
            progress.getCompleted() && progress.getHabitId().equals("habit123")
        ));
    }

    @Test
    @DisplayName("Should throw exception when toggling progress for other user's habit")
    void shouldThrowExceptionWhenTogglingOtherUserHabit() {
        testHabit.setUserId("otherUser");
        LocalDate today = LocalDate.now();

        when(habitRepository.findById("habit123")).thenReturn(Optional.of(testHabit));

        assertThrows(UnauthorizedException.class, () -> 
            dailyProgressService.toggleProgress("user123", "habit123", today)
        );

        verify(dailyProgressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should calculate daily score correctly")
    void shouldCalculateDailyScoreCorrectly() {
        LocalDate today = LocalDate.now();

        DailyProgress completed1 = new DailyProgress();
        completed1.setCompleted(true);
        DailyProgress completed2 = new DailyProgress();
        completed2.setCompleted(true);
        DailyProgress notCompleted = new DailyProgress();
        notCompleted.setCompleted(false);

        when(dailyProgressRepository.findByUserIdAndDate("user123", today))
            .thenReturn(Arrays.asList(completed1, completed2, notCompleted));

        Integer score = dailyProgressService.calculateDailyScore("user123", today);

        assertEquals(66, score);
    }

    @Test
    @DisplayName("Should return 0 when no progress exists")
    void shouldReturnZeroWhenNoProgressExists() {
        LocalDate today = LocalDate.now();

        when(dailyProgressRepository.findByUserIdAndDate("user123", today))
            .thenReturn(List.of());

        Integer score = dailyProgressService.calculateDailyScore("user123", today);

        assertEquals(0, score);
    }
}
