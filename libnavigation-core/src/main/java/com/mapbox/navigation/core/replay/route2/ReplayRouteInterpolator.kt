package com.mapbox.navigation.core.replay.route2

import com.mapbox.geojson.Point
import com.mapbox.turf.TurfMeasurement
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

internal class ReplayRouteInterpolator {

    companion object {
        const val smoothRouteMeters = 3.0
        const val maxSpeedMps = 30.0
        const val minSpeedMps = 6.0
        const val uTurnSpeedMps = 1.0
        const val maxAcceleration = 4.0
        const val minAcceleration = -4.0
    }

    private val routeSmoother = ReplayRouteSmoother()

    /**
     * Given a list of replay locations, update each of their bearings to
     * point to the next location in the route.
     */
    fun createBearingProfile(replayRouteLocations: List<ReplayRouteLocation>) {
        var bearing = replayRouteLocations.first().bearing
        replayRouteLocations.forEachIndexed { index, location ->
            if (index + 1 < replayRouteLocations.lastIndex) {
                val fromPoint = location.point
                val toPoint = replayRouteLocations[index + 1].point
                bearing = TurfMeasurement.bearing(fromPoint, toPoint)
            }
            location.bearing = bearing
        }
    }

    /**
     * Given a list of coordinates on a route, detect sections of the route that have significant
     * turns. Return a smaller list of replay locations that have calculated speeds.
     */
    fun createSpeedProfile(coordinates: List<Point>): List<ReplayRouteLocation> {
        val smoothLocations = routeSmoother.smoothRoute(coordinates, smoothRouteMeters)
        smoothLocations.first().speedMps = 0.0
        smoothLocations.last().speedMps = 0.0

        // Get the max speed we can take upcoming turns
        for (i in 1 until smoothLocations.size - 1) {
            val deltaBearing = abs(smoothLocations[i - 1].bearing - smoothLocations[i].bearing)
            val speedMps = if (deltaBearing > 150) {
                uTurnSpeedMps
            } else {
                val velocityFraction = 1.0 - min(1.0, deltaBearing / 90.0)
                val offsetToMaxVelocity = maxSpeedMps - minSpeedMps
                (minSpeedMps + (velocityFraction * offsetToMaxVelocity))
            }
            smoothLocations[i].speedMps = speedMps
        }

        // Reduce the speed if there is not enough distance to slow down
        for (i in smoothLocations.lastIndex downTo 1) {
            val segmentDistance = smoothLocations[i - 1].distance
            val maxSegmentSpeedMps = abs(segmentDistance / minAcceleration) + minSpeedMps
            smoothLocations[i].speedMps = min(smoothLocations[i].speedMps, maxSegmentSpeedMps)
        }

        return smoothLocations
    }

    /**
     * Interpolate a speed across a distance. The returned segment will include approximate
     * once per second speed and distance calculations that can be used to create location coordinates.
     */
    fun interpolateSpeed(startSpeed: Double, endSpeed: Double, distance: Double): ReplayRouteSegment {
        val speedSteps = mutableListOf<ReplayRouteStep>()
        speedSteps.add(ReplayRouteStep(
            acceleration = 0.0,
            speedMps = startSpeed,
            positionMeters = 0.0
        ))

        while (speedSteps.last().positionMeters < (distance - 5)) {
            val previous = speedSteps.last()

            // Try to reach max speed
            val acceleration = when {
                previous.speedMps == maxSpeedMps -> 0.0
                previous.speedMps > maxSpeedMps -> maxSpeedMps - previous.speedMps
                else -> min(maxSpeedMps - previous.speedMps, maxAcceleration)
            }

            // Make the model scared to go too fast
            val slowDownDistance = distanceToSlowDown(previous.speedMps, acceleration, endSpeed)
            val nextRemainingDistance = distance - (previous.positionMeters + previous.speedMps)
            if (nextRemainingDistance > 0 && nextRemainingDistance <= slowDownDistance) {
                interpolateSlowdown(endSpeed, distance, speedSteps)
            } else {
                // Calculate speed
                val speed = previous.speedMps + acceleration

                // Calculate position
                val position = previous.positionMeters + (speed + previous.speedMps) / 2.0

                speedSteps.add(ReplayRouteStep(
                    acceleration = acceleration,
                    speedMps = speed,
                    positionMeters = position
                ))
            }
        }

        return ReplayRouteSegment(
            startSpeedMps = startSpeed,
            endSpeedMps = endSpeed,
            distanceMeters = distance,
            steps = speedSteps)
    }

    private fun interpolateSlowdown(endSpeed: Double, distance: Double, steps: MutableList<ReplayRouteStep>) {
        var previous = steps.last()
        val targetDistance = distance - previous.positionMeters
        val targetSpeed = endSpeed - previous.speedMps
        val remainingSteps = ceil(targetSpeed / minAcceleration).toInt() + 1
        val acceleration = targetSpeed / remainingSteps
        val positionSpeed = targetDistance / remainingSteps

        for (i in 0 until remainingSteps) {
            previous = steps.last()

            // Calculate speed
            val speed = previous.speedMps + acceleration

            // Calculate position
            val position = previous.positionMeters + positionSpeed

            steps.add(ReplayRouteStep(
                acceleration = acceleration,
                speedMps = speed,
                positionMeters = position
            ))
        }
    }

    private fun distanceToSlowDown(velocity: Double, acceleration: Double, endVelocity: Double): Double {
        var currentVelocity = velocity + acceleration
        var distanceToStop = 0.0
        while (currentVelocity > endVelocity) {
            val velocityNext = currentVelocity + minAcceleration
            distanceToStop += (velocityNext + currentVelocity) / 2.0
            currentVelocity = velocityNext
        }
        return distanceToStop
    }
}
