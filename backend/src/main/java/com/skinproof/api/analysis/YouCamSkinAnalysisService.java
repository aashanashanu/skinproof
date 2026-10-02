package com.skinproof.api.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class YouCamSkinAnalysisService {
    private static final String API_BASE_URL = "https://yce-api-01.makeupar.com";
    private static final ObjectMapper ERROR_BODY_PARSER = new ObjectMapper();

    private final RestClient apiClient;
    private final RestClient uploadClient;
    private final SkinAnalysisResultParser resultParser;
    private final String apiKey;
    private final Duration pollInterval;
    private final Duration taskTimeout;

    public YouCamSkinAnalysisService(
            RestClient.Builder restClientBuilder,
            SkinAnalysisResultParser resultParser,
            @Value("${youcam.api-key:}") String apiKey,
            @Value("${youcam.poll-interval:PT3S}") Duration pollInterval,
            @Value("${youcam.timeout:PT2M}") Duration taskTimeout) {
        this.apiClient = restClientBuilder.clone().baseUrl(API_BASE_URL).build();
        this.uploadClient = restClientBuilder.clone().build();
        this.resultParser = resultParser;
        this.apiKey = apiKey;
        this.pollInterval = pollInterval;
        this.taskTimeout = taskTimeout;
    }

    public SkinAnalysisResponse analyze(
            byte[] imageBytes,
            String fileName,
            String contentType,
            List<SkinConcern> concerns) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "YouCam API key is not configured.");
        }

        try {
            String fileId = registerImage(imageBytes, fileName, contentType);
            String taskId = createAnalysisTask(fileId, concerns);
            JsonNode completedTask = pollTask(taskId);
            List<SkinAnalysisScore> scores = resultParser.parse(completedTask);
            return new SkinAnalysisResponse(taskId, "SD", documentedUnits(concerns.size()), scores);
        } catch (RestClientResponseException exception) {
            HttpStatus responseStatus = exception.getStatusCode().value() == 400
                    ? HttpStatus.UNPROCESSABLE_ENTITY
                    : HttpStatus.BAD_GATEWAY;
            throw new YouCamApiException(
                "YouCam API returned HTTP " + exception.getStatusCode().value()
                    + formatProviderError(exception.getResponseBodyAsString()),
                responseStatus,
                exception);
        }
    }

    private String registerImage(byte[] imageBytes, String fileName, String contentType) {
        Map<String, Object> fileMetadata = Map.of(
                "content_type", contentType,
                "file_name", fileName,
                "file_size", imageBytes.length);
        JsonNode response = apiClient.post()
                .uri("/s2s/v2.0/file")
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("files", List.of(fileMetadata)))
                .retrieve()
                .body(JsonNode.class);

        JsonNode file = response == null ? null : response.path("data").path("files").path(0);
        String fileId = textAt(file, "file_id");
        JsonNode upload = file == null ? null : file.path("requests").path(0);
        String uploadUrl = textAt(upload, "url");
        if (fileId.isBlank() || uploadUrl.isBlank()) {
            throw new YouCamApiException("YouCam File API response was missing the file ID or upload URL.");
        }

        String method = textAt(upload, "method");
        if (method.isBlank()) {
            method = HttpMethod.PUT.name();
        }
        JsonNode signedHeaders = upload.path("headers");
        // URI.create keeps the presigned query string opaque; re-templating it would corrupt the SigV4 signature.
        uploadClient.method(HttpMethod.valueOf(method))
                .uri(URI.create(uploadUrl))
                .headers(headers -> copyHeaders(signedHeaders, headers))
                .body(imageBytes)
                .retrieve()
                .toBodilessEntity();
        return fileId;
    }

    private String createAnalysisTask(String fileId, List<SkinConcern> concerns) {
        List<String> actions = concerns.stream().map(SkinConcern::action).toList();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("src_file_id", fileId);
        request.put("dst_actions", actions);
        request.put("format", "json");

        JsonNode response = apiClient.post()
                .uri("/s2s/v2.0/task/skin-analysis")
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        String taskId = response == null ? "" : textAt(response.path("data"), "task_id");
        if (taskId.isBlank()) {
            throw new YouCamApiException("YouCam task response was missing the task ID.");
        }
        return taskId;
    }

    private JsonNode pollTask(String taskId) {
        Instant deadline = Instant.now().plus(taskTimeout);
        while (Instant.now().isBefore(deadline)) {
            JsonNode response = apiClient.get()
                    .uri("/s2s/v2.0/task/skin-analysis/{taskId}", taskId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = response == null ? null : response.path("data");
            String status = data == null ? "" : textAt(data, "task_status");

            if ("success".equalsIgnoreCase(status)) {
                return response;
            }
            if ("error".equalsIgnoreCase(status)) {
                throw new YouCamApiException(
                    "YouCam reported that the skin analysis task failed" + formatProviderError(data),
                    HttpStatus.UNPROCESSABLE_ENTITY);
            }
            waitBeforeNextPoll();
        }
        throw new YouCamApiException("YouCam skin analysis did not finish before the configured timeout.");
    }

    private void waitBeforeNextPoll() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new YouCamApiException("YouCam analysis polling was interrupted.", exception);
        }
    }

    private String bearerToken() {
        return "Bearer " + apiKey;
    }

    private static String textAt(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private static String formatProviderError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            String formatted = formatProviderError(ERROR_BODY_PARSER.readTree(responseBody));
            if (!formatted.isBlank()) {
                return formatted;
            }
        } catch (Exception ignored) {
            // Fall through to the raw-body fallback below.
        }
        String sanitized = responseBody.replaceAll("[\\r\\n\\t]", " ").trim();
        if (sanitized.length() > 240) {
            sanitized = sanitized.substring(0, 240);
        }
        return sanitized.isBlank() ? "" : ": " + sanitized;
    }

    private static String formatProviderError(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return "";
        }
        String code = textAt(response, "error_code");
        String message = textAt(response, "error_message");
        if (message.isBlank()) {
            message = textAt(response, "error");
        }
        if (message.isBlank()) {
            message = textAt(response, "message");
        }
        if (message.length() > 240) {
            message = message.substring(0, 240);
        }
        message = message.replaceAll("[\\r\\n\\t]", " ");
        if (code.isBlank() && message.isBlank()) {
            return "";
        }
        return ": " + (code.isBlank() ? "" : "[" + code + "] ") + message;
    }

    private static void copyHeaders(JsonNode source, HttpHeaders destination) {
        if (source != null && source.isObject()) {
            source.fields().forEachRemaining(entry -> destination.set(entry.getKey(), entry.getValue().asText()));
        }
    }

    private static int documentedUnits(int concernCount) {
        if (concernCount <= 4) {
            return 9;
        }
        if (concernCount <= 8) {
            return 12;
        }
        if (concernCount <= 12) {
            return 14;
        }
        return 16;
    }
}