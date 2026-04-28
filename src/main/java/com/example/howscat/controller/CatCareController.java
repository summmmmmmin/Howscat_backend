package com.example.howscat.controller;

import com.example.howscat.dto.CareWeightRequest;
import com.example.howscat.dto.ObesityCheckRequest;
import com.example.howscat.dto.ObesityCheckResponse;
import com.example.howscat.dto.ObesityHistoryItem;
import com.example.howscat.dto.WeightHistoryItem;
import com.example.howscat.dto.VomitAnalysisRequest;
import com.example.howscat.dto.VomitAnalysisResponse;
import com.example.howscat.service.ObesityCheckService;
import com.example.howscat.service.VomitAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class CatCareController {

    private final ObesityCheckService obesityCheckService;
    private final VomitAnalysisService vomitAnalysisService;

    @PostMapping("/{catId}/care-weight")
    public ResponseEntity<Void> recordCareWeight(
            @PathVariable Long catId,
            @RequestBody @Valid CareWeightRequest request,
            Authentication authentication
    ) {
        obesityCheckService.recordCareWeight(catId, request, authentication);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{catId}/obesity-check")
    public ResponseEntity<ObesityCheckResponse> obesityCheck(
            @PathVariable Long catId,
            @RequestBody @Valid ObesityCheckRequest request,
            Authentication authentication
    ) {
        ObesityCheckResponse response = obesityCheckService.analyzeAndSave(catId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{catId}/vomit")
    public ResponseEntity<VomitAnalysisResponse> vomit(
            @PathVariable Long catId,
            @RequestBody VomitAnalysisRequest request,
            Authentication authentication
    ) {
        VomitAnalysisResponse response = vomitAnalysisService.analyzeAndSave(catId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{catId}/vomit/{vomitId}")
    public ResponseEntity<Void> deleteVomit(
            @PathVariable Long catId,
            @PathVariable Long vomitId,
            Authentication authentication
    ) {
        vomitAnalysisService.deleteVomitRecord(catId, vomitId, authentication);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{catId}/weight-history")
    public ResponseEntity<List<WeightHistoryItem>> weightHistory(
            @PathVariable Long catId,
            @RequestParam(required = false, defaultValue = "7") Integer limit,
            Authentication authentication
    ) {
        List<WeightHistoryItem> items = obesityCheckService.listWeightHistory(catId, limit, authentication);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{catId}/obesity-history")
    public ResponseEntity<List<ObesityHistoryItem>> obesityHistory(
            @PathVariable Long catId,
            @RequestParam(required = false, defaultValue = "7") Integer limit,
            Authentication authentication
    ) {
        List<ObesityHistoryItem> items = obesityCheckService.listObesityHistory(catId, limit, authentication);
        return ResponseEntity.ok(items);
    }
}

