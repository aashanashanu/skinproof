package com.skinproof.api.capture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.auth.OwnerType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaptureMaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureMaskService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MASK_BYTES = 10 * 1024 * 1024;
    private final CaptureRepository captures;
    private final PrivateImageStore images;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public CaptureMaskService(CaptureRepository captures, PrivateImageStore images) {
        this(captures, images, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    CaptureMaskService(CaptureRepository captures, PrivateImageStore images, HttpClient http) {
        this.captures = captures;
        this.images = images;
        this.http = http;
    }

    public void archive(UUID analysisId, List<SkinAnalysisScore> scores) {
        for (SkinAnalysisScore score : scores) {
            for (int index = 0; index < score.maskUrls().size(); index++) {
                try {
                    retain(analysisId, score, index);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not retain detection mask {} for analysis {}.", index, analysisId);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public byte[] read(UUID captureId, String concern, int index, OwnerScope owner) {
        CaptureEntity capture = (owner.type() == OwnerType.DEMO
                ? captures.findByIdAndWorkspaceId(captureId, owner.id())
                : captures.findByIdAndOwnerTypeAndOwnerId(captureId, owner.type(), owner.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SkinAnalysisScore score = scores(capture.getAnalysis()).stream()
                .filter(item -> item.concern().equals(concern))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (index < 0 || index >= score.maskUrls().size() || capture.isSimulated()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            return images.get(key(capture.getAnalysis().getId(), concern, index));
        } catch (PrivateImageStore.ImageStoreException missing) {
            try {
                return retain(capture.getAnalysis().getId(), score, index);
            } catch (RuntimeException unavailable) {
                throw new ResponseStatusException(HttpStatus.GONE, "Detection mask is no longer available.");
            }
        }
    }

    private byte[] retain(UUID analysisId, SkinAnalysisScore score, int index) {
        byte[] content = download(score.maskUrls().get(index));
        images.put(key(analysisId, score.concern(), index), content, "image/png");
        return content;
    }

    byte[] download(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid provider mask URL.");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("Provider mask is unavailable.");
                byte[] content = body.readNBytes(MAX_MASK_BYTES + 1);
                if (content.length > MAX_MASK_BYTES) throw new IllegalStateException("Provider mask exceeds size limit.");
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IllegalStateException("Provider mask is not an image.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_777_216L) {
                            throw new IllegalStateException("Provider mask dimensions exceed limit.");
                        }
                        BufferedImage image = reader.read(0);
                        ByteArrayOutputStream png = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", png);
                        return png.toByteArray();
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mask download interrupted.", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to download provider mask.", exception);
        }
    }

    static String key(UUID analysisId, String concern, int index) {
        return "analyses/" + analysisId + "/masks/" + concern + "/" + index + ".png";
    }

    public static List<String> storageKeys(AnalysisEntity analysis) {
        List<String> keys = new ArrayList<>();
        for (SkinAnalysisScore score : scores(analysis)) {
            for (int index = 0; index < score.maskUrls().size(); index++) {
                keys.add(key(analysis.getId(), score.concern(), index));
            }
        }
        return List.copyOf(keys);
    }

    private static List<SkinAnalysisScore> scores(AnalysisEntity analysis) {
        try {
            return JSON.readValue(analysis.getConcernScoresJson(), new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }
}