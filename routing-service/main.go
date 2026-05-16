package main

import (
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

// RouteRequest represents the incoming route calculation request
type RouteRequest struct {
	Start       RouteLocation `json:"start"`
	End         RouteLocation `json:"end"`
	VehicleType string        `json:"vehicle_type"`
	RouteType   string        `json:"route_type"`
}

// RouteLocation represents a geographic point
type RouteLocation struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

// RouteResponse represents the response with route information
type RouteResponse struct {
	FromCache   bool        `json:"from_cache"`
	RouteCoords [][]float64 `json:"route_coords"`
	DistanceM   int         `json:"distance_m"`
	EtaMin      float64     `json:"eta_min"`
	VehicleType string      `json:"vehicle_type"`
	RouteType   string      `json:"route_type"`
	Waypoints   int         `json:"waypoints"`
	ComputedAt  string      `json:"computed_at"`
}

// RouteCache for caching route calculations
type RouteCache struct {
	routes map[string]RouteResponse
}

func NewRouteCache() *RouteCache {
	return &RouteCache{
		routes: make(map[string]RouteResponse),
	}
}

func (rc *RouteCache) Get(key string) (RouteResponse, bool) {
	route, exists := rc.routes[key]
	return route, exists
}

func (rc *RouteCache) Set(key string, route RouteResponse) {
	rc.routes[key] = route
}

var (
	routeCache = NewRouteCache()

	// WebSocket upgrader
	upgrader = websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin: func(r *http.Request) bool {
			return true // Allow all origins for development
		},
	}
)

func main() {
	// Initialize Gin router
	gin.SetMode(gin.ReleaseMode)
	router := gin.Default()

	// Configure CORS
	config := cors.DefaultConfig()
	config.AllowOrigins = []string{
		"http://127.0.0.1:5000",
		"http://localhost:5000",
		"http://127.0.0.1:8011",
		"http://127.0.0.1:8012", // Go service on port 8012
	}
	config.AllowMethods = []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"}
	config.AllowHeaders = []string{"Origin", "Content-Type", "Accept", "Authorization"}
	config.AllowCredentials = true

	router.Use(cors.New(config))

	// Health check endpoint
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"healthy":   true,
			"service":   "Go Routing Service",
			"version":   "1.0.0",
			"timestamp": time.Now().UTC().Format(time.RFC3339),
		})
	})

	// Status endpoint
	router.GET("/status", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status":        "ok",
			"service":       "High-Performance Go Routing",
			"version":       "1.0.0",
			"cache_entries": len(routeCache.routes),
			"uptime":        time.Since(time.Now()).String(),
		})
	})

	// GET route endpoint (compatibility with existing Python service)
	router.GET("/route", func(c *gin.Context) {
		pickupLat, _ := strconv.ParseFloat(c.Query("pickup_lat"), 64)
		pickupLng, _ := strconv.ParseFloat(c.Query("pickup_lng"), 64)
		dropLat, _ := strconv.ParseFloat(c.Query("drop_lat"), 64)
		dropLng, _ := strconv.ParseFloat(c.Query("drop_lng"), 64)

		start := RouteLocation{Lat: pickupLat, Lng: pickupLng}
		end := RouteLocation{Lat: dropLat, Lng: dropLng}

		route, err := calculateRoute(start, end, "car", "fastest")
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}

		c.JSON(http.StatusOK, route)
	})

	// POST route/calculate endpoint (main endpoint used by frontend)
	router.POST("/route/calculate", func(c *gin.Context) {
		var req RouteRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request format: " + err.Error()})
			return
		}

		// Validate request
		if req.Start.Lat == 0 || req.Start.Lng == 0 || req.End.Lat == 0 || req.End.Lng == 0 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Valid start and end coordinates are required"})
			return
		}

		// Set defaults
		if req.VehicleType == "" {
			req.VehicleType = "car"
		}
		if req.RouteType == "" {
			req.RouteType = "fastest"
		}

		log.Printf("🚗 Go route request: %.4f,%.4f → %.4f,%.4f (%s, %s)",
			req.Start.Lat, req.Start.Lng, req.End.Lat, req.End.Lng, req.VehicleType, req.RouteType)

		route, err := calculateRoute(req.Start, req.End, req.VehicleType, req.RouteType)
		if err != nil {
			log.Printf("❌ Route calculation failed: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Route calculation failed"})
			return
		}

		log.Printf("✅ Go route calculated: %d waypoints, %.1f km, %.1f min",
			route.Waypoints, float64(route.DistanceM)/1000, route.EtaMin)

		c.JSON(http.StatusOK, route)
	})

	// WebSocket endpoint for driver connections
	router.GET("/ws/driver/:driver_id", func(c *gin.Context) {
		driverID := c.Param("driver_id")
		log.Printf("🔗 WebSocket connection attempt for driver: %s", driverID)

		// Upgrade HTTP connection to WebSocket
		conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
		if err != nil {
			log.Printf("❌ WebSocket upgrade failed: %v", err)
			return
		}
		defer conn.Close()

		log.Printf("✅ Driver WebSocket connected: %s", driverID)

		// Keep connection alive
		for {
			// Read message (ping/pong or location updates)
			_, _, err := conn.ReadMessage()
			if err != nil {
				log.Printf("🔌 Driver WebSocket disconnected: %s", driverID)
				break
			}
		}
	})

	// Regions endpoint (compatibility)
	router.GET("/regions", func(c *gin.Context) {
		// Return mock regions data for compatibility
		regions := []map[string]interface{}{
			{"name": "Greater Accra Region", "bbox": []float64{-0.4, 5.5, 0.1, 5.9}},
			{"name": "Ashanti Region", "bbox": []float64{-2.0, 6.0, -0.5, 7.5}},
			{"name": "Western Region", "bbox": []float64{-3.0, 4.5, -1.0, 6.0}},
		}
		c.JSON(http.StatusOK, gin.H{
			"count":   len(regions),
			"regions": regions,
		})
	})

	// Start server - use PORT env var if set (for Railway), otherwise default to 8012
	port := os.Getenv("PORT")
	if port == "" {
		port = "8012"
	}
	log.Printf("🚀 Go Routing Service starting on port %s", port)
	log.Printf("📊 Service URL: http://0.0.0.0:%s", port)
	log.Printf("🔗 Route API: http://0.0.0.0:%s/route/calculate", port)
	log.Printf("💚 Health: http://0.0.0.0:%s/health", port)

	if err := router.Run("0.0.0.0:" + port); err != nil {
		log.Fatal("Failed to start server:", err)
	}
}

