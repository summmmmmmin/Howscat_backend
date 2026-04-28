package com.example.howscat.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CalendarMemoUpdateRequest {

    @Size(max = 500, message = "메모는 500자를 초과할 수 없습니다.")
    private String content;

    /** yyyy-MM-dd — 있으면 메모 날짜도 함께 변경 */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 yyyy-MM-dd 이어야 합니다.")
    private String memoDate;
}

