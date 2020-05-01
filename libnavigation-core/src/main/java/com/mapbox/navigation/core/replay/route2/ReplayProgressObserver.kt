package com.mapbox.navigation.core.replay.route2

import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.replay.history.ReplayEventsListener
import com.mapbox.navigation.core.trip.session.RouteProgressObserver

class ReplayProgressObserver : RouteProgressObserver {

    var replayEventsListener: ReplayEventsListener = { }

    private val replayRouteMapper = ReplayRouteMapper()
    private var currentRouteLeg: RouteLeg? = null

    override fun onRouteProgressChanged(routeProgress: RouteProgress) {
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
