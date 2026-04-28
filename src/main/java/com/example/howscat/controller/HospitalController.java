package com.example.howscat.controller;

import com.example.howscat.dto.HospitalNearbyResponse;
import com.example.howscat.service.HospitalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    @GetMapping("/nearby")
    public ResponseEntity<List<HospitalNearbyResponse>> listNearby(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "20") Double radius,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean only24h,
            @RequestParam(required = false) Boolean onlyOperating,
            Authentication authentication
    ) {
        List<HospitalNearbyResponse> result = hospitalService.listNearby(
                lat,
                lng,
                radius,
                sort,
                only24h,
                onlyOperating,
                authentication
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<HospitalNearbyResponse>> listFavorites(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String sort,
            Authentication authentication
    ) {
        List<HospitalNearbyResponse> result = hospitalService.listFavorites(
                lat,
                lng,
                sort,
                authentication
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> addFavorite(
            @PathVariable("id") Long hospitalId,
            Authentication authentication
    ) {
        hospitalService.addFavorite(hospitalId, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable("id") Long hospitalId,
            Authentication authentication
    ) {
        hospitalService.removeFavorite(hospitalId, authentication);
        return ResponseEntity.ok().build();
    }
}

