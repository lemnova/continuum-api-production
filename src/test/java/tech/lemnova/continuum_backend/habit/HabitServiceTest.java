package tech.lemnova.continuum_backend.habit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.lemnova.continuum_backend.exception.BadRequestException;
import tech.lemnova.continuum_backend.exception.ResourceNotFoundException;
import tech.lemnova.continuum_backend.exception.UnauthorizedException;
import tech.lemnova.continuum_backend.habit.dtos.HabitCreateDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitDTO;
import tech.lemnova.continuum_backend.habit.dtos.HabitUpdateDTO;
import tech.lemnova.continuum_backend.progress.DailyProgressRepository;
import tech.lemnova.continuum_backend.subscription.SubscriptionService;
import tech.lemnova.continuum_backend.user.User;
import tech.lemnova.continuum_backend.user.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("HabitService Tests")
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyProgressRepository dailyProgressRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private HabitService habitService;

    private User testUser;
    private Habit testHabit;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user123");
        testUser.setUsername("testuser");

        testHabit = new Habit();
        testHabit.setId("habit123");
        testHabit.setName("Exercise");
        testHabit.setDescription("Daily exercise");
        testHabit.setUserId("user123");
        testHabit.setDeleted(false);
    }

    @Test
    @DisplayName("Should create habit successfully")
    void shouldCreateHabitSuccessfully() {
        HabitCreateDTO dto = new HabitCreateDTO(
            "Exercise",
            "Daily exercise",
            "Health",
            "💪",
            "#FF5733"
        );

        when(userRepository.findById("user123")).thenReturn(
            Optional.of(testUser)
        );
        when(
            habitRepository.countByUserIdAndDeletedFalse("user123")
        ).thenReturn(2L);
        when(subscriptionService.canCreateHabit("user123", 2)).thenReturn(true);
        when(habitRepository.save(any(Habit.class))).thenReturn(testHabit);

        HabitDTO result = habitService.create("user123", dto);

        assertNotNull(result);
        assertEquals("Exercise", result.name());
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    @DisplayName("Should throw exception when habit limit reached")
    void shouldThrowExceptionWhenHabitLimitReached() {
        HabitCreateDTO dto = new HabitCreateDTO(
            "Exercise",
            "Daily exercise",
            "Health",
            "💪",
            "#FF5733"
        );

        when(userRepository.findById("user123")).thenReturn(
            Optional.of(testUser)
        );
        when(
            habitRepository.countByUserIdAndDeletedFalse("user123")
        ).thenReturn(5L);
        when(subscriptionService.canCreateHabit("user123", 5)).thenReturn(
            false
        );

        assertThrows(BadRequestException.class, () ->
            habitService.create("user123", dto)
        );

        verify(habitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update habit successfully")
    void shouldUpdateHabitSuccessfully() {
        HabitUpdateDTO dto = new HabitUpdateDTO(
            "Updated Exercise",
            "New description",
            "Fitness",
            "🏋️",
            "#00FF00",
            true
        );

        when(habitRepository.findById("habit123")).thenReturn(
            Optional.of(testHabit)
        );
        when(habitRepository.save(any(Habit.class))).thenReturn(testHabit);
        when(
            dailyProgressRepository.countCompletedByHabitId(anyString())
        ).thenReturn(10L);

        HabitDTO result = habitService.update("user123", "habit123", dto);

        assertNotNull(result);
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    @DisplayName("Should throw exception when updating habit of another user")
    void shouldThrowExceptionWhenUpdatingOtherUserHabit() {
        testHabit.setUserId("otherUser");
        HabitUpdateDTO dto = new HabitUpdateDTO(
            "Updated",
            null,
            null,
            null,
            null,
            null
        );

        when(habitRepository.findById("habit123")).thenReturn(
            Optional.of(testHabit)
        );

        assertThrows(UnauthorizedException.class, () ->
            habitService.update("user123", "habit123", dto)
        );

        verify(habitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should soft delete habit")
    void shouldSoftDeleteHabit() {
        when(habitRepository.findById("habit123")).thenReturn(
            Optional.of(testHabit)
        );
        when(habitRepository.save(any(Habit.class))).thenReturn(testHabit);

        habitService.delete("user123", "habit123");

        verify(habitRepository).save(argThat(habit -> habit.getDeleted()));
    }

    @Test
    @DisplayName("Should throw exception when habit not found")
    void shouldThrowExceptionWhenHabitNotFound() {
        when(habitRepository.findById("nonexistent")).thenReturn(
            Optional.empty()
        );

        assertThrows(ResourceNotFoundException.class, () ->
            habitService.read("user123", "nonexistent")
        );
    }
}
