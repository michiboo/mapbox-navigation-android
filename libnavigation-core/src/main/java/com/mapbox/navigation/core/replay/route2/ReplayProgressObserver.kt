package com.mapbox.navigation.core.replay.route2

import android.util.Log
import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.replay.history.ReplayEventsListener
import com.mapbox.navigation.core.trip.session.RouteProgressObserver

class ReplayProgressObserver : RouteProgressObserver {

    var replayEventsListener: ReplayEventsListener = { }

    private val replayRouteMapper = ReplayRouteMapper()
    private var currentRouteLeg: RouteLeg? = null

    override fun onRouteProgressChanged(routeProgress: RouteProgress) {
        val stepPointsSize = routeProgress.currentLegProgress()?.currentStepProgress()?.stepPoints()?.size
        val stepDistanceRemaining = routeProgress.currentLegProgress()?.currentStepProgress()?.distanceRemaining()
        val legDistanceRemaining = routeProgress.currentLegProgress()?.distanceRemaining()
        Log.i("RouteReplay", "RouteReplay onRouteProgressChanged $legDistanceRemaining $stepDistanceRemaining $stepPointsSize")

        val routeProgressRouteLeg = routeProgress.currentLegProgress()?.routeLeg()
        if (routeProgressRouteLeg != currentRouteLeg) {
            this.currentRouteLeg = routeProgressRouteLeg
            if (routeProgressRouteLeg != null) {
                val replayEvents = replayRouteMapper.mapRouteLegGeometry(routeProgressRouteLeg)
                if (replayEvents.isNotEmpty()) {
                    replayEventsListener(replayEvents)
                }
            }
        }
    }
}