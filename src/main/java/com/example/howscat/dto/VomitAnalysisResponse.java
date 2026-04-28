package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VomitAnalysisResponse {

    private Long vomitStatusId;
    private String severityLevel;
    private String guideText;
    private String riskLevel;
    private Boolean urgent;
    private String aiGuide; // Gemini Vision이 생성한 한 줄 조언
    private String aiResult; // 색상·이물질 요약 (예: "흰색 · 거품 · 이물질(헤어볼)")
}

