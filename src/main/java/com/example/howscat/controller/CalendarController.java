package com.example.howscat.controller;

import com.example.howscat.dto.CalendarEventItem;
import com.example.howscat.dto.CalendarMemoUpdateRequest;
import com.example.howscat.dto.CalendarMemoCreateRequest;
import com.example.howscat.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cats")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/{catId}/calendar")
    public ResponseEntity<List<CalendarEventItem>> listCalendar(
            @PathVariable Long catId,
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication
    ) {
        // 최소: catId 기준으로 메모만 조회
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);

        List<CalendarEventItem> events = calendarService.listCalendarEvents(catId, fromDate, toDate, authentication);
        return ResponseEntity.ok(events);
    }

    @PutMapping("/{catId}/calendar-memos/{memoId}")
    public ResponseEntity<Void> updateCalendarMemo(
            @PathVariable Long catId,
            @PathVariable Long memoId,
            @RequestBody @Valid CalendarMemoUpdateRequest request,
            Authentication authentication
    ) {
        calendarService.updateCalendarMemo(
                catId, memoId, request.getContent(), request.getMemoDate(), authentication);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{catId}/calendar-memos")
    public ResponseEntity<Void> addCalendarMemo(
            @PathVariable Long catId,
            @RequestBody @Valid CalendarMemoCreateRequest request,
            Authentication authentication
    ) {
        calendarService.addCalendarMemo(catId, request, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{catId}/calendar-memos")
    public ResponseEntity<Void> deleteAllCalendarMemos(
            @PathVariable Long catId,
            Authentication authentication
    ) {
        calendarService.deleteAllCalendarMemos(catId, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{catId}/calendar-memos/{memoId}")
    public ResponseEntity<Void> deleteCalendarMemo(
            @PathVariable Long catId,
            @PathVariable Long memoId,
            Authentication authentication
    ) {
        calendarService.deleteCalendarMemo(catId, memoId, authentication);
        return ResponseEntity.ok().build();
    }
}

