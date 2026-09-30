package com.skinproof.api.capture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class CaptureQualityService {
    private static final int MIN_SHORT_SIDE = 480;
    private static final int MAX_LONG_SIDE = 4096;

    public CaptureQualityAssessment assess(byte[] bytes, CaptureQualityRequest cameraQuality) {
        BufferedImage image = decode(bytes);
        int shortSide = Math.min(image.getWidth(), image.getHeight());
        int longSide = Math.max(image.getWidth(), image.getHeight());
        if (shortSide < MIN_SHORT_SIDE) {
            throw new CaptureQualityException("Image is too small for SD skin analysis. Use an image with a short side of at least 480 px.");
        }
        if (longSide > MAX_LONG_SIDE) {
            throw new CaptureQualityException("Image is too large. Resize it so the long side is no more than 4096 px.");
        }

        int brightness = averageBrightness(image);
        if (brightness < 35) {
            throw new CaptureQualityException("Photo is too dark. Move to brighter, even lighting and try again.");
        }
        if (brightness > 245) {
            throw new CaptureQualityException("Photo is overexposed. Move away from direct light and try again.");
        }

        if (cameraQuality == null) {
            return new CaptureQualityAssessment(
                    50,
                    List.of("CAMERA_QUALITY_UNVERIFIED", "FACE_POSE_UNVERIFIED"),
                    false);
        }

        List<String> flags = new ArrayList<>();
        int score = 0;
        if (cameraQuality.hasFace()) score += 25;
        else flags.add("FACE_NOT_DETECTED");
        if ("good".equalsIgnoreCase(cameraQuality.position())) score += 25;
        else flags.add("FACE_POSITION_UNACCEPTABLE");
        if ("good".equalsIgnoreCase(cameraQuality.frontal())) score += 25;
        else flags.add("FACE_POSE_UNACCEPTABLE");
        if ("good".equalsIgnoreCase(cameraQuality.lighting())) score += 25;
        else if ("ok".equalsIgnoreCase(cameraQuality.lighting())) score += 15;
        else flags.add("LIGHTING_UNACCEPTABLE");

        if (!flags.isEmpty()) {
            throw new CaptureQualityException(humanReason(flags.getFirst()));
        }
        return new CaptureQualityAssessment(score, List.copyOf(flags), true);
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new CaptureQualityException("The uploaded file is not a readable image.");
            return image;
        } catch (CaptureQualityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CaptureQualityException("The uploaded image could not be decoded.");
        }
    }

    private static int averageBrightness(BufferedImage image) {
        int sampleX = Math.max(1, image.getWidth() / 64);
        int sampleY = Math.max(1, image.getHeight() / 64);
        long total = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y += sampleY) {
            for (int x = 0; x < image.getWidth(); x += sampleX) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                total += (red * 299L + green * 587L + blue * 114L) / 1000L;
                count++;
            }
        }
        return (int) (total / Math.max(1, count));
    }

    private static String humanReason(String flag) {
        return switch (flag) {
            case "FACE_NOT_DETECTED" -> "No face detected. Center your face in the camera and try again.";
            case "FACE_POSITION_UNACCEPTABLE" -> "Face position is not ready. Move closer to or farther from the camera as indicated.";
            case "FACE_POSE_UNACCEPTABLE" -> "Look straight at the camera and keep your head level.";
            case "LIGHTING_UNACCEPTABLE" -> "Lighting is not suitable. Use bright, even light without harsh shadows.";
            default -> "Photo did not pass the capture quality check.";
        };
    }
}