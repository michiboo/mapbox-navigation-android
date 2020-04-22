package com.mapbox.navigation.core.replay.route2

import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement

internal class ReplayRouteDriver {

    private val routeSmoother = ReplayRouteSmoother()
    private val routeInterpolator = ReplayRouteInterpolator()

    private var timeMillis = 0L

    fun driveGeometry(geometry: String): List<ReplayRouteLocation> {
        val coordinates = LineString.fromPolyline(geometry, 6).coordinates()
        if (coordinates.size < 3) return emptyList()

        val smoothLocations = routeInterpolator.createSpeedProfile(coordinates)
        val replayRouteLocations = mutableListOf<ReplayRouteLocation>()
        val firstLocation = smoothLocations[0]
        replayRouteLocations.addLocation(firstLocation)

        for (i in 1 until smoothLocations.size) {
            val segmentStart = smoothLocations[i - 1]
            val segmentEnd = smoothLocations[i]
            val segment = routeInterpolator.interpolateSpeed(
                segmentStart.speedMps,
                segmentEnd.speedMps,
                segmentStart.distance
            )

            val segmentRoute = routeSmoother.segmentRoute(coordinates, segmentStart.routeIndex!!, segmentEnd.routeIndex!!)
            for (stepIndex in 1 until segment.steps.size) {
                val step = segment.steps[stepIndex]
                val point = TurfMeasurement.along(LineString.fromLngLats(segmentRoute), step.positionMeters, TurfConstants.UNIT_METERS)
                val location = ReplayRouteLocation(null, point)
                location.speedMps = step.speedMps
                replayRouteLocations.addLocation(location)
            }
        }

        // Separately create a bearing profile so the location can look-head and start turning early
        routeInterpolator.createBearingProfile(replayRouteLocations)

        return replayRouteLocations
    }

    fun driveRouteLeg(routeLeg: RouteLeg): List<ReplayRouteLocation> {
        val replayRouteLocations = mutableListOf<ReplayRouteLocation>()
        val points = mutableListOf<Point>()
        routeLeg.steps()?.forEach { legStep ->
            val geometry = legStep.geometry() ?: return emptyList()
            val coordinates = PolylineUtils.decode(geometry, 6)
            coordinates.forEach { points.add(it) }
        }
        val annotation = routeLeg.annotation() ?: return emptyList()
        var distanceTraveled = 0.0
        annotation.distance()?.forEachIndexed { index, distance ->
            distanceTraveled += distance
            val point = TurfMeasurement.along(LineString.fromLngLats(points), distanceTraveled, TurfConstants.UNIT_METERS)
            val replayRouteLocation = ReplayRouteLocation(index, point)
            replayRouteLocation.speedMps = annotation.speed()?.get(index)!!
            replayRouteLocation.distance = annotation.distance()?.get(index)!!
            replayRouteLocations.addLocation(replayRouteLocation)
        }

        routeInterpolator.createBearingProfile(replayRouteLocations)

        return replayRouteLocations
    }

    private fun MutableList<ReplayRouteLocation>.addLocation(location: ReplayRouteLocation) {
        location.timeMillis = timeMillis
        add(location)
        timeMillis += 1000L
    }
}
