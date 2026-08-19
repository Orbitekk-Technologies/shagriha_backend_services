package com.orbitekk.shagriha.location;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class NearbyPlacesCache {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final MapboxProperties properties;

    public NearbyPlacesCache(JdbcClient jdbc, ObjectMapper json, MapboxProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    public Optional<NearbyPlacesResponseDto> get(long propertyId) {
        Duration ttl = properties.getNearbyCacheTtl();
        return jdbc.sql("SELECT response_json::text FROM property_nearby_cache WHERE property_id=:id AND cached_at > now() - (:seconds * interval '1 second')")
                .param("id", propertyId).param("seconds", ttl.toSeconds()).query(String.class).optional()
                .flatMap(this::read);
    }

    public void put(long propertyId, NearbyPlacesResponseDto response) {
        try {
            jdbc.sql("INSERT INTO property_nearby_cache(property_id,response_json,cached_at) VALUES(:id,CAST(:json AS jsonb),now()) ON CONFLICT(property_id) DO UPDATE SET response_json=EXCLUDED.response_json,cached_at=now()")
                    .param("id", propertyId).param("json", json.writeValueAsString(response)).update();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not cache nearby places", exception);
        }
    }

    public void invalidate(long propertyId) {
        jdbc.sql("DELETE FROM property_nearby_cache WHERE property_id=:id").param("id", propertyId).update();
    }

    private Optional<NearbyPlacesResponseDto> read(String value) {
        try { return Optional.of(json.readValue(value, NearbyPlacesResponseDto.class)); }
        catch (JacksonException exception) { return Optional.empty(); }
    }
}
