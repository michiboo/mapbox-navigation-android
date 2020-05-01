package com.mapbox.navigation.core.replay.route2

import android.location.Location
import android.util.Log
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.LegAnnotation
import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.api.directions.v5.models.LegStep
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.navigation.core.replay.history.ReplayEventBase
import com.mapbox.navigation.core.replay.history.ReplayEventLocation
import com.mapbox.navigation.core.replay.history.ReplayEventUpdateLocation
import com.mapbox.navigation.core.replay.history.ReplayHistoryPlayer

/**
 * This class converts a directions rout into events that can be
 * replayed using the [ReplayHistoryPlayer] to navigate a route.
 */
class ReplayRouteMapper {

    private val replayRouteDriver = ReplayRouteDriver()

    /**
     * Take a [DirectionsRoute] and map it to events that can be replayed by the [ReplayHistoryPlayer].
     * Uses the directions api [DirectionsRoute.geometry] to calculate the speed
     * and position estimates for the replay locations.
     *
     * @param directionsRoute the [DirectionsRoute] containing information about a route
     */
    fun mapDirectionsRouteGeometry(directionsRoute: DirectionsRoute): List<ReplayEventBase> {
        val usesPolyline6 = directionsRoute.routeOptions()?.geometries()?.contains(DirectionsCriteria.GEOMETRY_POLYLINE6) ?: false
        if (!usesPolyline6) {
            throw IllegalStateException("Add .geometries(DirectionsCriteria.GEOMETRY_POLYLINE6) to your directions request")
        }
        val geometry = directionsRoute.geometry() ?: return emptyList()
        return mapGeometry(geometry)
    }

    /**
     * Take a single [RouteLeg] from the [DirectionsRoute] and map it to a drive using the
     * [LegStep.geometry] composed together.
     */
    fun mapRouteLegGeometry(routeLeg: RouteLeg): List<ReplayEventBase> {
        val replayEvents = mutableListOf<ReplayEventBase>()
        routeLeg.steps()?.flatMap { legStep ->
            val geometry = legStep.geometry() ?: return emptyList()
            PolylineUtils.decode(geometry, 6)
        }?.also { points ->
            replayRouteDriver.drivePointList(points)
                .map { mapToUpdateLocation(it) }
                .forEach { replayEvents.add(it) }
        }
        return replayEvents
    }

    /**
     * Use any a polyline string to create replay events that simulator a car driving
     * the route.
     *
     * @param geometry is a polyline precision 6
     */
    fun mapGeometry(geometry: String): List<ReplayEventBase> {
        return replayRouteDriver.driveGeometry(geometry)
            .map { mapToUpdateLocation(it) }
    }

    /**
     * Take a [DirectionsRoute] and map it to events that can be replayed by the [ReplayHistoryPlayer].
     * Uses the directions api [LegAnnotation] to create the speed and position
     * estimates for the replay locations.
     *
     * @param directionsRoute the [DirectionsRoute] containing information about a route
     */
    fun mapDirectionsRouteLegAnnotation(directionsRoute: DirectionsRoute): List<ReplayEventBase> {
        return directionsRoute.legs()?.flatMap { routeLeg ->
            mapRouteLegAnnotation(routeLeg)
        } ?: emptyList()
    }

    /**
     * Given a [RouteLeg], use the [LegAnnotation] to create the speed and locations.
     * To use this, your directions request must include [DirectionsCriteria.ANNOTATION_SPEED] and
     * [DirectionsCriteria.ANNOTATION_DISTANCE]
     *
     * @param routeLeg The route leg to be mapped into replay events
     */
    fun mapRouteLegAnnotation(routeLeg: RouteLeg): List<ReplayEventBase> {
        return replayRouteDriver.driveRouteLeg(routeLeg)
            .map { mapToUpdateLocation(it) }
    }

    companion object {
        private const val REPLAY_ROUTE_ACCURACY_HORIZONTAL = 3.0

        /**
         * Map a Android location into a replay event.
         *
         * @param eventTimestamp the eventTimestamp for the replay event
         * @param location Android location to be replayed
         * @return an event to that can be replayed
         */
        fun mapToUpdateLocation(eventTimestamp: Double, location: Location): ReplayEventBase {
            val values = ReplayEventUpdateLocation(
                eventTimestamp = eventTimestamp,
                location = ReplayEventLocation(
                    lon = location.longitude,
                    lat = location.latitude,
                    provider = location.provider,
                    time = eventTimestamp,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracyHorizontal = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                    bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null
                )
            )
            Log.i("ReplayRoute", "ReplayRoute mapToUpdateLocation $values")
            return values
        }

        /**
         * Map a Android location into a replay event.
         *
         * @param location simulated location used for replay
         * @return a singleton list to be replayed, otherwise an empty list if the location cannot be replayed
         */
        internal fun mapToUpdateLocation(location: ReplayRouteLocation): ReplayEventBase {
            val values = ReplayEventUpdateLocation(
                eventTimestamp = location.timeSeconds,
                location = ReplayEventLocation(
                    lon = location.point.longitude(),
                    lat = location.point.latitude(),
                    provider = "ReplayRoute",
                    time = location.timeSeconds,
                    altitude = null,
                    accuracyHorizontal = REPLAY_ROUTE_ACCURACY_HORIZONTAL,
                    bearing = location.bearing,
                    speed = location.speedMps
                )
            )
            Log.i("ReplayRoute", "ReplayRoute mapToUpdateLocation $values")
            return values
        }
    }
}
