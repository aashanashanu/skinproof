package com.skinproof.api.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YouCamSkinAnalysisServiceTest {
    @Test
    void includesSanitizedProviderErrorForBadFileRequest() {
  RestClient.Builder builder = RestClient.builder();
  MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  YouCamSkinAnalysisService service = new YouCamSkinAnalysisService(
    builder,
    new SkinAnalysisResultParser(),
    "test-api-key",
    Duration.ofMillis(1),
    Duration.ofSeconds(5));
  server.expect(requestTo("https://yce-api-01.makeupar.com/s2s/v2.0/file"))
    .andExpect(method(POST))
    .andRespond(withBadRequest().body("""
      {"status":400,"error":"Invalid file metadata","error_code":"BadRequest"}
      """).contentType(MediaType.APPLICATION_JSON));

  YouCamApiException exception = assertThrows(
    YouCamApiException.class,
    () -> service.analyze(new byte[] {1}, "sample.png", "image/png", List.of(SkinConcern.ACNE)));

  assertEquals("YouCam API returned HTTP 400: [BadRequest] Invalid file metadata", exception.getMessage());
  assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getHttpStatus());
  server.verify();
    }

    @Test
    void uploadsCreatesTaskPollsAndReturnsScores() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YouCamSkinAnalysisService service = new YouCamSkinAnalysisService(
                builder,
                new SkinAnalysisResultParser(),
                "test-api-key",
                Duration.ofMillis(1),
                Duration.ofSeconds(5));
        byte[] imageBytes = new byte[] { 1, 2, 3 };

        server.expect(requestTo("https://yce-api-01.makeupar.com/s2s/v2.0/file"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.files[0].content_type").value("image/jpeg"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "files": [{
                              "file_id": "file-id",
                              "requests": [{
                                "method": "PUT",
                                "url": "https://upload.example.test/file-id",
                                "headers": {"X-Upload-Token": "signed-token"}
                              }]
                            }]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://upload.example.test/file-id"))
                .andExpect(method(PUT))
                .andExpect(header("X-Upload-Token", "signed-token"))
                .andExpect(content().bytes(imageBytes))
                .andRespond(withSuccess());

        server.expect(requestTo("https://yce-api-01.makeupar.com/s2s/v2.0/task/skin-analysis"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.src_file_id").value("file-id"))
                .andExpect(jsonPath("$.dst_actions[0]").value("acne"))
                .andRespond(withSuccess("""
                        {"data": {"task_id": "task-id"}}
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://yce-api-01.makeupar.com/s2s/v2.0/task/skin-analysis/task-id"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "task_status": "success",
                            "results": {
                              "output": [{
                                "type": "acne",
                                "raw_score": 72.5,
                                "ui_score": 78,
                                "mask_urls": ["https://mask.example.test/acne.png"]
                              }]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        SkinAnalysisResponse response = service.analyze(
                imageBytes,
                "sample.jpg",
                "image/jpeg",
                List.of(SkinConcern.ACNE));

        assertEquals("task-id", response.taskId());
        assertEquals("SD", response.mode());
        assertEquals(9, response.documentedUnitEstimate());
        assertEquals("acne", response.scores().getFirst().concern());
        assertEquals(72.5, response.scores().getFirst().rawScore());
        assertEquals(78, response.scores().getFirst().uiScore());
        server.verify();
    }
}