package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LitterBoxItem {
    private Long recordId;
    private String date;
    private Integer count;
    private String color;
    private String shape;
    private Boolean abnormal;
    private String notes;
}
