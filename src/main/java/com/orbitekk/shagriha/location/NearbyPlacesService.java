package com.orbitekk.shagriha.location;

import com.orbitekk.shagriha.property.PropertyReader;
import com.orbitekk.shagriha.common.ApiException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class NearbyPlacesService {
    private static final Logger log = LoggerFactory.getLogger(NearbyPlacesService.class);
    private final PropertyReader properties;
    private final MapboxClient mapbox;
    private final NearbyPlacesCache cache;

    public NearbyPlacesService(PropertyReader properties, MapboxClient mapbox, NearbyPlacesCache cache) {
        this.properties = properties;
        this.mapbox = mapbox;
        this.cache = cache;
    }

    public NearbyPlacesResponseDto get(long propertyId) {
        var property = properties.get(propertyId);
        var coordinates = property.location().coordinates();
        if (!Double.isFinite(coordinates.latitude()) || !Double.isFinite(coordinates.longitude())
                || (coordinates.latitude() == 0 && coordinates.longitude() == 0))
            throw new IllegalArgumentException("Property location is unavailable");
        var cached = cache.get(propertyId);
        if (cached.isPresent()) return cached.get();
        try {
            var response = new NearbyPlacesResponseDto(
                    nearest("grocery", "GROCERY", coordinates.longitude(), coordinates.latitude()),
                    nearest("restaurant", "RESTAURANT", coordinates.longitude(), coordinates.latitude()),
                    nearest("pharmacy", "PHARMACY", coordinates.longitude(), coordinates.latitude()),
                    nearest("gas_station", "GAS_STATION", coordinates.longitude(), coordinates.latitude()),
                    nearest("public_transportation", "TRANSIT", coordinates.longitude(), coordinates.latitude()));
            cache.put(propertyId, response);
            return response;
        } catch (RuntimeException exception) {
            log.warn("Nearby place lookup failed for property {}: {}", propertyId, exception.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Nearby places are temporarily unavailable");
        }
    }

    private List<NearbyPlaceDto> nearest(String mapboxCategory, String category, double longitude, double latitude) {
        return mapbox.searchCategory(mapboxCategory, longitude, latitude).stream()
                .map(item -> new NearbyPlaceDto(item.name(), category, item.latitude(), item.longitude(),
                        Math.round(distanceMeters(latitude, longitude, item.latitude(), item.longitude()))))
                .min(Comparator.comparingLong(NearbyPlaceDto::distanceMeters)).map(List::of).orElseGet(List::of);
    }

    static double distanceMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double earthRadius = 6_371_000;
        double lat = Math.toRadians(latitude2 - latitude1);
        double lon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2) + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2)) * Math.sin(lon / 2) * Math.sin(lon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
