package com.example.howscat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks CalendarService calendarService;

    private Authentication auth(int userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void catId가_없으면_예외() {
        // DB에서 null 반환 → 존재하지 않는 고양이
        when(jdbcTemplate.query(
                contains("SELECT user_id FROM cat WHERE cat_id"),
                any(Object[].class),
                any(ResultSetExtractor.class)
        )).thenReturn(null);

        assertThatThrownBy(() ->
                calendarService.listCalendarEvents(999L, LocalDate.now(), LocalDate.now(), auth(1))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("cat not found");
    }

    @Test
    void 다른_사람_고양이에_접근하면_예외() {
        // 실제 소유자는 userId=2인데 userId=1이 접근
        when(jdbcTemplate.query(
                contains("SELECT user_id FROM cat WHERE cat_id"),
                any(Object[].class),
                any(ResultSetExtractor.class)
        )).thenReturn(2L);

        assertThatThrownBy(() ->
                calendarService.listCalendarEvents(1L, LocalDate.now(), LocalDate.now(), auth(1))
        ).isInstanceOf(SecurityException.class)
         .hasMessage("cat does not belong to user");
    }
}
