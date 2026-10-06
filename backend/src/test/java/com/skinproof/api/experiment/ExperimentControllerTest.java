package com.skinproof.api.experiment;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import com.skinproof.api.auth.SecurityConfiguration;
import com.skinproof.api.demo.DemoWorkspaceService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExperimentController.class)
@Import(SecurityConfiguration.class)
class ExperimentControllerTest {
    private static final String WORKSPACE_HEADER = "X-Demo-Workspace";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExperimentRepository experiments;

    @MockitoBean
    private DemoWorkspaceService experimentData;

    @Test
    void listReturnsOnlyRecordsForRequestedWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(experiments.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OwnerType.DEMO, workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/experiments").header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(experiments).findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OwnerType.DEMO, workspaceId);
    }

    @Test
    void authenticatedListUsesJwtSubjectInsteadOfCallerWorkspaceHeader() throws Exception {
        UUID userSubject = UUID.randomUUID();
        UUID forgedWorkspace = UUID.randomUUID();
        when(experiments.findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OwnerType.USER, userSubject))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/experiments")
                        .header(WORKSPACE_HEADER, forgedWorkspace)
                        .with(jwt().jwt(token -> token.subject(userSubject.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(experiments).findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(OwnerType.USER, userSubject);
    }

    @Test
    void claimDemoDataTransfersOnlyThePresentedLegacyWorkspaceToJwtSubject() throws Exception {
        UUID userSubject = UUID.randomUUID();
        UUID legacyWorkspace = UUID.randomUUID();
        when(experiments.claimDemoWorkspace(legacyWorkspace, userSubject)).thenReturn(2);

        mockMvc.perform(post("/api/experiments/claim-demo-data")
                        .header(WORKSPACE_HEADER, legacyWorkspace)
                        .with(jwt().jwt(token -> token.subject(userSubject.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedExperiments").value(2));

        verify(experiments).claimDemoWorkspace(legacyWorkspace, userSubject);
    }

    @Test
    void createRejectsUnsupportedConcern() throws Exception {
        mockMvc.perform(post("/api/experiments")
                        .header(WORKSPACE_HEADER, UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productName": "Daily serum",
                                  "targetConcern": "WRINKLES",
                                  "startDate": "2026-09-29",
                                  "durationWeeks": 8,
                                  "protocolNotes": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(experiments);
    }

    @Test
    void archiveCannotAccessExperimentOwnedByAnotherWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, OwnerType.DEMO, workspaceId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/experiments/{id}/archive", experimentId)
                        .header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreCannotAccessExperimentOwnedByAnotherWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experimentId, OwnerType.DEMO, workspaceId))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/experiments/{id}/restore", experimentId)
                        .header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreReturnsArchivedExperimentToActive() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        ExperimentEntity experiment = new ExperimentEntity(workspaceId,
                new CreateExperimentRequest("Daily serum", "TEXTURE", LocalDate.of(2026, 9, 29), 8, ""));
        experiment.archive();
        when(experiments.findByIdAndOwnerTypeAndOwnerId(experiment.getId(), OwnerType.DEMO, workspaceId))
                .thenReturn(Optional.of(experiment));
        when(experiments.save(experiment)).thenReturn(experiment);

        mockMvc.perform(post("/api/experiments/{id}/restore", experiment.getId())
                        .header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteUsesAuthenticatedOwnerAndReturnsNoContent() throws Exception {
        UUID userSubject = UUID.randomUUID();
        UUID experimentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/experiments/{id}", experimentId)
                        .header(WORKSPACE_HEADER, UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject(userSubject.toString()))))
                .andExpect(status().isNoContent());

        verify(experimentData).deleteExperiment(experimentId, OwnerScope.user(userSubject));
    }

    @Test
    void createPersistsAnActiveExperimentInTheWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(experiments.save(any(ExperimentEntity.class))).thenAnswer(invocation -> {
            ExperimentEntity experiment = invocation.getArgument(0);
            return experiment;
        });

        mockMvc.perform(post("/api/experiments")
                        .header(WORKSPACE_HEADER, workspaceId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "productName": "Daily serum",
                                  "targetConcern": "TEXTURE",
                                  "startDate": "2026-09-29",
                                  "durationWeeks": 8,
                                  "protocolNotes": "Consistent morning capture"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Daily serum"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.workspaceId").doesNotExist());

        verify(experiments).save(any(ExperimentEntity.class));
    }
}