package com.example.howscat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {

    @NotBlank(message = "아이디를 입력하세요.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자 사이여야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 30, message = "이름은 30자를 초과할 수 없습니다.")
    private String name;
}