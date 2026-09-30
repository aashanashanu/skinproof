package com.skinproof.api.verdict;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VerdictController.class)
class VerdictControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerdictService verdicts;

    @Test
    void getReturnsWorkspaceScopedVerdict() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(verdicts.getLatest(experimentId, workspaceId)).thenReturn(response());

        mockMvc.perform(get("/api/experiments/{experimentId}/verdict", experimentId)
                        .header("X-Demo-Workspace", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("INCONCLUSIVE"))
                .andExpect(jsonPath("$.noiseFloor").value(3.0));

        verify(verdicts).getLatest(experimentId, workspaceId);
    }

    @Test
    void postRecomputesWithExplicitConfounds() throws Exception {
        UUID experimentId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        List<ConfoundFlag> confounds = List.of(new ConfoundFlag(ConfoundCode.NEW_PRODUCT));
        when(verdicts.recompute(experimentId, workspaceId, confounds)).thenReturn(response(confounds));

        mockMvc.perform(post("/api/experiments/{experimentId}/verdict", experimentId)
                        .header("X-Demo-Workspace", workspaceId)
                        .contentType("application/json")
                        .content("""
                                {"confounds":["NEW_PRODUCT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confounds[0].code").value("NEW_PRODUCT"));

        verify(verdicts).recompute(experimentId, workspaceId,
                List.of(new ConfoundFlag(ConfoundCode.NEW_PRODUCT)));
    }

    private static VerdictResponse response() {
                return response(List.of());
        }

        private static VerdictResponse response(List<ConfoundFlag> confounds) {
        return new VerdictResponse(UUID.randomUUID(), "acne", VerdictLabel.INCONCLUSIVE, ConfidenceLevel.LOW,
                null, null, null, 3.0, 0, 0, 0, 0, false,
                                "Not enough data yet.", confounds, Instant.parse("2026-09-30T00:00:00Z"));
    }
}