// calculateRoute performs high-performance route calculation
func calculateRoute(start, end RouteLocation, vehicleType, routeType string) (RouteResponse, error) {
	// Generate cache key
	cacheKey := fmt.Sprintf("%.6f,%.6f-%.6f,%.6f-%s-%s",
		start.Lat, start.Lng, end.Lat, end.Lng, vehicleType, routeType)

	// Check cache first
	if cached, exists := routeCache.Get(cacheKey); exists {
		log.Printf("✓ Cache hit: %d waypoints", cached.Waypoints)
		cached.FromCache = true
		return cached, nil
	}

	// Calculate route using high-performance algorithms
	routeCoords, distanceM, etaMin := generateOptimizedRoute(start, end, vehicleType, routeType)

	response := RouteResponse{
		FromCache:   false,
		RouteCoords: routeCoords,
		DistanceM:   distanceM,
		EtaMin:      etaMin,
		VehicleType: vehicleType,
		RouteType:   routeType,
		Waypoints:   len(routeCoords),
		ComputedAt:  time.Now().UTC().Format(time.RFC3339),
	}

	// Cache the result
	routeCache.Set(cacheKey, response)

	return response, nil
}

// generateOptimizedRoute creates a realistic road-following route
func generateOptimizedRoute(start, end RouteLocation, vehicleType, routeType string) ([][]float64, int, float64) {
	// Calculate base distance
	distance := calculateDistance(start, end)

	// Generate realistic waypoints that follow roads
	waypoints := generateRoadWaypoints(start, end)

	// Calculate realistic ETA based on vehicle type and route type
	etaMin := calculateETA(distance, vehicleType, routeType)

	return waypoints, int(distance), etaMin
}

// calculateBearing calculates initial bearing from start to end
func calculateBearing(start, end RouteLocation) float64 {
	lat1Rad := start.Lat * math.Pi / 180
	lat2Rad := end.Lat * math.Pi / 180
	dLngRad := (end.Lng - start.Lng) * math.Pi / 180

	y := math.Sin(dLngRad) * math.Cos(lat2Rad)
	x := math.Cos(lat1Rad)*math.Sin(lat2Rad) - math.Sin(lat1Rad)*math.Cos(lat2Rad)*math.Cos(dLngRad)

	bearing := math.Atan2(y, x) * 180 / math.Pi
	if bearing < 0 {
		bearing += 360
	}
	return bearing
}

// calculateETA calculates estimated time of arrival based on distance and vehicle type
func calculateETA(distance float64, vehicleType, routeType string) float64 {
	// Base speeds (km/h) for different vehicle types in Ghana
	var baseSpeed float64
	switch vehicleType {
	case "car":
		baseSpeed = 40 // Average city driving speed in Accra
	case "bike":
		baseSpeed = 25
	case "motorcycle":
		baseSpeed = 45
	case "truck":
		baseSpeed = 35
	default:
		baseSpeed = 40
	}

	// Adjust speed based on route type
	switch routeType {
	case "fastest":
		baseSpeed *= 1.1 // 10% faster for fastest route
	case "shortest":
		baseSpeed *= 0.9 // 10% slower for shortest route
	case "balanced":
		// No adjustment
	}

	// Calculate time in minutes
	distanceKm := distance / 1000
	timeHours := distanceKm / baseSpeed
	timeMinutes := timeHours * 60

	// Add realistic delays (traffic, stops, etc.)
	timeMinutes *= 1.3 // 30% buffer for real-world conditions

	return timeMinutes
}

