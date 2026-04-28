package com.example.howscat.controller;

import com.example.howscat.dto.LitterBoxCreateRequest;
import com.example.howscat.dto.LitterBoxItem;
import com.example.howscat.dto.MedicationCreateRequest;
import com.example.howscat.dto.MedicationItem;
import com.example.howscat.dto.VetVisitCreateRequest;
import com.example.howscat.dto.VetVisitItem;
import com.example.howscat.service.CareRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class CareRecordController {

    private final CareRecordService careRecordService;

    // ── Medication ────────────────────────────────────────────────────────────

    @GetMapping("/{catId}/medications")
    public ResponseEntity<List<MedicationItem>> getMedications(
            @PathVariable Long catId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(careRecordService.listMedications(catId, authentication));
    }

    @PostMapping("/{catId}/medications")
    public ResponseEntity<Map<String, String>> addMedication(
            @PathVariable Long catId,
            @RequestBody MedicationCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.addMedication(catId, request, authentication);
        return ResponseEntity.ok(Map.of("message", "투약 기록이 저장되었습니다."));
    }

    @PutMapping("/{catId}/medications/{medicationId}")
    public ResponseEntity<Void> updateMedication(
            @PathVariable Long catId,
            @PathVariable Long medicationId,
            @RequestBody MedicationCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.updateMedication(catId, medicationId, request, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{catId}/medications/{medicationId}")
    public ResponseEntity<Void> deleteMedication(
            @PathVariable Long catId,
            @PathVariable Long medicationId,
            Authentication authentication
    ) {
        careRecordService.deleteMedication(catId, medicationId, authentication);
        return ResponseEntity.ok().build();
    }

    // ── Litter Box ────────────────────────────────────────────────────────────

    @GetMapping("/{catId}/litter-records")
    public ResponseEntity<List<LitterBoxItem>> getLitterRecords(
            @PathVariable Long catId,
            @RequestParam(required = false, defaultValue = "30") Integer limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(careRecordService.listLitterRecords(catId, limit, authentication));
    }

    @PostMapping("/{catId}/litter-records")
    public ResponseEntity<Map<String, String>> addLitterRecord(
            @PathVariable Long catId,
            @RequestBody LitterBoxCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.addLitterRecord(catId, request, authentication);
        return ResponseEntity.ok(Map.of("message", "화장실 기록이 저장되었습니다."));
    }

    @PutMapping("/{catId}/litter-records/{recordId}")
    public ResponseEntity<Void> updateLitterRecord(
            @PathVariable Long catId,
            @PathVariable Long recordId,
            @RequestBody LitterBoxCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.updateLitterRecord(catId, recordId, request, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{catId}/litter-records/{recordId}")
    public ResponseEntity<Void> deleteLitterRecord(
            @PathVariable Long catId,
            @PathVariable Long recordId,
            Authentication authentication
    ) {
        careRecordService.deleteLitterRecord(catId, recordId, authentication);
        return ResponseEntity.ok().build();
    }

    // ── Vet Visit ─────────────────────────────────────────────────────────────

    @GetMapping("/{catId}/vet-visits")
    public ResponseEntity<List<VetVisitItem>> getVetVisits(
            @PathVariable Long catId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(careRecordService.listVetVisits(catId, authentication));
    }

    @PostMapping("/{catId}/vet-visits")
    public ResponseEntity<Map<String, String>> addVetVisit(
            @PathVariable Long catId,
            @RequestBody VetVisitCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.addVetVisit(catId, request, authentication);
        return ResponseEntity.ok(Map.of("message", "진료 기록이 저장되었습니다."));
    }

    @PutMapping("/{catId}/vet-visits/{visitId}")
    public ResponseEntity<Void> updateVetVisit(
            @PathVariable Long catId,
            @PathVariable Long visitId,
            @RequestBody VetVisitCreateRequest request,
            Authentication authentication
    ) {
        careRecordService.updateVetVisit(catId, visitId, request, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{catId}/vet-visits/{visitId}")
    public ResponseEntity<Void> deleteVetVisit(
            @PathVariable Long catId,
            @PathVariable Long visitId,
            Authentication authentication
    ) {
        careRecordService.deleteVetVisit(catId, visitId, authentication);
        return ResponseEntity.ok().build();
    }
}
