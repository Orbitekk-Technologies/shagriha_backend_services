package com.orbitekk.shagriha.location;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyReader;
import com.orbitekk.shagriha.property.PropertyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NearbyPlacesServiceTests {
    private PropertyReader properties;
    private MapboxClient mapbox;
    private NearbyPlacesCache cache;
    private NearbyPlacesService service;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyReader.class);
        mapbox = mock(MapboxClient.class);
        cache = mock(NearbyPlacesCache.class);
        service = new NearbyPlacesService(properties, mapbox, cache);
    }

    @Test
    void returnsCachedResultWithoutCallingMapbox() {
        var expected = NearbyPlacesResponseDto.empty();
        when(properties.get(7)).thenReturn(property(7, -96.8, 32.8));
        when(cache.get(7)).thenReturn(Optional.of(expected));
        assertSame(expected, service.get(7));
        verifyNoInteractions(mapbox);
    }

    @Test
    void cacheMissQueriesCategoriesNormalizesNearestAndCaches() {
        when(properties.get(7)).thenReturn(property(7, -96.8, 32.8));
        when(cache.get(7)).thenReturn(Optional.empty());
        when(mapbox.searchCategory(anyString(), anyDouble(), anyDouble())).thenReturn(List.of(
                new MapboxClient.Candidate("Far", -96.7, 32.9),
                new MapboxClient.Candidate("Near", -96.799, 32.801)));

        var result = service.get(7);

        assertEquals("Near", result.groceries().getFirst().name());
        assertTrue(result.groceries().getFirst().distanceMeters() > 0);
        assertEquals("GROCERY", result.groceries().getFirst().category());
        verify(mapbox, times(5)).searchCategory(anyString(), eq(-96.8), eq(32.8));
        verify(cache).put(eq(7L), same(result));
    }

    @Test
    void mapboxFailureReturnsUnavailableAndDoesNotCacheFailure() {
        when(properties.get(7)).thenReturn(property(7, -96.8, 32.8));
        when(cache.get(7)).thenReturn(Optional.empty());
        when(mapbox.searchCategory(anyString(), anyDouble(), anyDouble())).thenThrow(new RuntimeException("unavailable"));
        ApiException error = assertThrows(ApiException.class, () -> service.get(7));
        assertEquals(503, error.status().value());
        verify(cache, never()).put(anyLong(), any());
    }

    @Test
    void propertyNotFoundIsPreserved() {
        when(properties.get(99)).thenThrow(ApiException.notFound("Property not found"));
        assertThrows(ApiException.class, () -> service.get(99));
        verifyNoInteractions(cache, mapbox);
    }

    @Test
    void missingCoordinatesAreRejectedBeforeCacheOrMapbox() {
        when(properties.get(7)).thenReturn(property(7, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.get(7));
        verifyNoInteractions(cache, mapbox);
    }

    @Test
    void calculatesGeographicDistanceInMeters() {
        double distance = NearbyPlacesService.distanceMeters(32.7767, -96.7970, 32.7867, -96.7970);
        assertEquals(1112, distance, 2);
    }

    private static PropertyView property(long id, double longitude, double latitude) {
        var location = new PropertyView.LocationView(id, "1 Main St", "1 Main St", null,
                "Dallas", "Texas", "Texas", "TX", "United States", "United States", "US",
                "75201", "1 Main St, Dallas, TX 75201", "address.test",
                new PropertyView.Coordinates(longitude, latitude));
        return new PropertyView(id, "Home", "Description", BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), List.of(), List.of(), false, false, 1, 1, 500,
                "Apartment", Instant.now(), 0, 0, id, UUID.randomUUID(), location, null);
    }
}
