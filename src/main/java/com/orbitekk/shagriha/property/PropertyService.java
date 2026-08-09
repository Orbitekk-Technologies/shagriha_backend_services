package com.orbitekk.shagriha.property;

import com.orbitekk.shagriha.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PropertyService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;

    public PropertyService(JdbcClient jdbc, PropertyReader properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<PropertyView> list(BigDecimal priceMin, BigDecimal priceMax, Integer beds, Integer baths,
                                   String propertyType, Integer squareFeetMin, Integer squareFeetMax,
                                   String amenities, String favoriteIds, String location) {
        Set<Long> ids = longSet(favoriteIds);
        Set<String> requiredAmenities = stringSet(amenities);
        String place = location == null ? null : location.trim().toLowerCase(Locale.ROOT);
        return properties.list().stream()
                .filter(p -> ids.isEmpty() || ids.contains(p.id()))
                .filter(p -> priceMin == null || p.pricePerMonth().compareTo(priceMin) >= 0)
                .filter(p -> priceMax == null || p.pricePerMonth().compareTo(priceMax) <= 0)
                .filter(p -> beds == null || p.beds() >= beds)
                .filter(p -> baths == null || p.baths() >= baths)
                .filter(p -> squareFeetMin == null || p.squareFeet() >= squareFeetMin)
                .filter(p -> squareFeetMax == null || p.squareFeet() <= squareFeetMax)
                .filter(p -> propertyType == null || propertyType.equalsIgnoreCase("any") || p.propertyType().equalsIgnoreCase(propertyType))
                .filter(p -> requiredAmenities.isEmpty() || p.amenities().containsAll(requiredAmenities))
                .filter(p -> place == null || place.isBlank() || String.join(" ", p.location().address(), p.location().city(), p.location().state(), p.location().country(), p.location().postalCode()).toLowerCase(Locale.ROOT).contains(place))
                .toList();
    }

    @Transactional
    public PropertyView create(UUID managerId, Map<String, String> fields, List<MultipartFile> photos) {
        requireManager(managerId);
        String name = required(fields, "name");
        String description = required(fields, "description");
        String address = required(fields, "address");
        String city = required(fields, "city");
        String state = required(fields, "state");
        String country = required(fields, "country");
        String postalCode = required(fields, "postalCode");
        BigDecimal price = decimal(fields, "pricePerMonth", false);
        BigDecimal deposit = decimal(fields, "securityDeposit", true);
        BigDecimal fee = decimal(fields, "applicationFee", true);
        int beds = integer(fields, "beds", 0, 100);
        int baths = integer(fields, "baths", 0, 100);
        int squareFeet = integer(fields, "squareFeet", 1, Integer.MAX_VALUE);
        double longitude = floating(fields.get("longitude"));
        double latitude = floating(fields.get("latitude"));

        long locationId = jdbc.sql("INSERT INTO locations(address,city,state,country,postal_code,coordinates) VALUES(:address,:city,:state,:country,:postalCode,ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326)::geography) RETURNING id")
                .param("address", address).param("city", city).param("state", state).param("country", country)
                .param("postalCode", postalCode).param("longitude", longitude).param("latitude", latitude)
                .query(Long.class).single();
        long propertyId = jdbc.sql("""
                INSERT INTO properties(manager_user_id,location_id,name,description,stay_type,bath_type,gender_preference,
                    price_per_month,security_deposit,application_fee,pets_allowed,parking_included,beds,baths,square_feet,
                    property_type,available_from,status)
                VALUES(:managerId,:locationId,:name,:description,'ENTIRE_PLACE','PRIVATE','ANY',:price,:deposit,:fee,
                    :pets,:parking,:beds,:baths,:squareFeet,:propertyType,:availableFrom,'PUBLISHED') RETURNING id
                """).param("managerId", managerId).param("locationId", locationId).param("name", name)
                .param("description", description).param("price", price).param("deposit", deposit).param("fee", fee)
                .param("pets", bool(fields.get("isPetsAllowed"))).param("parking", bool(fields.get("isParkingIncluded")))
                .param("beds", beds).param("baths", baths).param("squareFeet", squareFeet)
                .param("propertyType", required(fields, "propertyType"))
                .param("availableFrom", date(fields.get("availableFrom"))).query(Long.class).single();

        insertValues(propertyId, "property_amenities", "amenity", stringSet(fields.get("amenities")));
        insertValues(propertyId, "property_highlights", "highlight", stringSet(fields.get("highlights")));
        int order = 0;
        for (MultipartFile photo : photos == null ? List.<MultipartFile>of() : photos) {
            if (photo.isEmpty()) continue;
            if (photo.getContentType() == null || !photo.getContentType().startsWith("image/"))
                throw new IllegalArgumentException("Photos must be image files");
            if (photo.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Each photo must be 10 MB or smaller");
            try {
                String url = "data:" + photo.getContentType() + ";base64," + Base64.getEncoder().encodeToString(photo.getBytes());
                jdbc.sql("INSERT INTO property_photos(property_id,url,display_order) VALUES(:propertyId,:url,:displayOrder)")
                        .param("propertyId", propertyId).param("url", url).param("displayOrder", order++).update();
            } catch (IOException ex) {
                throw new IllegalArgumentException("Could not read uploaded photo", ex);
            }
        }
        return properties.get(propertyId);
    }

    private void requireManager(UUID managerId) {
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM manager_profiles WHERE user_id=:id)")
                .param("id", managerId).query(Boolean.class).single();
        if (!exists) throw ApiException.notFound("Manager not found");
    }

    private void insertValues(long propertyId, String table, String column, Set<String> values) {
        for (String value : values) jdbc.sql("INSERT INTO " + table + "(property_id," + column + ") VALUES(:propertyId,:value)")
                .param("propertyId", propertyId).param("value", value).update();
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value.trim();
    }
    private static BigDecimal decimal(Map<String, String> fields, String key, boolean zeroAllowed) {
        try {
            BigDecimal value = new BigDecimal(required(fields, key));
            if (value.signum() < 0 || (!zeroAllowed && value.signum() == 0)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be a valid non-negative number"); }
    }
    private static int integer(Map<String, String> fields, String key, int min, int max) {
        try {
            int value = Integer.parseInt(required(fields, key));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " is invalid"); }
    }
    private static double floating(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Coordinates are invalid"); }
    }
    private static boolean bool(String value) { return Boolean.parseBoolean(value); }
    private static LocalDate date(String value) { return value == null || value.isBlank() ? null : LocalDate.parse(value); }
    private static Set<String> stringSet(String csv) {
        if (csv == null || csv.isBlank() || csv.equalsIgnoreCase("any")) return Set.of();
        String normalized = csv.trim().replaceAll("^\\[|\\]$", "").replace("\"", "");
        Set<String> values = new LinkedHashSet<>();
        for (String value : normalized.split(",")) if (!value.isBlank()) values.add(value.trim());
        return values;
    }
    private static Set<Long> longSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        try {
            Set<Long> values = new HashSet<>();
            for (String value : csv.split(",")) values.add(Long.parseLong(value.trim()));
            return values;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("favoriteIds is invalid"); }
    }
}
