package com.skinproof.api.auth;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skinproof.api.demo.DemoWorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(SecurityConfiguration.class)
class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoWorkspaceService data;

    @Test
    void profileReturnsAuthenticatedSubject() throws Exception {
        UUID subject = UUID.randomUUID();

        mockMvc.perform(get("/api/account/me")
                        .with(jwt().jwt(token -> token.subject(subject.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(subject.toString()));
    }

    @Test
    void deleteUsesJwtSubjectAsOwner() throws Exception {
        UUID subject = UUID.randomUUID();

        mockMvc.perform(delete("/api/account/data")
                        .header("X-Demo-Workspace", UUID.randomUUID())
                        .with(jwt().jwt(token -> token.subject(subject.toString()))))
                .andExpect(status().isNoContent());

        verify(data).reset(OwnerScope.user(subject));
    }
}
