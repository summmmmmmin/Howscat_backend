package com.example.howscat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 병원 목록/즐겨찾기에서 공통으로 쓰는 응답 DTO.
 * (기존 Android에서 필요한 필드만 구성)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HospitalNearbyResponse {

    // DB 병원인 경우 hospital_id
    private Long id;

    // Kakao Place인 경우 placeId (DB 병원일 때는 null)
    private String kakaoPlaceId;

    private String name;
    private String address;

    private Double latitude;
    private Double longitude;

    private String phone;

    private Boolean open24Hours;
    private Boolean operating;
    private Double rating;

    // lat/lng 제공 시 계산됨
    private Double distanceKm;

    // 즐겨찾기 여부
    private Boolean favorited;
}
