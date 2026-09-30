package com.skinproof.api.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinAnalysisResultParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkinAnalysisResultParser parser = new SkinAnalysisResultParser();

    @Test
    void parsesRawUiScoresAndMaskUrlsFromSuccessfulTask() throws Exception {
        var response = objectMapper.readTree("""
                {
                  "data": {
                    "task_status": "success",
                    "results": {
                      "output": [
                        {
                          "type": "texture",
                          "raw_score": 57.33,
                          "ui_score": 68,
                          "mask_urls": ["https://mask.example/texture.png"]
                        }
                      ]
                    }
                  }
                }
                """);

        List<SkinAnalysisScore> scores = parser.parse(response);

        assertEquals(1, scores.size());
        assertEquals("texture", scores.getFirst().concern());
        assertEquals(57.33, scores.getFirst().rawScore());
        assertEquals(68, scores.getFirst().uiScore());
        assertEquals(List.of("https://mask.example/texture.png"), scores.getFirst().maskUrls());
    }

    @Test
    void rejectsMalformedTaskOutput() throws Exception {
        var response = objectMapper.readTree("""
                {"data": {"task_status": "success", "results": {"output": []}}}
                """);

        assertThrows(YouCamApiException.class, () -> parser.parse(response));
    }

      @Test
      void exposesEveryDocumentedSdAction() {
        assertEquals(16, SkinConcern.values().length);
        assertEquals(List.of(
            "wrinkle",
            "droopy_upper_eyelid",
            "droopy_lower_eyelid",
            "firmness",
            "acne",
            "moisture",
            "eye_bag",
            "dark_circle_v2",
            "age_spot",
            "radiance",
            "redness",
            "oiliness",
            "pore",
            "texture",
            "tear_trough",
            "skin_type"),
            java.util.Arrays.stream(SkinConcern.values())
                .map(SkinConcern::action)
                .toList());
      }
}