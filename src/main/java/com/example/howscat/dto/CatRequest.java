package com.example.howscat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CatRequest {
    // userId는 서버에서 JWT로 추출하므로 클라이언트 입력 무시

    @NotBlank(message = "고양이 이름을 입력하세요.")
    @Size(max = 30, message = "이름은 30자를 초과할 수 없습니다.")
    private String name;

    @NotBlank(message = "성별을 입력하세요.")
    private String gender;

    @NotBlank(message = "생년월일을 입력하세요.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 yyyy-MM-dd 이어야 합니다.")
    private String birthDate;
}
