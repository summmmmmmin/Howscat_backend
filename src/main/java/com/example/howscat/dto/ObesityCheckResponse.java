package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ObesityCheckResponse {

    private String obesityLevel;
    private Double bodyFatPercent;
    private Double recommendedTargetWeight;
    private Double recommendedWater;
    private Double recommendedFood;
}

