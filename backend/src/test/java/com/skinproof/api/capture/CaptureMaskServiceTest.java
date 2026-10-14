package com.skinproof.api.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skinproof.api.analysis.SkinAnalysisScore;
import com.skinproof.api.auth.OwnerScope;
import com.skinproof.api.experiment.CreateExperimentRequest;
import com.skinproof.api.experiment.ExperimentEntity;
import java.time.LocalDate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CaptureMaskServiceTest {
    private final CaptureRepository captures = mock(CaptureRepository.class);
    private final PrivateImageStore images = mock(PrivateImageStore.class);
    private final CaptureMaskService service = spy(new CaptureMaskService(captures, images));
    private final OwnerScope owner = OwnerScope.user(UUID.randomUUID());
    private final SkinAnalysisScore score = new SkinAnalysisScore("texture", 60.0, 65,
            List.of("https://provider.example/mask.png?signature=expired"));

    @Test
    void downloadsSignedUrlWithoutChangingEncodingAndRetainsValidPng() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        BufferedImage mask = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        mask.setRGB(0, 0, 0xffe06070);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(mask, "png", encoded);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(encoded.toByteArray()));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        CaptureMaskService downloader = new CaptureMaskService(captures, images, http);
        String url = "https://provider.example/mask.png?X-Amz-Credential=KEY%2Fdate%2Fs3&X-Amz-Signature=abc";

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(downloader.download(url)));

        assertEquals(2, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
        assertEquals(mask.getRGB(0, 0), decoded.getRGB(0, 0));
        org.mockito.ArgumentCaptor<HttpRequest> request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
        assertEquals(URI.create(url), request.getValue().uri());
    }

    @Test
    void rejectsNonImageProviderResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("<html>expired</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);

        CaptureMaskService downloader = new CaptureMaskService(captures, images, http);

        assertThrows(IllegalStateException.class, () -> downloader.download("https://provider.example/mask.png"));
        verifyNoInteractions(images);
    }

    @Test
    void captureResponseUsesAuthenticatedMaskPathInsteadOfTemporaryProviderUrl() throws Exception {
        CaptureEntity capture = capture();

        CaptureResponse response = CaptureResponse.from(capture);

        assertEquals(List.of("/api/captures/" + capture.getId() + "/masks/texture/0"),
                response.scores().getFirst().maskUrls());
        assertEquals(60.0, response.scores().getFirst().rawScore());
    }

    @Test
    void rejectsNonHttpsProviderMaskWithoutNetworkAccess() {
        assertThrows(IllegalStateException.class, () -> service.download("http://provider.example/mask.png"));
        verifyNoInteractions(images);
    }

    @Test
    void storedMaskRendersEvenAfterProviderUrlExpires() throws Exception {
        CaptureEntity capture = capture();
        byte[] png = new byte[] {1, 2, 3};
        when(images.get(CaptureMaskService.key(capture.getAnalysis().getId(), "texture", 0))).thenReturn(png);

        assertArrayEquals(png, service.read(capture.getId(), "texture", 0, owner));

        verify(service, never()).download(any());
    }

    @Test
    void legacyMaskIsRetainedOnFirstRead() throws Exception {
        CaptureEntity capture = capture();
        String key = CaptureMaskService.key(capture.getAnalysis().getId(), "texture", 0);
        byte[] png = new byte[] {1, 2, 3};
        when(images.get(key)).thenThrow(new PrivateImageStore.ImageStoreException("Missing"));
        doReturn(png).when(service).download(score.maskUrls().getFirst());

        assertArrayEquals(png, service.read(capture.getId(), "texture", 0, owner));

        verify(images).put(key, png, "image/png");
    }

    @Test
    void expiredLegacyMaskReturnsGoneWithoutReanalyzing() throws Exception {
        CaptureEntity capture = capture();
        when(images.get(any())).thenThrow(new PrivateImageStore.ImageStoreException("Missing"));
        doThrow(new IllegalStateException("Expired")).when(service).download(any());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.read(capture.getId(), "texture", 0, owner));

        assertEquals(HttpStatus.GONE, exception.getStatusCode());
        verify(images, never()).put(any(), any(), any());
    }

    @Test
    void anotherOwnerCannotReadMask() {
        UUID captureId = UUID.randomUUID();
        when(captures.findByIdAndOwnerTypeAndOwnerId(captureId, owner.type(), owner.id())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.read(captureId, "texture", 0, owner));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(images);
    }

    @Test
    void outOfRangeMaskCannotReadStorage() throws Exception {
        CaptureEntity capture = capture();

        assertThrows(ResponseStatusException.class, () -> service.read(capture.getId(), "texture", 1, owner));

        verifyNoInteractions(images);
    }

    @Test
    void failedMaskArchiveDoesNotThrowAndCleanupKeysAreDeterministic() throws Exception {
        CaptureEntity capture = capture();
        doThrow(new IllegalStateException("Expired")).when(service).download(any());

        service.archive(capture.getAnalysis().getId(), List.of(score));

        assertEquals(List.of(CaptureMaskService.key(capture.getAnalysis().getId(), "texture", 0)),
                CaptureMaskService.storageKeys(capture.getAnalysis()));
        verify(images, never()).put(any(), any(), any());
    }

    private CaptureEntity capture() throws Exception {
        ExperimentEntity experiment = new ExperimentEntity(owner.id(), new CreateExperimentRequest(
                "Serum", "TEXTURE", LocalDate.now(), 8, ""));
        AnalysisEntity analysis = new AnalysisEntity("cache-key", "texture", 9);
        analysis.succeed("task", new ObjectMapper().writeValueAsString(List.of(score)));
        CaptureEntity capture = new CaptureEntity(experiment, analysis, "private-image", "hash", "image/png",
                100, "[]", true, true);
        when(captures.findByIdAndOwnerTypeAndOwnerId(capture.getId(), owner.type(), owner.id()))
                .thenReturn(Optional.of(capture));
        return capture;
    }
}