package com.mapbox.navigation.core.replay.route2

import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.replay.history.ReplayHistoryPlayer
import com.mapbox.navigation.core.trip.session.RouteProgressObserver

/**
 * Register this to [MapboxNavigation.registerRouteProgressObserver].
 * This class will feed locations to your [ReplayHistoryPlayer] and simulate
 * your active route for you.
 */
class ReplayProgressObserver(
    private val replayHistoryPlayer: ReplayHistoryPlayer
) : RouteProgressObserver {

    private val replayRouteMapper = ReplayRouteMapper()
    private var currentRouteLeg: RouteLeg? = null

    /**
     * Called by [MapboxNavigation] when this class has been registered. If you are handling
     * your own [RouteProgress], you can call this method directly.
     *
     * @param routeProgress from the navigation session
     */
    override fun onRouteProgressChanged(routeProgress: RouteProgress) {
        val routeProgressRouteLeg = routeProgress.currentLegProgress()?.routeLeg()
        if (routeProgressRouteLeg != currentRouteLeg) {
            this.currentRouteLeg = routeProgressRouteLeg
            onRouteLegChanged(routeProgressRouteLeg)
        }
    }

    private fun onRouteLegChanged(routeProgressRouteLeg: RouteLeg?) {
        if (routeProgressRouteLeg != null) {
            val replayEvents = replayRouteMapper.mapRouteLegGeometry(routeProgressRouteLeg)
            if (replayEvents.isNotEmpty()) {
                replayHistoryPlayer.pushEvents(replayEvents)
                replayHistoryPlayer.seekTo(replayEvents.first())
            }
        }
    }
}
