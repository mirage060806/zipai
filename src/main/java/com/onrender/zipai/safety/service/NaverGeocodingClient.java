package com.onrender.zipai.safety.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
@Primary
public class NaverGeocodingClient implements SafetyCoordinateGeocoder {
    private static final Logger log = LoggerFactory.getLogger(NaverGeocodingClient.class);
    private final String clientId;
    private final String clientSecret;
    private final RestClient client;

    public NaverGeocodingClient(
        @Value("${NAVER_MAPS_CLIENT_ID:}") String clientId,
        @Value("${NAVER_MAPS_CLIENT_SECRET:}") String clientSecret
    ) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<Coordinate> geocode(String query) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw unavailable("네이버 Maps 인증 환경변수 두 항목을 설정해 주세요.");
        }
        URI uri = UriComponentsBuilder.fromUriString("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")
            .queryParam("query", query).queryParam("count", 1).queryParam("language", "kor")
            .build().encode(StandardCharsets.UTF_8).toUri();
        JsonNode root = request(uri, "geocode");
        if (root == null || !"OK".equals(root.path("status").asText())) {
            throw unavailable("네이버 주소 검색 응답을 처리하지 못했습니다.");
        }
        JsonNode item = root.path("addresses").path(0);
        if (item.isMissingNode()) return Optional.empty();
        double longitude;
        double latitude;
        try {
            longitude = Double.parseDouble(item.path("x").asText());
            latitude = Double.parseDouble(item.path("y").asText());
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
                throw new IllegalArgumentException("Invalid coordinates");
            }
        } catch (IllegalArgumentException error) {
            throw unavailable("네이버 주소 검색의 좌표 응답이 올바르지 않습니다.");
        }
        String sido = element(item, "SIDO");
        String sigungu = element(item, "SIGUGUN");
        // Geocoding already supplies region names. Reverse lookup is needed only
        // when that metadata is absent, avoiding an unnecessary second API call.
        if (sido.isBlank() || sigungu.isBlank()) {
            try {
                URI reverse = UriComponentsBuilder.fromUriString("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc")
                    .queryParam("coords", longitude + "," + latitude)
                    .queryParam("sourcecrs", "EPSG:4326").queryParam("orders", "legalcode")
                    .queryParam("output", "json").build().encode(StandardCharsets.UTF_8).toUri();
                JsonNode response = request(reverse, "reverse-geocode");
                if (response != null && response.path("status").path("code").asInt(-1) == 0) {
                    JsonNode region = response.path("results").path(0).path("region");
                    if (sido.isBlank()) sido = region.path("area1").path("name").asText("");
                    if (sigungu.isBlank()) sigungu = region.path("area2").path("name").asText("");
                }
            } catch (ResponseStatusException error) {
                log.warn("[NAVER-GEO] supplementary region lookup unavailable; coordinates retained");
            }
        }
        String address = item.path("roadAddress").asText("");
        if (address.isBlank()) address = item.path("jibunAddress").asText(query);
        return Optional.of(new Coordinate(address, latitude, longitude,
            sido.isBlank() ? null : sido, sigungu.isBlank() ? null : sigungu));
    }

    private JsonNode request(URI uri, String operation) {
        try {
            return client.get().uri(uri)
                .header("x-ncp-apigw-api-key-id", clientId)
                .header("x-ncp-apigw-api-key", clientSecret)
                .header("Accept", "application/json").retrieve().body(JsonNode.class);
        } catch (RestClientResponseException error) {
            log.warn("[NAVER-GEO] operation={} httpStatus={}", operation, error.getStatusCode().value());
            int status = error.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw unavailable("네이버 Maps 인증 정보와 Geocoding API 사용 설정을 확인해 주세요.");
            }
            if (status == 429) throw unavailable("네이버 Maps 호출 한도를 초과했습니다.");
            throw unavailable("네이버 주소 검색 API가 오류를 반환했습니다.");
        } catch (Exception error) {
            log.warn("[NAVER-GEO] operation={} failure={}", operation, error.getClass().getSimpleName());
            throw unavailable("네이버 주소 검색 API 연결에 실패했습니다.");
        }
    }

    private static String element(JsonNode item, String type) {
        for (JsonNode element : item.path("addressElements")) {
            for (JsonNode value : element.path("types")) {
                if (type.equals(value.asText())) return element.path("longName").asText("");
            }
        }
        return "";
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
