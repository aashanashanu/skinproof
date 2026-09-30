package com.skinproof.api.capture;

import com.skinproof.api.analysis.SkinConcern;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Captures", description = "Private capture upload and asynchronous skin analysis status")
public class CaptureController {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final CaptureAnalysisService captures;

    public CaptureController(CaptureAnalysisService captures) {
        this.captures = captures;
    }

    @PostMapping(path = "/api/experiments/{experimentId}/captures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a private capture and queue YouCam analysis")
    public CaptureResponse create(
            @PathVariable UUID experimentId,
            @RequestHeader("X-Demo-Workspace") UUID workspaceId,
            @RequestHeader(name = "X-SkinProof-Consent", defaultValue = "false") String consent,
            @RequestPart("image") MultipartFile image,
            @RequestPart(name = "quality", required = false) CaptureQualityRequest quality,
            @RequestParam(name = "concerns") @NotEmpty List<SkinConcern> concerns,
            @RequestParam(name = "baseline", defaultValue = "false") boolean baseline) {
        if (!"true".equalsIgnoreCase(consent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit image-analysis consent is required.");
        }
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be non-empty and smaller than 10 MB.");
        }
        if (concerns.isEmpty() || concerns.size() > 16 || concerns.stream().distinct().count() != concerns.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 1 and 16 unique concerns.");
        }
        String contentType = image.getContentType();
        if (!MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)
                && !MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG and PNG images are supported.");
        }
        try {
            return captures.createCapture(
                    experimentId,
                    workspaceId,
                    image.getBytes(),
                    contentType,
                    quality,
                    baseline,
                    concerns);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image upload could not be read.");
        } catch (CaptureQualityException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        }
    }

    @GetMapping("/api/captures/{captureId}")
    @Operation(summary = "Get capture quality and asynchronous analysis status")
    public CaptureResponse get(
            @PathVariable UUID captureId,
            @RequestHeader("X-Demo-Workspace") UUID workspaceId) {
        return captures.getCapture(captureId, workspaceId);
    }

    @PostMapping("/api/captures/{captureId}/retry")
    @Operation(summary = "Retry a failed analysis for a capture")
    public CaptureResponse retry(
            @PathVariable UUID captureId,
            @RequestHeader("X-Demo-Workspace") UUID workspaceId) {
        return captures.retryAnalysis(captureId, workspaceId);
    }
}