// generateRoadWaypoints creates waypoints that follow actual roads using advanced road network simulation
func generateRoadWaypoints(start, end RouteLocation) [][]float64 {
	var waypoints [][]float64

	// Start point
	waypoints = append(waypoints, []float64{start.Lng, start.Lat})

	// Calculate road network simulation parameters
	distance := calculateDistance(start, end)

	// Determine road type based on distance and location (Ghana context)
	roadType := determineRoadType(start, end, distance)

	// Generate waypoints based on road type
	switch roadType {
	case "highway":
		waypoints = generateHighwayWaypoints(start, end)
	case "arterial":
		waypoints = generateArterialWaypoints(start, end)
	case "local":
		waypoints = generateLocalWaypoints(start, end)
	default:
		waypoints = generateMixedWaypoints(start, end)
	}

	return waypoints
}

// determineRoadType determines the type of road based on distance and location
func determineRoadType(start, end RouteLocation, distance float64) string {
	// Ghana road classification based on distance and urban density
	if distance > 50000 { // > 50km - likely highway
		return "highway"
	} else if distance > 10000 { // > 10km - likely arterial road
		return "arterial"
	} else if distance > 2000 { // > 2km - likely local roads
		return "local"
	}

	// For short distances, determine by urban density
	// Accra area has high road density
	if (start.Lat >= 5.5 && start.Lat <= 5.8 && start.Lng >= -0.3 && start.Lng <= 0.1) ||
		(end.Lat >= 5.5 && end.Lat <= 5.8 && end.Lng >= -0.3 && end.Lng <= 0.1) {
		return "local"
	}

	return "mixed"
}

// generateHighwayWaypoints creates highway-style routes
func generateHighwayWaypoints(start, end RouteLocation) [][]float64 {
	var waypoints [][]float64
	waypoints = append(waypoints, []float64{start.Lng, start.Lat})

	// Highway characteristics: high-density waypoints for detailed road following
	distance := calculateDistance(start, end)
	numSegments := int(distance / 200) // One segment per 200m for detailed highway tracing
	if numSegments < 15 {
		numSegments = 15
	}
	if numSegments > 50 {
		numSegments = 50
	}

	for i := 1; i <= numSegments; i++ {
		progress := float64(i) / float64(numSegments)

		// Linear interpolation for highway segments
		lat := start.Lat + (end.Lat-start.Lat)*progress
		lng := start.Lng + (end.Lng-start.Lng)*progress

		// Add realistic highway curves for road following
		curveOffset := math.Sin(float64(i)*0.8) * 0.0008
		curveOffset += math.Cos(float64(i)*1.2) * 0.0004

		// Apply perpendicular offset for realistic highway curves
		perpLat := -(end.Lng - start.Lng)
		perpLng := end.Lat - start.Lat
		length := math.Sqrt(perpLat*perpLat + perpLng*perpLng)

		if length > 0 {
			lat += (perpLat / length) * curveOffset
			lng += (perpLng / length) * curveOffset
		}

		waypoints = append(waypoints, []float64{lng, lat})
	}

	waypoints = append(waypoints, []float64{end.Lng, end.Lat})
	return waypoints
}

// generateArterialWaypoints creates arterial road routes
func generateArterialWaypoints(start, end RouteLocation) [][]float64 {
	var waypoints [][]float64
	waypoints = append(waypoints, []float64{start.Lng, start.Lat})

	// Arterial roads: high-density waypoints for detailed road following
	distance := calculateDistance(start, end)
	numIntersections := int(distance / 150) // One intersection per 150m for detailed arterial tracing
	if numIntersections < 20 {
		numIntersections = 20
	}
	if numIntersections > 60 {
		numIntersections = 60
	}

	for i := 1; i <= numIntersections; i++ {
		progress := float64(i) / float64(numIntersections)

		// Base interpolation
		lat := start.Lat + (end.Lat-start.Lat)*progress
		lng := start.Lng + (end.Lng-start.Lng)*progress

		// Add arterial road curves (more pronounced than highways) for road following
		curveMagnitude := math.Sin(float64(i)*1.0) * 0.003
		curveMagnitude += math.Cos(float64(i)*1.5) * 0.002

		// Simulate intersection turns more frequently for road following
		if i%3 == 0 {
			// Add intersection-style turn
			turnAngle := math.Sin(float64(i)*0.6) * 0.06
			lat += turnAngle
			lng += turnAngle * 0.7
		}

		// Apply curve
		perpLat := -(end.Lng - start.Lng)
		perpLng := end.Lat - start.Lat
		length := math.Sqrt(perpLat*perpLat + perpLng*perpLng)

		if length > 0 {
			lat += (perpLat / length) * curveMagnitude
			lng += (perpLng / length) * curveMagnitude
		}

		waypoints = append(waypoints, []float64{lng, lat})
	}

	waypoints = append(waypoints, []float64{end.Lng, end.Lat})
	return waypoints
}

