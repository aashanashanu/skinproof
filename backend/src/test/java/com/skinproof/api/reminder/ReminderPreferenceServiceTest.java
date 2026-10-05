package com.skinproof.api.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ReminderPreferenceServiceTest {
    private final ReminderPreferenceRepository repository = org.mockito.Mockito.mock(ReminderPreferenceRepository.class);
    private final ReminderPreferenceService service = new ReminderPreferenceService(repository);

    @Test
    void defaultsAreDisabledWithoutPersistingOrSending() {
        UUID userId = UUID.randomUUID();

        ReminderPreferenceResponse response = service.get(userId);

        assertFalse(response.enabled());
        assertEquals("America/Chicago", response.timezone());
        verify(repository, never()).save(any());
    }

    @Test
    void optInAndUnsubscribeUpdatePreferenceState() {
        UUID userId = UUID.randomUUID();
        ReminderPreferenceRequest enabled = new ReminderPreferenceRequest(
                true, "America/Chicago", 1, LocalTime.of(9, 0), ReminderCadence.WEEKLY);
        ReminderPreferenceEntity stored = new ReminderPreferenceEntity(userId, new ReminderPreferenceRequest(
                false, "America/Chicago", 1, LocalTime.of(9, 0), ReminderCadence.WEEKLY));
        when(repository.findById(userId)).thenReturn(Optional.of(stored));
        when(repository.save(any(ReminderPreferenceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.save(userId, enabled).enabled());
        assertTrue(stored.getOptedInAt() != null);

        ReminderPreferenceRequest unsubscribed = new ReminderPreferenceRequest(
                false, "America/Chicago", 1, LocalTime.of(9, 0), ReminderCadence.WEEKLY);
        assertFalse(service.save(userId, unsubscribed).enabled());
        assertTrue(stored.getUnsubscribedAt().isAfter(Instant.EPOCH));
    }

    @Test
    void rejectsInvalidTimezoneBeforePersistence() {
        UUID userId = UUID.randomUUID();
        ReminderPreferenceRequest invalid = new ReminderPreferenceRequest(
                true, "Not/A_Zone", 1, LocalTime.of(9, 0), ReminderCadence.WEEKLY);

        assertThrows(ResponseStatusException.class, () -> service.save(userId, invalid));
        verify(repository, never()).save(any());
    }
}