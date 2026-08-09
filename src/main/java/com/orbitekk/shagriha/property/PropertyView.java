package com.orbitekk.shagriha.property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PropertyView(
        long id, String name, String description, BigDecimal pricePerMonth,
        BigDecimal securityDeposit, BigDecimal applicationFee, List<String> photoUrls,
        List<String> amenities, List<String> highlights, boolean isPetsAllowed,
        boolean isParkingIncluded, int beds, int baths, int squareFeet,
        String propertyType, Instant postedDate, double averageRating, int numberOfReviews,
        long locationId, UUID managerUserId, LocationView location, ManagerView manager) {
    public record Coordinates(double longitude, double latitude) {}
    public record LocationView(long id, String address, String city, String state,
                               String country, String postalCode, Coordinates coordinates) {}
    public record ManagerView(long id, UUID userId, String name, String email,
                              String phoneNumber, String image) {}
}
