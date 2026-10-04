package com.skinproof.api.checkin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckInEntity, UUID> {
    List<CheckInEntity> findAllByExperiment_IdOrderByCheckInDateAsc(UUID experimentId);

    Optional<CheckInEntity> findByExperiment_IdAndCheckInDate(UUID experimentId, LocalDate checkInDate);
}
