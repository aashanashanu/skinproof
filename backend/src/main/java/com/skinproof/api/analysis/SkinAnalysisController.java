package com.skinproof.api.analysis;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@Tag(name = "Skin Analysis", description = "M0 spike endpoint for YouCam SD skin analysis")
public class SkinAnalysisController {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final int MIN_SHORT_SIDE = 480;
    private static final int MAX_LONG_SIDE = 4096;

    private final YouCamSkinAnalysisService skinAnalysis;

    public SkinAnalysisController(YouCamSkinAnalysisService skinAnalysis) {
        this.skinAnalysis = skinAnalysis;
    }

    @PostMapping(path = "/api/analysis/skin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Analyze one consented image",
            description = "Uploads one JPEG/PNG image to the YouCam File API, runs SD skincare analysis, and returns raw/UI scores and mask URLs.")
    public SkinAnalysisResponse analyze(
            @RequestPart("image") MultipartFile image,
            @RequestHeader(name = "X-SkinProof-Consent", defaultValue = "false") String consent,
            @RequestParam(name = "concerns", defaultValue = "ACNE,TEXTURE") List<SkinConcern> concerns) {
        if (!"true".equalsIgnoreCase(consent)) {
            throw new ResponseStatusException(BAD_REQUEST, "Explicit image-analysis consent is required.");
        }
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(BAD_REQUEST, "Image must be non-empty and smaller than 10 MB.");
        }
        if (concerns.isEmpty() || concerns.size() > 16 || concerns.stream().distinct().count() != concerns.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose between 1 and 16 unique skin concerns.");
        }

        String contentType = image.getContentType();
        if (!MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)
                && !MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(BAD_REQUEST, "Only JPEG and PNG images are supported.");
        }

        byte[] imageBytes = readBytes(image);
        validateImageDimensions(imageBytes);
        String extension = MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType) ? ".png" : ".jpg";
        return skinAnalysis.analyze(
                imageBytes,
                "skinproof-" + UUID.randomUUID() + extension,
                contentType,
                concerns);
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Image upload could not be read.");
        }
    }

    private static void validateImageDimensions(byte[] imageBytes) {
        try {
            var bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                throw new ResponseStatusException(BAD_REQUEST, "The uploaded file is not a readable image.");
            }
            int shortSide = Math.min(bufferedImage.getWidth(), bufferedImage.getHeight());
            int longSide = Math.max(bufferedImage.getWidth(), bufferedImage.getHeight());
            if (shortSide < MIN_SHORT_SIDE || longSide > MAX_LONG_SIDE) {
                throw new ResponseStatusException(BAD_REQUEST, "Image dimensions must have a short side of at least 480 px and a long side no greater than 4096 px.");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "The uploaded image could not be decoded.");
        }
    }
}