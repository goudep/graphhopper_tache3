package com.graphhopper.resources;

// JUnit 5 imports
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

// Mockito imports
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// GraphHopper imports
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.http.GHRequestTransformer;
import com.graphhopper.jackson.MultiException;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

// Jakarta imports (for HTTP responses)
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.servlet.http.HttpServletRequest;

// Java imports
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for Tâche 3.
 * This class tests the orchestration logic of RouteResource by isolating its dependencies.
 */
@ExtendWith(MockitoExtension.class)
public class RouteResourceMockTest {

    // --- Required Mocks ---

    @Mock
    private GraphHopper mockGraphHopper; // Mock 1: Main routing engine

    @Mock
    private ProfileResolver mockProfileResolver; // Mock 2: Profile resolution service

    @Mock
    private HttpServletRequest mockHttpServletRequest; // Mock 3: HTTP request

    @Mock
    private GraphHopperConfig mockGraphHopperConfig; // Mock 4: GraphHopper configuration

    @Mock
    private GHRequestTransformer mockGHRequestTransformer; // Mock 5: Request transformer

    @Mock
    private StorableProperties mockStorableProperties; // Mock 6: GraphHopper properties

    // --- Class under test ---
    private RouteResource routeResource;

    @BeforeEach
    public void setUp() {
        // Configure all mocks first, then create RouteResource instance
        
        // Configure GraphHopperConfig mock default behavior (must be done before creating RouteResource)
        when(mockGraphHopperConfig.getString(any(String.class), any(String.class))).thenAnswer(invocation -> {
            String defaultValue = invocation.getArgument(1);
            return defaultValue != null ? defaultValue : "";
        });
        // Use lenient() because some tests may not use getCopyrights()
        org.mockito.Mockito.lenient().when(mockGraphHopperConfig.getCopyrights()).thenReturn(List.of("GraphHopper", "OpenStreetMap contributors"));
        
        // Configure GraphHopper mock's getProperties() method
        when(mockGraphHopper.getProperties()).thenReturn(mockStorableProperties);
        when(mockStorableProperties.getAll()).thenReturn(new java.util.HashMap<>());
        
        // Configure GHRequestTransformer mock default behavior (returns original request)
        when(mockGHRequestTransformer.transformRequest(any(GHRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Configure HttpServletRequest mock default behavior
        when(mockHttpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(mockHttpServletRequest.getLocale()).thenReturn(java.util.Locale.ENGLISH);
        when(mockHttpServletRequest.getHeader("User-Agent")).thenReturn("Test-Agent");
        
        // Now create RouteResource instance
        routeResource = new RouteResource(
                mockGraphHopperConfig,
                mockGraphHopper,
                mockProfileResolver,
                mockGHRequestTransformer,
                false // hasElevation
        );
    }

    // --- Test Case 1: Happy Path Scenario ---

    @Test
    public void testRoutePost_HappyPath() {
        // 1. ARRANGE (define mocks and test data)
        GHRequest request = new GHRequest(List.of(new GHPoint(40, -74), new GHPoint(40.1, -74.1)));
        request.setProfile("car");

        GHResponse fakeResponse = new GHResponse();
        ResponsePath fakePath = new ResponsePath();
        fakePath.setDistance(1000.0); 
        fakePath.setTime(120000); 
        
        PointList fakePoints = new PointList();
        fakePoints.add(new GHPoint(40, -74));
        fakePoints.add(new GHPoint(40.1, -74.1));
        fakePath.setPoints(fakePoints);
        
        // Set empty InstructionList to avoid exception when accessing instructions
        InstructionList instructions = new InstructionList(new TranslationMap().doImport().get("en"));
        fakePath.setInstructions(instructions);
        
        fakeResponse.add(fakePath);
        
        // Define mock behaviors
        when(mockProfileResolver.resolveProfile(any())).thenReturn("car");
        when(mockGraphHopper.route(any(GHRequest.class))).thenReturn(fakeResponse);

        // 2. ACT (execute the method under test)
        Response httpResponse = routeResource.doPost(request, mockHttpServletRequest);

        // 3. ASSERT (verify results)
        assertEquals(200, httpResponse.getStatus(), "HTTP response status should be 200 (OK)");
        assertTrue(httpResponse.hasEntity(), "Response should contain entity");
        
        // Note: doPost returns a JSON object, not GHResponse
        // We need to verify response status and content type
        assertEquals(MediaType.APPLICATION_JSON, httpResponse.getMediaType().toString(), "Response type should be JSON");
    }

    /**
     * Test 2: Verifies that when GraphHopper service returns a response containing errors
     * (e.g., point not found), RouteResource handles the error and throws MultiException.
     */
    @Test
    public void testRoutePost_ErrorPath_PointNotFound() {
        // 1. ARRANGE (define mocks)
        GHRequest request = new GHRequest(List.of(new GHPoint(0, 0), new GHPoint(1, 1)));
        request.setProfile("car");

        // Create a GHResponse containing an error
        PointNotFoundException pointNotFoundError = new PointNotFoundException("Simulated Error: Point 0 not found.", 0);
        GHResponse errorResponse = new GHResponse();
        errorResponse.addError(pointNotFoundError);

        // Define mock behaviors
        when(mockProfileResolver.resolveProfile(any())).thenReturn("car");
        when(mockGraphHopper.route(any(GHRequest.class))).thenReturn(errorResponse);

        // 2. ACT & ASSERT (execute the method under test and verify exception)
        MultiException exception = org.junit.jupiter.api.Assertions.assertThrows(
                MultiException.class,
                () -> routeResource.doPost(request, mockHttpServletRequest),
                "doPost should throw MultiException when response contains errors"
        );

        // 3. ASSERT (verify exception content)
        assertNotNull(exception.getErrors(), "Exception should contain error list");
        assertFalse(exception.getErrors().isEmpty(), "Error list should not be empty");
        assertEquals(1, exception.getErrors().size(), "Should have one error");
        
        Throwable firstError = exception.getErrors().get(0);
        assertTrue(firstError instanceof PointNotFoundException, "First error should be PointNotFoundException");
        assertEquals("Simulated Error: Point 0 not found.", firstError.getMessage(), "Error message should match");
        
        PointNotFoundException pnf = (PointNotFoundException) firstError;
        assertEquals(0, pnf.getPointIndex(), "Point index should be 0");
    }
}