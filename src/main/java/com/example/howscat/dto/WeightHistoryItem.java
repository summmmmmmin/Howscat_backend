package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeightHistoryItem {

    private String date; // yyyy-MM-dd (UI-friendly)
    private Double weightKg;
    private Double recommendedWaterMl;
    private Double recommendedFoodG;
}

