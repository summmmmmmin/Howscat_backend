package com.example.howscat.client;

import com.example.howscat.dto.HospitalNearbyResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KakaoLocalApiClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${kakao.rest.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    void init() {
        // property injection이 비어있을 경우를 대비한 env fallback
        if (apiKey == null || apiKey.isBlank()) {
            String env = System.getenv("KAKAO_REST_API_KEY");
            if (env != null && !env.isBlank()) {
                apiKey = env;
            }
        }
        boolean configured = apiKey != null && !apiKey.isBlank();
        log.info("KakaoLocalApiClient configured={}", configured);
    }

    public List<HospitalNearbyResponse> searchAnimalHospitals(
            double lat,
            double lng,
            double radiusKm,
            int size
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Kakao API key is not configured");
            return Collections.emptyList();
        }

        int radiusMeters = (int) Math.round(radiusKm * 1000.0);
        if (radiusMeters < 1) radiusMeters = 20000;

        String url = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                .queryParam("query", "동물병원")
                .queryParam("x", lng)
                .queryParam("y", lat)
                .queryParam("radius", radiusMeters)
                .queryParam("sort", "distance")
                .queryParam("size", size)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "KakaoAK " + apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Kakao local request failed status={} bodyPresent={}",
                        resp.getStatusCode(), resp.getBody() != null);
                return Collections.emptyList();
            }

            JsonNode root = JSON.readTree(resp.getBody());
            JsonNode docs = root.get("documents");
            if (docs == null || !docs.isArray()) return Collections.emptyList();

            List<HospitalNearbyResponse> out = new ArrayList<>();
            for (JsonNode d : docs) {
                String kakaoPlaceId = d.path("id").asText(null);
                String name = d.path("place_name").asText(null);
                String address = d.path("address_name").asText(null);
                String phone = d.path("phone").asText(null);

                // Kakao: y=latitude, x=longitude
                Double y = d.hasNonNull("y") ? d.get("y").asDouble() : null;
                Double x = d.hasNonNull("x") ? d.get("x").asDouble() : null;

                HospitalNearbyResponse h = new HospitalNearbyResponse();
                h.setId(null); // DB 병원 매핑은 별도(현재는 Kakao 결과 표시용)
                h.setKakaoPlaceId(kakaoPlaceId);
                h.setName(name);
                h.setAddress(address);
                h.setLatitude(y);
                h.setLongitude(x);
                h.setPhone(phone);
                h.setOpen24Hours(null);
                h.setOperating(null);
                h.setRating(null);
                h.setDistanceKm(null);
                h.setFavorited(false);

                out.add(h);
            }

            return out;
        } catch (Exception e) {
            log.warn("Kakao local request exception", e);
            return Collections.emptyList();
        }
    }
}

