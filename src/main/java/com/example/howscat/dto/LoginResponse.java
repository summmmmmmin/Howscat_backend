package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String loginId;
    private String accessToken;
    private String refreshToken;
    private String name;
    private Long lastViewedCatId;
    private String lastViewedCatName;


    // 생성자 수정
    public LoginResponse(String loginId, String name,
                         Long lastViewedCatId, String lastViewedCatName,
                         String accessToken, String refreshToken) {
        this.loginId = loginId;
        this.name = name;
        this.lastViewedCatId = lastViewedCatId;
        this.lastViewedCatName = lastViewedCatName;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    // getter / setter도 추가
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}