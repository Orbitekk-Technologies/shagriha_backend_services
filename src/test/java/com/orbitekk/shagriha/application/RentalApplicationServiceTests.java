package com.orbitekk.shagriha.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RentalApplicationServiceTests {
    @Test
    void rejectsUnknownStatusBeforeAccessingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> RentalApplicationService.normalizeStatus("cancelled"));
        assertEquals("APPROVED", RentalApplicationService.normalizeStatus("Approved"));
    }
}
