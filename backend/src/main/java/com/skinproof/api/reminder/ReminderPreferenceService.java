package com.skinproof.api.reminder;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderPreferenceService {
    private final ReminderPreferenceRepository preferences;

    public ReminderPreferenceService(ReminderPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public ReminderPreferenceResponse get(UUID userId) {
        return preferences.findById(userId)
                .map(ReminderPreferenceEntity::toResponse)
                .orElseGet(ReminderPreferenceResponse::defaults);
    }

    @Transactional
    public ReminderPreferenceResponse save(UUID userId, ReminderPreferenceRequest request) {
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid IANA time zone.");
        }
        ReminderPreferenceEntity preference = preferences.findById(userId)
                .orElseGet(() -> new ReminderPreferenceEntity(userId, request));
        preference.update(request, java.time.Instant.now());
        return preferences.save(preference).toResponse();
    }

    @Transactional
    public void delete(UUID userId) {
        preferences.deleteById(userId);
    }
}