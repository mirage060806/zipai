package com.onrender.zipai.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CollectionHistoryServiceTest {
    @Test void statusUsesKnownValuesAndKeepsUnknownDistinct() {
        assertEquals("completed",CollectionHistoryService.normalizeStatus("SUCCESS","done"));
        assertEquals("partial",CollectionHistoryService.normalizeStatus("PARTIAL","done"));
        assertEquals("failed",CollectionHistoryService.normalizeStatus("FAILED","done"));
        assertEquals("running",CollectionHistoryService.normalizeStatus("RUNNING",null));
        assertEquals("unknown",CollectionHistoryService.normalizeStatus("unrecognized","done"));
    }
}
