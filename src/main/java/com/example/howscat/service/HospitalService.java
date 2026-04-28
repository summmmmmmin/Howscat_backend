package com.example.howscat.service;

import com.example.howscat.client.KakaoLocalApiClient;
import com.example.howscat.dto.HospitalNearbyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HospitalService {

    private final JdbcTemplate jdbcTemplate;
    private final KakaoLocalApiClient kakaoLocalApiClient;

    public List<HospitalNearbyResponse> listNearby(
            Double lat,
            Double lng,
            Double radiusKm,
            String sort,
            Boolean only24h,
            Boolean onlyOperating,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();

        // 1) DB 병원 + 2) 카카오 로컬 결과를 합쳐서 보여줌
        List<HospitalNearbyResponse> hospitals = new ArrayList<>();

        // Kakao 결과는 lat/lng가 있어야 정상 검색됩니다.
        if (lat != null && lng != null) {
            double r = radiusKm != null ? radiusKm : 20.0;
            List<HospitalNearbyResponse> kakaoHospitals =
                    kakaoLocalApiClient.searchAnimalHospitals(lat, lng, r, 10);
            hospitals.addAll(kakaoHospitals);
        }

        // DB 병원(기존 데이터/즐겨찾기 기반)
        hospitals.addAll(queryHospitals(only24h, onlyOperating));

        Set<Long> favoriteHospitalIds = queryFavoriteHospitalIds(userId);

        // distance 계산 + radius 필터
        if (lat != null && lng != null) {
            double r = radiusKm != null ? radiusKm : 20.0; // default 20km
            double lat1 = lat;
            double lon1 = lng;

            List<HospitalNearbyResponse> filtered = new ArrayList<>();
            for (HospitalNearbyResponse h : hospitals) {
                Double d = haversineKm(lat1, lon1, h.getLatitude(), h.getLongitude());
                h.setDistanceKm(d);
                if (d != null && d <= r) {
                    filtered.add(h);
                }
            }
            hospitals = filtered;
        } else {
            for (HospitalNearbyResponse h : hospitals) {
                h.setDistanceKm(null);
            }
        }

        // favorited 표시
        for (HospitalNearbyResponse h : hospitals) {
                if (h.getId() != null) {
                    h.setFavorited(favoriteHospitalIds.contains(h.getId()));
                } else {
                    // Kakao 결과는 아직 DB 매핑이 없어서 찜 버튼을 막을 수 있게 null/false 유지
                    h.setFavorited(false);
                }
        }

        // 정렬
        String s = sort != null ? sort : "distance";
        switch (s) {
            case "rating" -> hospitals.sort((a, b) -> cmpNullableDouble(b.getRating(), a.getRating()));
            case "distance" -> hospitals.sort((a, b) -> cmpNullableDouble(a.getDistanceKm(), b.getDistanceKm()));
            default -> hospitals.sort((a, b) -> cmpNullableDouble(a.getDistanceKm(), b.getDistanceKm()));
        }

        return hospitals;
    }

    public List<HospitalNearbyResponse> listFavorites(
            Double lat,
            Double lng,
            String sort,
            Authentication authentication
    ) {
        Integer userId = (Integer) authentication.getPrincipal();

        List<HospitalNearbyResponse> hospitals = queryFavoriteHospitals(userId);

        if (lat != null && lng != null) {
            for (HospitalNearbyResponse h : hospitals) {
                h.setDistanceKm(haversineKm(lat, lng, h.getLatitude(), h.getLongitude()));
            }
        } else {
            for (HospitalNearbyResponse h : hospitals) {
                h.setDistanceKm(null);
            }
        }

        // favorites는 무조건 true
        for (HospitalNearbyResponse h : hospitals) {
            h.setFavorited(true);
        }

        String s = sort != null ? sort : "distance";
        switch (s) {
            case "rating" -> hospitals.sort((a, b) -> cmpNullableDouble(b.getRating(), a.getRating()));
            case "distance" -> hospitals.sort((a, b) -> cmpNullableDouble(a.getDistanceKm(), b.getDistanceKm()));
            default -> hospitals.sort((a, b) -> cmpNullableDouble(a.getDistanceKm(), b.getDistanceKm()));
        }

        return hospitals;
    }

    public void addFavorite(Long hospitalId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        jdbcTemplate.update(
                "INSERT INTO favorite_hospital (user_id, hospital_id, created_at) VALUES (?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE hospital_id = hospital_id",
                userId, hospitalId
        );
    }

    public void removeFavorite(Long hospitalId, Authentication authentication) {
        Integer userId = (Integer) authentication.getPrincipal();
        jdbcTemplate.update(
                "DELETE FROM favorite_hospital WHERE user_id = ? AND hospital_id = ?",
                userId, hospitalId
        );
    }

    private List<HospitalNearbyResponse> queryHospitals(Boolean only24h, Boolean onlyOperating) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT hospital_id, name, address, latitude, longitude, phone, is_24h, is_operating, rating ")
                .append("FROM hospital ");

        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();

        if (Boolean.TRUE.equals(only24h)) {
            where.add("is_24h = 1");
        }
        if (Boolean.TRUE.equals(onlyOperating)) {
            where.add("is_operating = 1");
        }

        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, i) -> {
            HospitalNearbyResponse h = new HospitalNearbyResponse();
            h.setId(rs.getLong("hospital_id"));
            h.setKakaoPlaceId(null);
            h.setName(rs.getString("name"));
            h.setAddress(rs.getString("address"));
            h.setLatitude(rs.getDouble("latitude"));
            h.setLongitude(rs.getDouble("longitude"));
            h.setPhone(rs.getString("phone"));
            h.setOpen24Hours(rs.getInt("is_24h") == 1);
            h.setOperating(rs.getInt("is_operating") == 1);
            h.setRating(rs.getDouble("rating"));
            h.setDistanceKm(null);
            h.setFavorited(false);
            return h;
        });
    }

    private Set<Long> queryFavoriteHospitalIds(Integer userId) {
        return new HashSet<>(
                jdbcTemplate.query(
                        "SELECT hospital_id FROM favorite_hospital WHERE user_id = ?",
                        new Object[]{userId},
                        (rs, i) -> rs.getLong("hospital_id")
                )
        );
    }

    private List<HospitalNearbyResponse> queryFavoriteHospitals(Integer userId) {
        String sql = "SELECT h.hospital_id, h.name, h.address, h.latitude, h.longitude, h.phone, h.is_24h, h.is_operating, h.rating " +
                "FROM favorite_hospital f " +
                "JOIN hospital h ON h.hospital_id = f.hospital_id " +
                "WHERE f.user_id = ?";

        return jdbcTemplate.query(sql, new Object[]{userId}, (rs, i) -> {
            HospitalNearbyResponse h = new HospitalNearbyResponse();
            h.setId(rs.getLong("hospital_id"));
            h.setKakaoPlaceId(null);
            h.setName(rs.getString("name"));
            h.setAddress(rs.getString("address"));
            h.setLatitude(rs.getDouble("latitude"));
            h.setLongitude(rs.getDouble("longitude"));
            h.setPhone(rs.getString("phone"));
            h.setOpen24Hours(rs.getInt("is_24h") == 1);
            h.setOperating(rs.getInt("is_operating") == 1);
            h.setRating(rs.getDouble("rating"));
            h.setDistanceKm(null);
            h.setFavorited(true);
            return h;
        });
    }

    // null 처리 포함 비교 유틸
    private int cmpNullableDouble(Double a, Double b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return Double.compare(a, b);
    }

    private Double haversineKm(double lat1, double lon1, Double lat2, Double lon2) {
        if (lat2 == null || lon2 == null) return null;
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

