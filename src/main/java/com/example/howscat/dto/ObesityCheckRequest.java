package com.example.howscat.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ObesityCheckRequest {

    @NotNull(message = "체지방률을 입력하세요.")
    @DecimalMin(value = "0.0", message = "체지방률은 0% 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "체지방률은 100% 이하여야 합니다.")
    private Double bodyFatPercent;

    @NotNull(message = "체중을 입력하세요.")
    @DecimalMin(value = "0.1", message = "체중은 0.1kg 이상이어야 합니다.")
    @DecimalMax(value = "50.0", message = "체중은 50kg 이하여야 합니다.")
    private Double weightKg;

    @NotNull(message = "사료 칼로리를 입력하세요.")
    @DecimalMin(value = "0.1", message = "사료 칼로리는 0보다 커야 합니다.")
    private Double feedKcalPerG;
}

