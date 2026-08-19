package com.orbitekk.shagriha.property;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.orbitekk.shagriha.location.NearbyPlacesResponseDto;
import com.orbitekk.shagriha.location.NearbyPlacesService;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/properties")
public class PropertyController {
    private final PropertyReader reader;
    private final PropertyService properties;
    private final NearbyPlacesService nearbyPlaces;
    public PropertyController(PropertyReader reader, PropertyService properties, NearbyPlacesService nearbyPlaces) {
        this.reader = reader; this.properties = properties; this.nearbyPlaces = nearbyPlaces;
    }

    @GetMapping List<PropertyView> list(
            @RequestParam(required=false) BigDecimal priceMin, @RequestParam(required=false) BigDecimal priceMax,
            @RequestParam(required=false) Integer beds, @RequestParam(required=false) Integer baths,
            @RequestParam(required=false) String propertyType, @RequestParam(required=false) Integer squareFeetMin,
            @RequestParam(required=false) Integer squareFeetMax, @RequestParam(required=false) String amenities,
            @RequestParam(required=false) String favoriteIds, @RequestParam(required=false) String location) {
        return properties.list(priceMin, priceMax, beds, baths, propertyType, squareFeetMin, squareFeetMax, amenities, favoriteIds, location);
    }

    @GetMapping("/{id}") PropertyView get(@PathVariable long id) { return reader.get(id); }

    @GetMapping("/{id}/nearby") NearbyPlacesResponseDto nearby(@PathVariable long id) {
        return nearbyPlaces.get(id);
    }

    @PostMapping(consumes = "multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    PropertyView create(@AuthenticationPrincipal Jwt jwt, @RequestParam Map<String, String> fields,
                        @RequestParam(name="photos", required=false) List<MultipartFile> photos) {
        return properties.create(UUID.fromString(jwt.getSubject()), fields, photos);
    }

    @PutMapping(path="/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('MANAGER')")
    PropertyView update(@PathVariable long id, @AuthenticationPrincipal Jwt jwt,
                        @RequestParam Map<String, String> fields) {
        return properties.update(id, UUID.fromString(jwt.getSubject()), fields);
    }
}
