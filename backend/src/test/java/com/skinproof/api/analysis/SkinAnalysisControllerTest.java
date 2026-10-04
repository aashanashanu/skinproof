package com.skinproof.api.analysis;

import com.skinproof.api.auth.SecurityConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkinAnalysisController.class)
@Import(SecurityConfiguration.class)
class SkinAnalysisControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YouCamSkinAnalysisService skinAnalysis;

    @Test
    void rejectsAnalysisWithoutExplicitConsent() throws Exception {
        mockMvc.perform(multipart("/api/analysis/skin")
                        .file(sampleImage())
                        .param("concerns", "ACNE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(skinAnalysis);
    }

    @Test
    void acceptsConsentedImageAndReturnsRawScore() throws Exception {
        when(skinAnalysis.analyze(
                any(byte[].class),
                anyString(),
                eq("image/png"),
                eq(List.of(SkinConcern.ACNE))))
                .thenReturn(new SkinAnalysisResponse(
                        "task-1",
                        "SD",
                        9,
                        List.of(new SkinAnalysisScore("acne", 72.5, 78, List.of()))));

        mockMvc.perform(multipart("/api/analysis/skin")
                        .file(sampleImage())
                        .header("X-SkinProof-Consent", "true")
            .header("X-Demo-Workspace", java.util.UUID.randomUUID())
                        .param("concerns", "ACNE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SD"))
                .andExpect(jsonPath("$.documentedUnitEstimate").value(9))
                .andExpect(jsonPath("$.scores[0].concern").value("acne"))
                .andExpect(jsonPath("$.scores[0].rawScore").value(72.5));

        verify(skinAnalysis).analyze(any(byte[].class), anyString(), eq("image/png"), eq(List.of(SkinConcern.ACNE)));
    }

    private static MockMultipartFile sampleImage() throws Exception {
        BufferedImage image = new BufferedImage(480, 480, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "synthetic-test.png", "image/png", bytes.toByteArray());
    }
}