package com.skinproof.api.reminder;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderPreferenceRepository extends JpaRepository<ReminderPreferenceEntity, UUID> {
}