package com.example.howscat.dto;

import lombok.Getter;

@Getter
public class VomitAnalysisRequest {

    private String imageBase64; // Gemini Vision 분석용 base64 이미지

    private String memo;
    private String imagePath;
}

