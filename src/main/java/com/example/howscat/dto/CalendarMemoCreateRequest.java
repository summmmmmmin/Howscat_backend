package com.example.howscat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CalendarMemoCreateRequest {

    @NotBlank(message = "내용을 입력하세요.")
    @Size(max = 500, message = "메모는 500자를 초과할 수 없습니다.")
    private String content;

    @NotBlank(message = "날짜를 입력하세요.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 yyyy-MM-dd 이어야 합니다.")
    private String memoDate;

    /** 건강 유형과 연결(선택) */
    private Long healthTypeId;
}

