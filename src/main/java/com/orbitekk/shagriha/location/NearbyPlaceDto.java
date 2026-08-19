package com.orbitekk.shagriha.location;

public record NearbyPlaceDto(String name, String category, double latitude,
                             double longitude, long distanceMeters) {}
