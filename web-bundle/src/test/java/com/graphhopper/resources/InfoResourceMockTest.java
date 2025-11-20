package com.graphhopper.resources;

// JUnit 5 imports
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Mockito imports
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// GraphHopper imports
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.shapes.BBox;

// Java imports
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Tâche 3 - Second class under test.
 * This class tests InfoResource by isolating its dependencies with Mockito.
 * 
 * Rationale for choosing InfoResource:
 * - InfoResource is a simpler resource class that provides system information
 * - It has clear dependencies (GraphHopperConfig, GraphHopper, EncodingManager, BaseGraph, StorableProperties)
 * - It's a good complement to RouteResource tests, showing different types of resource classes
 */
@ExtendWith(MockitoExtension.class)
public class InfoResourceMockTest {

    // --- Required Mocks ---

    @Mock
    private GraphHopperConfig mockGraphHopperConfig; // Mock 1: Configuration

    @Mock
    private GraphHopper mockGraphHopper; // Mock 2: Main GraphHopper instance

    @Mock
    private EncodingManager mockEncodingManager; // Mock 3: Encoding manager for encoded values

    @Mock
    private BaseGraph mockBaseGraph; // Mock 4: Base graph for bounds

    @Mock
    private StorableProperties mockStorableProperties; // Mock 5: Properties for dates

    // --- Class under test ---
    private InfoResource infoResource;

