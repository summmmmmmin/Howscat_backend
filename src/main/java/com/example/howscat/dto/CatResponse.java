package com.example.howscat.dto;

import com.example.howscat.domain.Cat;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Period;

@Getter
public class CatResponse {

    private Long id;
    private String name;
    private String gender;
    private String birthdate;
    private Integer age;

    public CatResponse(Cat cat) {
        this.id = cat.getId();
        this.name = cat.getName();
        this.gender = cat.getGender();
        this.birthdate = String.valueOf(cat.getBirthDate());
        if (cat.getBirthDate() != null) {
            this.age = Period.between(cat.getBirthDate(), LocalDate.now()).getYears();
        }
    }
}