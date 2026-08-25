package com.orbitekk.shagriha.property;

import com.orbitekk.shagriha.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.orbitekk.shagriha.location.NearbyPlacesCache;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class PropertyService {
    private final JdbcClient jdbc;
    private final PropertyReader properties;
    private final NearbyPlacesCache nearbyCache;
    private final ObjectMapper json;

    public PropertyService(JdbcClient jdbc, PropertyReader properties, NearbyPlacesCache nearbyCache, ObjectMapper json) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.nearbyCache = nearbyCache;
        this.json = json;
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

    public PropertySearchResult search(BigDecimal priceMin, BigDecimal priceMax, Integer beds, Integer baths,
                                       String propertyType, Integer squareFeetMin, Integer squareFeetMax,
                                       String amenities, LocalDate availableFrom, Double latitude, Double longitude,
                                       String city, String state, String location, int page, int size, String sort) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("latitude and longitude must be provided together");
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180))
            throw new IllegalArgumentException("Coordinates are invalid");

        Set<String> requiredAmenities = stringSet(amenities);
        String order = switch (sort == null ? "newest" : sort.toLowerCase(Locale.ROOT)) {
            case "price_asc" -> "price_per_month ASC, id DESC";
            case "price_desc" -> "price_per_month DESC, id DESC";
            case "newest" -> "posted_at DESC, id DESC";
            default -> throw new IllegalArgumentException("sort is invalid");
        };
        boolean hasCoordinates = latitude != null;
        boolean hasCity = city != null && !city.isBlank();
        boolean hasState = state != null && !state.isBlank();
        boolean hasLocation = hasCoordinates || hasCity || hasState;
        String matchCase = hasLocation ? "CASE "
                + (hasCoordinates ? "WHEN ST_DWithin(l.coordinates, ST_GeogFromText(:point), 25000) THEN 1 " : "")
                + (hasCity ? "WHEN lower(l.city)=lower(:city) THEN 2 " : "")
                + (hasCoordinates ? "WHEN ST_DWithin(l.coordinates, ST_GeogFromText(:point), 100000) THEN 3 " : "")
                + (hasState ? "WHEN lower(COALESCE(l.state_code,l.state_name))=lower(:state) OR lower(l.state_name)=lower(:state) THEN 4 " : "")
                + "ELSE 99 END" : "99";
        String sql = """
            WITH candidates AS (
              SELECT p.id,p.price_per_month,p.posted_at,
                     %s AS match_rank
              FROM properties p JOIN locations l ON l.id=p.location_id
              WHERE p.status='PUBLISHED'
                AND (CAST(:priceMin AS numeric) IS NULL OR p.price_per_month >= :priceMin)
                AND (CAST(:priceMax AS numeric) IS NULL OR p.price_per_month <= :priceMax)
                AND (CAST(:beds AS integer) IS NULL OR p.beds >= :beds)
                AND (CAST(:baths AS integer) IS NULL OR p.baths >= :baths)
                AND (CAST(:squareFeetMin AS integer) IS NULL OR p.square_feet >= :squareFeetMin)
                AND (CAST(:squareFeetMax AS integer) IS NULL OR p.square_feet <= :squareFeetMax)
                AND (CAST(:propertyType AS varchar) IS NULL OR lower(p.property_type)=lower(:propertyType))
                AND (CAST(:availableFrom AS date) IS NULL OR p.available_from IS NULL OR p.available_from <= :availableFrom)
                AND (:amenityCount=0 OR (SELECT count(DISTINCT pa.amenity) FROM property_amenities pa
                     WHERE pa.property_id=p.id AND pa.amenity IN (:amenities))=:amenityCount)
            ), ranked AS (
              SELECT *, %s AS selected_rank FROM candidates
            ), filtered AS (
              SELECT * FROM ranked WHERE match_rank=selected_rank
            )
            SELECT id,match_rank,count(*) OVER() total_count FROM filtered
            ORDER BY %s LIMIT :size OFFSET :offset
            """.formatted(matchCase, hasLocation ? "min(match_rank) OVER()" : "99", order);
        var statement = jdbc.sql(sql)
                .param("priceMin", priceMin).param("priceMax", priceMax).param("beds", beds).param("baths", baths)
                .param("squareFeetMin", squareFeetMin).param("squareFeetMax", squareFeetMax)
                .param("propertyType", propertyType == null || propertyType.equalsIgnoreCase("any") ? null : propertyType)
                .param("availableFrom", availableFrom).param("amenityCount", requiredAmenities.size())
                .param("amenities", requiredAmenities.isEmpty() ? Set.of("__none__") : requiredAmenities)
                .param("size", size).param("offset", (long) page * size);
        if (hasCoordinates) statement = statement.param("point", "SRID=4326;POINT(" + longitude + " " + latitude + ")");
        if (hasCity) statement = statement.param("city", city.trim());
        if (hasState) statement = statement.param("state", state.trim());
        List<SearchRow> rows = statement.query((rs, n) -> new SearchRow(rs.getLong("id"), rs.getInt("match_rank"), rs.getLong("total_count"))).list();
        long total = rows.isEmpty() ? 0 : rows.getFirst().total();
        int rank = rows.isEmpty() ? 0 : rows.getFirst().rank();
        var matchType = total == 0 ? PropertySearchResult.MatchType.NONE : !hasLocation ? PropertySearchResult.MatchType.ALL : switch (rank) {
            case 1 -> PropertySearchResult.MatchType.NEARBY;
            case 2 -> PropertySearchResult.MatchType.CITY;
            case 3 -> PropertySearchResult.MatchType.RADIUS_EXPANDED;
            case 4 -> PropertySearchResult.MatchType.STATE;
            default -> PropertySearchResult.MatchType.NONE;
        };
        Double radius = null;
        if (rank == 1) radius = 25d;
        else if (rank == 3) radius = 100d;
        String searched = location == null || location.isBlank() ? null : location.trim();
        return new PropertySearchResult(properties.byIds(rows.stream().map(SearchRow::id).toList()), matchType,
                searched, radius, total, page, size, (int) Math.ceil((double) total / size));
    }

    private record SearchRow(long id, int rank, long total) {}

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
        String stayType = oneOf(required(fields, "stayType"), "PayingGuest", "WholeUnit");
        String bathType = oneOf(required(fields, "bathType"), "Private", "SharedBath");
        String genderPreference = oneOf(stringSet(fields.get("genderPreference")).stream().findFirst().orElse("NoPreference"),
                "Male", "Female", "NoPreference");
        boolean petsAllowed = bool(fields.get("isPetsAllowed"));
        boolean parkingIncluded = bool(fields.get("isParkingIncluded"));
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
                VALUES(:managerId,:locationId,:name,:description,:stayType,:bathType,:genderPreference,:price,:deposit,:fee,
                    :pets,:parking,:beds,:baths,:squareFeet,:propertyType,:availableFrom,'PUBLISHED') RETURNING id
                """).param("managerId", managerId).param("locationId", locationId).param("name", name)
                .param("description", description).param("price", price).param("deposit", deposit).param("fee", fee)
                .param("stayType", stayType).param("bathType", bathType).param("genderPreference", genderPreference)
                .param("pets", petsAllowed).param("parking", parkingIncluded)
                .param("beds", stayType.equals("PayingGuest") ? 0 : beds).param("baths", baths).param("squareFeet", squareFeet)
                .param("propertyType", required(fields, "propertyType"))
                .param("availableFrom", date(fields.get("availableFrom"))).query(Long.class).single();
        jdbc.sql("""
                UPDATE properties SET pet_count=:petCount,pet_fee=:petFee,parking_fee=:parkingFee,smoking_included=:smoking
                WHERE id=:id
                """).param("petCount", petsAllowed ? optionalInteger(fields, "petCount", 0, 100) : null)
                .param("petFee", petsAllowed ? optionalDecimal(fields, "petFee", null) : null)
                .param("parkingFee", parkingIncluded ? optionalDecimal(fields, "parkingFee", null) : null)
                .param("smoking", bool(fields.get("smokingIncluded"))).param("id", propertyId).update();

        insertValues(propertyId, "property_amenities", "amenity", stringSet(fields.get("amenities")));
        insertValues(propertyId, "property_highlights", "highlight", stringSet(fields.get("highlights")));
        savePhotos(propertyId, fields, photos);
        return properties.get(propertyId);
    }

    @Transactional
    public PropertyView update(long propertyId, UUID managerId, Map<String, String> fields, List<MultipartFile> photos) {
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
                UPDATE properties SET name=:name,description=:description,stay_type=:stayType,bath_type=:bathType,
                    gender_preference=:genderPreference,price_per_month=:price,
                    security_deposit=:deposit,pets_allowed=:pets,parking_included=:parking,pet_count=:petCount,
                    pet_fee=:petFee,parking_fee=:parkingFee,smoking_included=:smoking,beds=:beds,baths=:baths,
                    square_feet=:squareFeet,property_type=:propertyType WHERE id=:id
                """).param("name", required(fields, "name")).param("description", required(fields, "description"))
                .param("price", decimal(fields, "pricePerMonth", false)).param("deposit", decimal(fields, "securityDeposit", true))
                .param("pets", bool(fields.get("isPetsAllowed"))).param("parking", bool(fields.get("isParkingIncluded")))
                .param("petCount", bool(fields.get("isPetsAllowed")) ? optionalInteger(fields, "petCount", 0, 100) : null)
                .param("petFee", bool(fields.get("isPetsAllowed")) ? optionalDecimal(fields, "petFee", null) : null)
                .param("parkingFee", bool(fields.get("isParkingIncluded")) ? optionalDecimal(fields, "parkingFee", null) : null)
                .param("smoking", bool(fields.get("smokingIncluded")))
                .param("stayType", oneOf(required(fields, "stayType"), "PayingGuest", "WholeUnit"))
                .param("bathType", oneOf(required(fields, "bathType"), "Private", "SharedBath"))
                .param("genderPreference", oneOf(stringSet(fields.get("genderPreference")).stream().findFirst().orElse("NoPreference"), "Male", "Female", "NoPreference"))
                .param("beds", "PayingGuest".equals(fields.get("stayType")) ? 0 : integer(fields, "beds", 0, 100)).param("baths", integer(fields, "baths", 0, 100))
                .param("squareFeet", integer(fields, "squareFeet", 1, Integer.MAX_VALUE))
                .param("propertyType", required(fields, "propertyType")).param("id", propertyId).update();
        jdbc.sql("DELETE FROM property_amenities WHERE property_id=:id").param("id", propertyId).update();
        insertValues(propertyId, "property_amenities", "amenity", stringSet(fields.get("amenities")));
        jdbc.sql("DELETE FROM property_photos WHERE property_id=:id").param("id", propertyId).update();
        savePhotos(propertyId, fields, photos);
        if (locationChanged) nearbyCache.invalidate(propertyId);
        return properties.get(propertyId);
    }

    private void requireManager(UUID managerId) {
        boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE user_id=:id)")
                .param("id", managerId).query(Boolean.class).single();
        if (!exists) throw ApiException.notFound("User not found");
    }

    private void insertValues(long propertyId, String table, String column, Set<String> values) {
        for (String value : values) jdbc.sql("INSERT INTO " + table + "(property_id," + column + ") VALUES(:propertyId,:value)")
                .param("propertyId", propertyId).param("value", value).update();
    }

    private void savePhotos(long propertyId, Map<String, String> fields, List<MultipartFile> photos) {
        List<String> existing = new ArrayList<>(jsonStringList(fields.get("existingPhotoUrls")));
        List<String> uploaded = new ArrayList<>();
        for (MultipartFile photo : photos == null ? List.<MultipartFile>of() : photos) {
            if (photo.isEmpty()) continue;
            if (photo.getContentType() == null || !photo.getContentType().startsWith("image/"))
                throw new IllegalArgumentException("Photos must be image files");
            if (photo.getSize() > 10 * 1024 * 1024)
                throw new IllegalArgumentException("Each photo must be 10 MB or smaller");
            try {
                uploaded.add("data:" + photo.getContentType() + ";base64," + Base64.getEncoder().encodeToString(photo.getBytes()));
            } catch (IOException ex) {
                throw new IllegalArgumentException("Could not read uploaded photo", ex);
            }
        }
        existing.forEach(url -> {
            if (!url.startsWith("data:image/")) throw new IllegalArgumentException("Saved photos must be images");
        });
        if (existing.isEmpty() && uploaded.isEmpty())
            throw new IllegalArgumentException("At least one property photo is required");
        if (existing.size() + uploaded.size() > 5)
            throw new IllegalArgumentException("You can upload a maximum of 5 property photos");

        List<String> ordered = new ArrayList<>();
        for (String item : jsonStringList(fields.get("photoOrder"))) {
            String[] parts = item.split(":", 2);
            try {
                int index = Integer.parseInt(parts[1]);
                String value = "existing".equals(parts[0]) ? existing.get(index) : uploaded.get(index);
                if (!ordered.contains(value)) ordered.add(value);
            } catch (RuntimeException ignored) {
                throw new IllegalArgumentException("photoOrder is invalid");
            }
        }
        if (ordered.isEmpty()) {
            ordered.addAll(existing);
            ordered.addAll(uploaded);
        }
        for (int order = 0; order < ordered.size(); order++) {
            jdbc.sql("INSERT INTO property_photos(property_id,url,display_order) VALUES(:propertyId,:url,:displayOrder)")
                    .param("propertyId", propertyId).param("url", ordered.get(order)).param("displayOrder", order).update();
        }
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
    private static Integer optionalInteger(Map<String, String> fields, String key, int min, int max) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(key + " must be a whole number"); }
    }
    private static String oneOf(String value, String... allowed) {
        if (Arrays.asList(allowed).contains(value)) return value;
        throw new IllegalArgumentException("Invalid option: " + value);
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

    private List<String> jsonStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("existingPhotoUrls is invalid");
        }
    }
}
