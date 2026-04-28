package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObesityHistoryItem {

    private String date; // yyyy-MM-dd
    private String obesityLevel;
    private Double bodyFatPercent;
    private Double recommendedTargetWeight;
    private Double recommendedWater;
    private Double recommendedFood;
}

