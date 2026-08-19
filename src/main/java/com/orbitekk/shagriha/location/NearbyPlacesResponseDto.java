package com.orbitekk.shagriha.location;

import java.util.List;

public record NearbyPlacesResponseDto(
        List<NearbyPlaceDto> groceries,
        List<NearbyPlaceDto> restaurants,
        List<NearbyPlaceDto> pharmacies,
        List<NearbyPlaceDto> gasStations,
        List<NearbyPlaceDto> transit) {
    public static NearbyPlacesResponseDto empty() {
        return new NearbyPlacesResponseDto(List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
