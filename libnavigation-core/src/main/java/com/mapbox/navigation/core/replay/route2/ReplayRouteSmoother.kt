package com.mapbox.navigation.core.replay.route2

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfMeasurement.EARTH_RADIUS
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In order to estimate the speed needed to traverse a [DirectionsRoute] we need
 * a route with locations that are significant for speed.
 *
 * This class reduces a list of location coordinates into a list of locations
 * with direction changes beyond a threshold.
 */
internal class ReplayRouteSmoother {

    /**
     * Take a list of coordinates and return a list of locations where there are significant
     * changes in bearing. Each location will have a bearing and a distance.
     */
    fun smoothRoute(points: List<Point>, thresholdMeters: Double): List<ReplayRouteLocation> {
        val distinctPoints = distinctPoints(points, DISTINCT_POINT_METERS)
        val smoothIndices = smoothRouteIndices(distinctPoints, thresholdMeters)
        val smoothLocations = smoothIndices.map { ReplayRouteLocation(it, distinctPoints[it]) }

        var segmentStart = smoothLocations[0]
        var segmentEnd = smoothLocations[1]
        var bearing = TurfMeasurement.bearing(segmentStart.point, segmentEnd.point)
        var segmentRoute = segmentRoute(distinctPoints, segmentStart.routeIndex!!, segmentEnd.routeIndex!!)
        var distance = TurfMeasurement.length(LineString.fromLngLats(segmentRoute), TurfConstants.UNIT_METERS)
        smoothLocations.first().apply {
            this.bearing = bearing
            this.distance = distance
        }

        for (i in 1 until smoothLocations.size - 1) {
            segmentStart = smoothLocations[i]
            segmentEnd = smoothLocations[i + 1]
            segmentRoute = segmentRoute(distinctPoints, segmentStart.routeIndex!!, segmentEnd.routeIndex!!)
            distance = TurfMeasurement.length(LineString.fromLngLats(segmentRoute), TurfConstants.UNIT_METERS)
            bearing = TurfMeasurement.bearing(segmentStart.point, segmentEnd.point)

            segmentStart.bearing = bearing
            segmentStart.distance = distance
        }

        // Stop at the end of the route
        smoothLocations.last().apply {
            this.bearing = bearing
            this.distance = 0.0
        }

        return smoothLocations
    }

    /**
     * Given a list of location points, identify indices that are not
     * within [thresholdMeters] of a straight road.
     */
    fun smoothRouteIndices(points: List<Point>, thresholdMeters: Double): List<Int> {
        val smoothedRouteIndices = mutableListOf<Int>()
        if (points.size <= 3) return List(3) { it }

        smoothedRouteIndices.add(0)
        var sumDistance = 0.0
        for (i in 1 until points.lastIndex) {
            val startPoint = points[i - 1]
            val centerPoint = points[i]
            val endPoint = points[i + 1]
            val distanceToSegment = distanceToSegment(startPoint, centerPoint, endPoint)
            sumDistance += abs(distanceToSegment ?: 0.0)
            if (distanceToSegment == null || sumDistance > thresholdMeters) {
                smoothedRouteIndices.add(i)
                sumDistance = 0.0
            }
        }
        smoothedRouteIndices.add(points.lastIndex)

        return smoothedRouteIndices
    }

    /**
     * Takes a list of points removes points outside of a threshold distance.
     * Note that this will not remove u-turn points.
     */
    fun distinctPoints(points: List<Point>, thresholdMeters: Double): List<Point> {
        var previous = points.firstOrNull() ?: return points
        val distinct = mutableListOf(previous)
        for (i in 1..points.lastIndex) {
            val distance = TurfMeasurement.distance(previous, points[i], TurfConstants.UNIT_METERS)
            if (distance >= thresholdMeters) {
                distinct.add(points[i])
                previous = points[i]
            }
        }
        return distinct
    }

    /**
     * Given three points on a road segment. This will return the turn perpendicular distance
     * of the middle point to the road. If the road direction is being reversed, return null.
     * The sign of the distance says if it is turning one direction vs another.
     */
    fun distanceToSegment(segmentStart: Point, middlePoint: Point, segmentEnd: Point): Double? {
        val p0: DoubleArray = cartesian(segmentStart)
        val p1: DoubleArray = cartesian(segmentEnd)
        val c: DoubleArray = cartesian(middlePoint)
        val v0: DoubleArray = normalize(vector(p0, p1))
        val v1: DoubleArray = vector(p0, c)
        val directionVector = crossProduct(v0, v1)
        val distance = dotProduct(gravity(p0), directionVector)
        val isUTurn = isNaN(v0) || dotProduct(v0, vector(c, p1)) < 0
        return if (!isUTurn) {
            distance
        } else {
            null
        }
    }

    /**
     * Given two indexes on a road, return all the points within that segment.
     */
    fun segmentRoute(points: List<Point>, startIndex: Int, endIndex: Int): List<Point> {
        return points.subList(startIndex, endIndex + 1)
    }

    /**
     * Vector3 math so we can calculate the distance to a road segment.
     */

    private fun gravity(vector3: DoubleArray): DoubleArray {
        return normalize(vector(EARTH_CENTER, vector3))
    }

    private fun cartesian(point: Point): DoubleArray {
        val latitudeRadians = Math.toRadians(point.latitude())
        val longitudeRadians = Math.toRadians(point.longitude())
        return doubleArrayOf(
            EARTH_RADIUS * cos(latitudeRadians) * cos(longitudeRadians),
            EARTH_RADIUS * cos(latitudeRadians) * sin(longitudeRadians),
            EARTH_RADIUS * sin(latitudeRadians)
        )
    }

    private fun isNaN(vector3: DoubleArray): Boolean {
        return vector3[0].isNaN() ||
            vector3[1].isNaN() ||
            vector3[2].isNaN()
    }

    private fun vector(fromPoint3: DoubleArray, toPoint3: DoubleArray): DoubleArray {
        return doubleArrayOf(
            toPoint3[0] - fromPoint3[0],
            toPoint3[1] - fromPoint3[1],
            toPoint3[2] - fromPoint3[2]
        )
    }

    private fun normalize(vector3: DoubleArray): DoubleArray {
        val length: Double = magnitude(vector3)
        return doubleArrayOf(
            vector3[0] / length,
            vector3[1] / length,
            vector3[2] / length
        )
    }

    private fun magnitude(vector3: DoubleArray): Double {
        return sqrt(dotProduct(vector3, vector3))
    }

    private fun dotProduct(lhsVector3: DoubleArray, rhsVector3: DoubleArray): Double {
        return lhsVector3[0] * rhsVector3[0] + lhsVector3[1] * rhsVector3[1] + lhsVector3[2] * rhsVector3[2]
    }

    private fun crossProduct(lhs: DoubleArray, rhs: DoubleArray): DoubleArray {
        return doubleArrayOf(
            lhs[1] * rhs[2] - lhs[2] * rhs[1],
            lhs[2] * rhs[0] - lhs[0] * rhs[2],
            lhs[0] * rhs[1] - lhs[1] * rhs[0]
        )
    }

    companion object {
        val EARTH_CENTER: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
        val DISTINCT_POINT_METERS = 0.0001
    }
}
