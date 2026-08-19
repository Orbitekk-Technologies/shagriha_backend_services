package com.orbitekk.shagriha.property;

import com.orbitekk.shagriha.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.orbitekk.shagriha.location.NearbyPlacesCache;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PropertyService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;
    private final NearbyPlacesCache nearbyCache;

    public PropertyService(JdbcClient jdbc, PropertyReader properties, NearbyPlacesCache nearbyCache) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.nearbyCache = nearbyCache;
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
        String address = required(fields, "addressLine1");
        String addressLine2 = optional(fields, "addressLine2");
        String city = required(fields, "city");
        String state = required(fields, "stateName");
        String stateCode = optional(fields, "stateCode");
        String country = required(fields, "countryName");
        String countryCode = required(fields, "countryCode");
        String postalCode = required(fields, "postalCode");
        BigDecimal price = decimal(fields, "pricePerMonth", false);
        BigDecimal deposit = decimal(fields, "securityDeposit", true);
        BigDecimal fee = optionalDecimal(fields, "applicationFee", BigDecimal.ZERO);
        int beds = integer(fields, "beds", 0, 100);
        int baths = integer(fields, "baths", 0, 100);
        int squareFeet = integer(fields, "squareFeet", 1, Integer.MAX_VALUE);
        double longitude = requiredCoordinate(fields, "longitude", -180, 180);
        double latitude = requiredCoordinate(fields, "latitude", -90, 90);

        long locationId = jdbc.sql("""
                INSERT INTO locations(address_line1,address_line2,city,state_name,state_code,country_name,country_code,
                    postal_code,formatted_address,mapbox_feature_id,coordinates)
                VALUES(:address,:addressLine2,:city,:state,:stateCode,:country,:countryCode,:postalCode,
                    :formattedAddress,:mapboxFeatureId,ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326)::geography) RETURNING id
                """)
                .param("address", address).param("city", city).param("state", state).param("country", country)
                .param("addressLine2", addressLine2).param("stateCode", stateCode).param("countryCode", countryCode)
                .param("formattedAddress", optional(fields, "formattedAddress")).param("mapboxFeatureId", optional(fields, "mapboxFeatureId"))
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

    @Transactional
    public PropertyView update(long propertyId, UUID managerId, Map<String, String> fields) {
        if (!properties.isManagedBy(propertyId, managerId)) throw ApiException.notFound("Property not found");
        double longitude = requiredCoordinate(fields, "longitude", -180, 180);
        double latitude = requiredCoordinate(fields, "latitude", -90, 90);
        boolean locationChanged = jdbc.sql("""
                SELECT NOT ST_Equals(l.coordinates::geometry, ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326))
                FROM locations l JOIN properties p ON p.location_id=l.id WHERE p.id=:id
                """).param("longitude", longitude).param("latitude", latitude).param("id", propertyId)
                .query(Boolean.class).single();
        jdbc.sql("""
                UPDATE locations SET address_line1=:address,address_line2=:addressLine2,city=:city,
                    state_name=:stateName,state_code=:stateCode,country_name=:countryName,country_code=:countryCode,
                    postal_code=:postalCode,formatted_address=:formattedAddress,mapbox_feature_id=:mapboxFeatureId,
                    coordinates=ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326)::geography
                WHERE id=(SELECT location_id FROM properties WHERE id=:id)
                """).param("address", required(fields, "addressLine1")).param("addressLine2", optional(fields, "addressLine2"))
                .param("city", required(fields, "city")).param("stateName", required(fields, "stateName"))
                .param("stateCode", optional(fields, "stateCode")).param("countryName", required(fields, "countryName"))
                .param("countryCode", required(fields, "countryCode")).param("postalCode", required(fields, "postalCode"))
                .param("formattedAddress", optional(fields, "formattedAddress")).param("mapboxFeatureId", optional(fields, "mapboxFeatureId"))
                .param("longitude", longitude).param("latitude", latitude).param("id", propertyId).update();
        jdbc.sql("""
                UPDATE properties SET name=:name,description=:description,price_per_month=:price,
                    security_deposit=:deposit,pets_allowed=:pets,parking_included=:parking,beds=:beds,baths=:baths,
                    square_feet=:squareFeet,property_type=:propertyType WHERE id=:id
                """).param("name", required(fields, "name")).param("description", required(fields, "description"))
                .param("price", decimal(fields, "pricePerMonth", false)).param("deposit", decimal(fields, "securityDeposit", true))
                .param("pets", bool(fields.get("isPetsAllowed"))).param("parking", bool(fields.get("isParkingIncluded")))
                .param("beds", integer(fields, "beds", 0, 100)).param("baths", integer(fields, "baths", 0, 100))
                .param("squareFeet", integer(fields, "squareFeet", 1, Integer.MAX_VALUE))
                .param("propertyType", required(fields, "propertyType")).param("id", propertyId).update();
        jdbc.sql("DELETE FROM property_amenities WHERE property_id=:id").param("id", propertyId).update();
        insertValues(propertyId, "property_amenities", "amenity", stringSet(fields.get("amenities")));
        if (locationChanged) nearbyCache.invalidate(propertyId);
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
    private static String optional(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }
    private static BigDecimal decimal(Map<String, String> fields, String key, boolean zeroAllowed) {
        try {
            BigDecimal value = new BigDecimal(required(fields, key));
            if (value.signum() < 0 || (!zeroAllowed && value.signum() == 0)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be a valid non-negative number"); }
    }
    private static BigDecimal optionalDecimal(Map<String, String> fields, String key, BigDecimal defaultValue) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a valid non-negative number");
        }
    }
    private static int integer(Map<String, String> fields, String key, int min, int max) {
        try {
            int value = Integer.parseInt(required(fields, key));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " is invalid"); }
    }
    private static double requiredCoordinate(Map<String, String> fields, String key, double min, double max) {
        try {
            double value = Double.parseDouble(required(fields, key));
            if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Coordinates are invalid"); }
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
