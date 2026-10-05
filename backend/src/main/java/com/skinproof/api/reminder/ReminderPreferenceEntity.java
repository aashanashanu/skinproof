package com.skinproof.api.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "reminder_preferences")
public class ReminderPreferenceEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "timezone_id", nullable = false, length = 64)
    private String timezone;

    @Column(name = "preferred_weekday", nullable = false)
    private int weekday;

    @Column(name = "preferred_time", nullable = false)
    private LocalTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReminderCadence cadence;

    @Column(name = "opted_in_at")
    private Instant optedInAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderPreferenceEntity() {
    }

    public ReminderPreferenceEntity(UUID userId, ReminderPreferenceRequest request) {
        this.userId = userId;
        update(request, Instant.now());
    }

    public void update(ReminderPreferenceRequest request, Instant now) {
        boolean wasEnabled = enabled;
        enabled = request.enabled();
        timezone = request.timezone();
        weekday = request.weekday();
        preferredTime = request.preferredTime();
        cadence = request.cadence();
        if (enabled) {
            if (!wasEnabled) optedInAt = now;
            unsubscribedAt = null;
        } else if (wasEnabled) {
            unsubscribedAt = now;
        }
        updatedAt = now;
    }

    public ReminderPreferenceResponse toResponse() {
        return new ReminderPreferenceResponse(enabled, timezone, weekday, preferredTime, cadence);
    }

    Instant getOptedInAt() { return optedInAt; }
    Instant getUnsubscribedAt() { return unsubscribedAt; }
}