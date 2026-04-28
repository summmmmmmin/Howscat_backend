package com.example.howscat.controller;

import com.example.howscat.service.AiHealthSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class AiHealthSummaryController {

    private final AiHealthSummaryService aiHealthSummaryService;

    @GetMapping("/{catId}/ai-summary")
    public ResponseEntity<Map<String, String>> getAiSummary(
            @PathVariable Long catId,
            Authentication authentication
    ) {
        String summary = aiHealthSummaryService.getSummary(catId, authentication);
        return ResponseEntity.ok(Map.of("summary", summary));
    }
}
