package com.skinproof.api.capture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.SecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(CaptureController.class)
@Import(SecurityConfiguration.class)
class CaptureBaselineControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaptureAnalysisService captures;

    @MockitoBean
    private CaptureBaselineService baselines;

    @Test
    void marksBaselineForOwningWorkspace() throws Exception {
        UUID captureId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        mockMvc.perform(post("/api/captures/{captureId}/baseline", captureId)
                        .header("X-Demo-Workspace", workspaceId))
                .andExpect(status().isOk());

        verify(baselines).markAsBaseline(captureId, OwnerScope.demo(workspaceId));
        verifyNoInteractions(captures);
    }

    @Test
    void authenticatedOwnerTakesPrecedenceOverWorkspaceHeader() throws Exception {
        UUID captureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/captures/{captureId}/baseline", captureId)
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .header("X-Demo-Workspace", UUID.randomUUID()))
                .andExpect(status().isOk());

        verify(baselines).markAsBaseline(captureId, OwnerScope.user(userId));
    }

    @Test
    void inaccessibleCaptureReturnsNotFound() throws Exception {
        when(baselines.markAsBaseline(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/captures/{captureId}/baseline", UUID.randomUUID())
                        .header("X-Demo-Workspace", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}