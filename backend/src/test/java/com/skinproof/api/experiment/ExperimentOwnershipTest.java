package com.skinproof.api.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperimentOwnershipTest {
    @Test
    void transfersDemoWorkspaceOwnershipToAuthenticatedSubjectOnce() {
        UUID workspaceId = UUID.randomUUID();
        UUID userSubject = UUID.randomUUID();
        ExperimentEntity experiment = new ExperimentEntity(workspaceId,
                new CreateExperimentRequest("Barrier serum", "TEXTURE", LocalDate.now(), 8, ""));

        experiment.transferDemoDataTo(OwnerScope.user(userSubject));

        assertEquals(OwnerType.USER, experiment.getOwnerType());
        assertEquals(userSubject, experiment.getOwnerId());
        assertNull(experiment.getWorkspaceId());
        assertThrows(IllegalStateException.class, () -> experiment.transferDemoDataTo(OwnerScope.user(UUID.randomUUID())));
    }
}
