package com.example.howscat.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CareWeightRequest {

    @NotNull(message = "체중을 입력하세요.")
    @DecimalMin(value = "0.1", message = "체중은 0.1kg 이상이어야 합니다.")
    @DecimalMax(value = "50.0", message = "체중은 50kg 이하여야 합니다.")
    private Double weightKg;

    @DecimalMin(value = "0.0", message = "음수는 입력할 수 없습니다.")
    private Double waterMl;

    @DecimalMin(value = "0.0", message = "음수는 입력할 수 없습니다.")
    private Double foodG;

    /** yyyy-MM-dd, null이면 오늘 */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 yyyy-MM-dd 이어야 합니다.")
    private String memoDate;
}
