package com.example.howscat.dto;

import lombok.Getter;

@Getter
public class LitterBoxCreateRequest {
    private String date;
    private Integer count;
    private String color;
    private String shape;
    private Boolean abnormal;
    private String notes;
}