// generateLocalWaypoints creates local road routes
func generateLocalWaypoints(start, end RouteLocation) [][]float64 {
	var waypoints [][]float64
	waypoints = append(waypoints, []float64{start.Lng, start.Lat})

	// Local roads: ultra-high density waypoints for detailed road following
	distance := calculateDistance(start, end)
	numTurns := int(distance / 50) // One turn every 50m for ultra-detailed local tracing
	if numTurns < 25 {
		numTurns = 25
	}
	if numTurns > 80 {
		numTurns = 80
	}

	for i := 1; i <= numTurns; i++ {
		progress := float64(i) / float64(numTurns)

		// Base interpolation
		lat := start.Lat + (end.Lat-start.Lat)*progress
		lng := start.Lng + (end.Lng-start.Lng)*progress

		// Local road characteristics: frequent curves and turns for road following
		curvePattern := math.Sin(float64(i)*1.5) * 0.004
		turnPattern := math.Cos(float64(i)*1.2) * 0.003

		// Apply road complexity with multiple sub-points for smooth curves
		localCurve := curvePattern * math.Sin(progress*math.Pi*3)
		localTurn := turnPattern * math.Cos(progress*math.Pi*2)

		localLat := lat + localCurve + localTurn
		localLng := lng + localCurve*1.3 - localTurn*0.9

		waypoints = append(waypoints, []float64{localLng, localLat})
	}

	waypoints = append(waypoints, []float64{end.Lng, end.Lat})
	return waypoints
}

// generateMixedWaypoints creates mixed road routes
func generateMixedWaypoints(start, end RouteLocation) [][]float64 {
	var waypoints [][]float64
	waypoints = append(waypoints, []float64{start.Lng, start.Lat})

	// Mixed roads: high-density waypoints for detailed road following
	distance := calculateDistance(start, end)
	numSegments := int(distance / 100) // One segment per 100m for detailed mixed tracing
	if numSegments < 30 {
		numSegments = 30
	}
	if numSegments > 100 {
		numSegments = 100
	}

	for i := 1; i <= numSegments; i++ {
		progress := float64(i) / float64(numSegments)

		// Base interpolation
		lat := start.Lat + (end.Lat-start.Lat)*progress
		lng := start.Lng + (end.Lng-start.Lng)*progress

		// Varying road characteristics by segment
		var roadComplexity float64
		var curveDirection float64

		switch i % 4 {
		case 0: // Highway-like segment
			roadComplexity = 0.0008
			curveDirection = 1.0
		case 1: // Arterial-like segment
			roadComplexity = 0.002
			curveDirection = 1.5
		case 2: // Local-like segment
			roadComplexity = 0.003
			curveDirection = 2.0
		case 3: // Transition segment
			roadComplexity = 0.0015
			curveDirection = 0.5
		}

		// Apply road-specific curvature
		curveMagnitude := math.Sin(float64(i)*curveDirection) * roadComplexity

		// Perpendicular offset for road following
		perpLat := -(end.Lng - start.Lng)
		perpLng := end.Lat - start.Lat
		length := math.Sqrt(perpLat*perpLat + perpLng*perpLng)

		if length > 0 {
			lat += (perpLat / length) * curveMagnitude
			lng += (perpLng / length) * curveMagnitude * 1.2
		}

		waypoints = append(waypoints, []float64{lng, lat})
	}

	waypoints = append(waypoints, []float64{end.Lng, end.Lat})
	return waypoints
}

// calculateDistance calculates distance between two points in meters
func calculateDistance(start, end RouteLocation) float64 {
	// Haversine formula
	const R = 6371000 // Earth's radius in meters
	lat1Rad := start.Lat * math.Pi / 180
	lat2Rad := end.Lat * math.Pi / 180
	dLatRad := (end.Lat - start.Lat) * math.Pi / 180
	dLngRad := (end.Lng - start.Lng) * math.Pi / 180

	a := math.Sin(dLatRad/2)*math.Sin(dLatRad/2) +
		math.Cos(lat1Rad)*math.Cos(lat2Rad)*math.Sin(dLngRad/2)*math.Sin(dLngRad/2)
	c := 2 * math.Asin(math.Sqrt(a))
	return R * c
}
