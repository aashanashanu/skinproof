package com.skinproof.api.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExperimentController.class)
class ExperimentControllerTest {
    private static final String WORKSPACE_HEADER = "X-Demo-Workspace";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExperimentRepository experiments;

    @Test
    void listReturnsOnlyRecordsForRequestedWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(experiments.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/experiments").header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(experiments).findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
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
        when(experiments.findByIdAndWorkspaceId(experimentId, workspaceId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/experiments/{id}/archive", experimentId)
                        .header(WORKSPACE_HEADER, workspaceId))
                .andExpect(status().isNotFound());
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