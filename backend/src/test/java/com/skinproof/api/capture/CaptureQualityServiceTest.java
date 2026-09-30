package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CaptureQualityServiceTest {
    private final CaptureQualityService quality = new CaptureQualityService();

    @Test
    void guidedCaptureStoresFullScoreWhenCameraChecksPass() throws Exception {
        CaptureQualityAssessment result = quality.assess(
                image(Color.GRAY),
                new CaptureQualityRequest(true, "good", "good", "good"));

        assertEquals(100, result.score());
        assertTrue(result.valid());
        assertTrue(result.flags().isEmpty());
    }

    @Test
    void guidedCaptureRejectsBadLightingWithActionableReason() throws Exception {
        CaptureQualityException error = assertThrows(CaptureQualityException.class, () -> quality.assess(
                image(Color.GRAY),
                new CaptureQualityRequest(true, "good", "good", "notgood")));

        assertTrue(error.getMessage().contains("bright, even light"));
    }

    @Test
    void manualUploadIsKeptButMarkedUnverified() throws Exception {
        CaptureQualityAssessment result = quality.assess(image(Color.GRAY), null);

        assertEquals(50, result.score());
        assertFalse(result.valid());
        assertTrue(result.flags().contains("CAMERA_QUALITY_UNVERIFIED"));
    }

    @Test
    void rejectsDarkPhotoWithActionableReason() throws Exception {
        CaptureQualityException error = assertThrows(CaptureQualityException.class, () -> quality.assess(
                image(Color.BLACK), null));

        assertTrue(error.getMessage().contains("too dark"));
    }

    private static byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(480, 480, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 480, 480);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}