    @BeforeEach
    public void setUp() {
        // Configure GraphHopper mock to return other mocks
        when(mockGraphHopper.getEncodingManager()).thenReturn(mockEncodingManager);
        when(mockGraphHopper.getBaseGraph()).thenReturn(mockBaseGraph);
        when(mockGraphHopper.getProperties()).thenReturn(mockStorableProperties);

        // Configure default GraphHopperConfig behavior
        org.mockito.Mockito.lenient().when(mockGraphHopperConfig.getString(any(String.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(1)); // Return default value

        // Configure default BaseGraph bounds
        BBox bounds = new BBox(40.0, 41.0, -74.0, -73.0);
        org.mockito.Mockito.lenient().when(mockBaseGraph.getBounds()).thenReturn(bounds);

        // Configure default StorableProperties
        org.mockito.Mockito.lenient().when(mockStorableProperties.get("datareader.import.date"))
                .thenReturn("2024-01-01T00:00:00Z");
        org.mockito.Mockito.lenient().when(mockStorableProperties.get("datareader.data.date"))
                .thenReturn("2024-01-15T00:00:00Z");

        // Configure default EncodingManager (empty list by default)
        org.mockito.Mockito.lenient().when(mockEncodingManager.getEncodedValues())
                .thenReturn(new ArrayList<>());
        org.mockito.Mockito.lenient().when(mockEncodingManager.hasEncodedValue(any(String.class)))
                .thenReturn(false);

        // Create InfoResource instance
        infoResource = new InfoResource(mockGraphHopperConfig, mockGraphHopper, false);
    }

    /**
     * Test 1: Verifies that InfoResource correctly retrieves and returns system information
     * including bounds, profiles, elevation status, and dates.
     * 
     * Classes mocked:
     * - GraphHopperConfig: Provides profiles configuration
     * - BaseGraph: Provides graph bounds
     * - StorableProperties: Provides import and data dates
     * 
     * Simulated values:
     * - Bounds: (40.0, 41.0, -74.0, -73.0)
     * - Profiles: "car" and "bike"
     * - Elevation: false
     * - Import date: "2024-01-01T00:00:00Z"
     * - Data date: "2024-01-15T00:00:00Z"
     */
    @Test
    public void testGetInfo_ReturnsCompleteSystemInformation() {
        // 1. ARRANGE
        // Configure profiles
        Profile carProfile = new Profile("car");
        Profile bikeProfile = new Profile("bike");
        when(mockGraphHopperConfig.getProfiles()).thenReturn(List.of(carProfile, bikeProfile));

        // Configure bounds
        BBox bounds = new BBox(40.0, 41.0, -74.0, -73.0);
        when(mockBaseGraph.getBounds()).thenReturn(bounds);

        // Configure properties
        when(mockStorableProperties.get("datareader.import.date")).thenReturn("2024-01-01T00:00:00Z");
        when(mockStorableProperties.get("datareader.data.date")).thenReturn("2024-01-15T00:00:00Z");

        // 2. ACT
        InfoResource.Info result = infoResource.getInfo();

        // 3. ASSERT
        assertNotNull(result, "Info should not be null");
        assertNotNull(result.bbox, "Bounding box should not be null");
        assertEquals(40.0, result.bbox.getMinX(), 0.0001, "Min longitude should match");
        assertEquals(41.0, result.bbox.getMaxX(), 0.0001, "Max longitude should match");
        assertEquals(-74.0, result.bbox.getMinY(), 0.0001, "Min latitude should match");
        assertEquals(-73.0, result.bbox.getMaxY(), 0.0001, "Max latitude should match");

        assertEquals(2, result.profiles.size(), "Should have 2 profiles");
        assertEquals("car", result.profiles.get(0).name, "First profile should be 'car'");
        assertEquals("bike", result.profiles.get(1).name, "Second profile should be 'bike'");

        assertFalse(result.elevation, "Elevation should be false");
        assertEquals("2024-01-01T00:00:00Z", result.import_date, "Import date should match");
        assertEquals("2024-01-15T00:00:00Z", result.data_date, "Data date should match");

        // Verify interactions
        verify(mockGraphHopperConfig, times(1)).getProfiles();
        // getBounds() is called 4 times in InfoResource.getInfo() (once for each property: minLon, maxLon, minLat, maxLat)
        verify(mockBaseGraph, times(4)).getBounds();
        verify(mockStorableProperties, times(1)).get("datareader.import.date");
        verify(mockStorableProperties, times(1)).get("datareader.data.date");
    }

    /**
     * Test 2: Verifies that InfoResource correctly handles elevation flag.
     * 
     * Classes mocked:
     * - GraphHopperConfig: Provides profiles configuration
     * 
     * Simulated values:
     * - Elevation: true (different from default false)
     * - Profiles: "car"
     */
    @Test
    public void testGetInfo_HandlesElevationFlag() {
        // 1. ARRANGE
        Profile carProfile = new Profile("car");
        when(mockGraphHopperConfig.getProfiles()).thenReturn(List.of(carProfile));

        // Recreate InfoResource with elevation = true
        infoResource = new InfoResource(mockGraphHopperConfig, mockGraphHopper, true);

        // 2. ACT
        InfoResource.Info result = infoResource.getInfo();

        // 3. ASSERT
        assertTrue(result.elevation, "Elevation should be true when hasElevation is true");

        // Verify interactions
        verify(mockGraphHopperConfig, times(1)).getProfiles();
    }

    /**
     * Test 3: Verifies that InfoResource correctly adds "pt" profile when GTFS file is configured.
     * 
     * Classes mocked:
     * - GraphHopperConfig: Provides profiles and GTFS configuration
     * 
     * Simulated values:
     * - Profiles: "car"
     * - GTFS file: configured (has("gtfs.file") returns true)
     * - Expected: "pt" profile should be added to the list
     */
    @Test
    public void testGetInfo_AddsPtProfileWhenGtfsConfigured() {
        // 1. ARRANGE
        Profile carProfile = new Profile("car");
        when(mockGraphHopperConfig.getProfiles()).thenReturn(List.of(carProfile));
        when(mockGraphHopperConfig.has("gtfs.file")).thenReturn(true);

        // 2. ACT
        InfoResource.Info result = infoResource.getInfo();

        // 3. ASSERT
        assertEquals(2, result.profiles.size(), "Should have 2 profiles (car + pt)");
        assertEquals("car", result.profiles.get(0).name, "First profile should be 'car'");
        assertEquals("pt", result.profiles.get(1).name, "Second profile should be 'pt'");

        // Verify interactions
        verify(mockGraphHopperConfig, times(1)).getProfiles();
        verify(mockGraphHopperConfig, times(1)).has("gtfs.file");
    }
}

