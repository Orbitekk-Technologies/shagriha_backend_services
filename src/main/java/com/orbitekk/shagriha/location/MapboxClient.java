package com.orbitekk.shagriha.location;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class MapboxClient {
    private final RestClient client;
    private final MapboxProperties properties;

    public MapboxClient(MapboxProperties properties) {
        this.client = RestClient.builder()
                .baseUrl("https://api.mapbox.com/search/searchbox/v1")
                .build();
        this.properties = properties;
    }

    public List<Candidate> searchCategory(String category, double longitude, double latitude) {
        if (properties.getAccessToken().isBlank()) throw new IllegalStateException("Mapbox access token is not configured");
        JsonNode body = client.get().uri(uri -> uri.path("/category/{category}")
                        .queryParam("proximity", longitude + "," + latitude)
                        .queryParam("limit", 5)
                        .queryParam("language", "en")
                        .queryParam("access_token", properties.getAccessToken())
                        .build(category))
                .retrieve().body(JsonNode.class);
        List<Candidate> candidates = new ArrayList<>();
        if (body == null || !body.path("features").isArray()) return candidates;
        for (JsonNode feature : body.path("features")) {
            JsonNode coordinates = feature.path("geometry").path("coordinates");
            if (coordinates.size() < 2) continue;
            String name = feature.path("properties").path("name").asText();
            if (name.isBlank()) name = feature.path("properties").path("feature_name").asText();
            if (!name.isBlank()) candidates.add(new Candidate(name, coordinates.get(0).asDouble(), coordinates.get(1).asDouble()));
        }
        return candidates;
    }

    public record Candidate(String name, double longitude, double latitude) {}
}
