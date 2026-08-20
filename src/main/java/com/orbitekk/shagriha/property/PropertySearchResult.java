package com.orbitekk.shagriha.property;

import java.util.List;

public record PropertySearchResult(
        List<PropertyView> properties,
        MatchType matchType,
        String searchedLocation,
        Double effectiveRadiusKm,
        long totalResults,
        int page,
        int size,
        int totalPages) {
    public enum MatchType { ALL, NEARBY, CITY, RADIUS_EXPANDED, STATE, NONE }
}
