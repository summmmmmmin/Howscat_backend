package com.example.howscat.controller;

import com.example.howscat.dto.HealthScheduleCreateRequest;
import com.example.howscat.dto.HealthScheduleItem;
import com.example.howscat.dto.HealthScheduleUpdateRequest;
import com.example.howscat.service.HealthScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class HealthCalendarController {

    private final HealthScheduleService healthScheduleService;

    // 건강검진/예방접종 스케줄 조회
    @GetMapping("/{catId}/health-schedules")
    public ResponseEntity<List<HealthScheduleItem>> listSchedules(
            @PathVariable Long catId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                healthScheduleService.listSchedules(catId, authentication)
        );
    }

    // 스케줄 신규 등록 or 갱신 (upsert)
    @PostMapping("/{catId}/health-schedules")
    public ResponseEntity<HealthScheduleItem> createSchedule(
            @PathVariable Long catId,
            @RequestBody HealthScheduleCreateRequest request,
            Authentication authentication
    ) {
        HealthScheduleItem item = healthScheduleService.createOrUpdateSchedule(catId, request, authentication);
        return ResponseEntity.ok(item);
    }

    // 스케줄 수정 (nextDate / customCycleMonth / alarmEnabled)
    @PutMapping("/{catId}/health-schedules/{scheduleId}")
    public ResponseEntity<Void> updateSchedule(
            @PathVariable Long catId,
            @PathVariable Long scheduleId,
            @RequestBody HealthScheduleUpdateRequest request,
            Authentication authentication
    ) {
        healthScheduleService.updateSchedule(catId, scheduleId, request, authentication);
        return ResponseEntity.ok().build();
    }

    // 스케줄 삭제
    @DeleteMapping("/{catId}/health-schedules/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long catId,
            @PathVariable Long scheduleId,
            Authentication authentication
    ) {
        healthScheduleService.deleteSchedule(catId, scheduleId, authentication);
        return ResponseEntity.ok().build();
    }
}

