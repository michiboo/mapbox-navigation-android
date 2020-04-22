package com.mapbox.navigation.core.replay.route2

import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class ReplayRouteDriverTest {
    private val replayRouteDriver = ReplayRouteDriver()

    @Test
    fun `should have location every second`() {
        val geometry = """anq_gAxdhmhFbZkA^?tDMUsF?m@WmKMoHOeF]eO?}@GiBcB}s@?{@McGoDLu@?cUlAqMj@qfAtE"""

        val locations = replayRouteDriver.driveGeometry(geometry)

        var time = 0L
        locations.forEach {
            assertEquals(time, it.timeMillis)
            time += 1000L
        }
    }

    @Test
    fun `should have location every second for multiple routes`() {
        val firstGeometry = """anq_gAxdhmhFbZkA^?tDMUsF?m@WmKMoHOeF]eO?}@GiBcB}s@?{@McGoDLu@?cUlAqMj@qfAtE"""
        val secondGeometry = """qnq_gAxdhmhFuvBlJe@?qC^"""

        val firstLegLocations = replayRouteDriver.driveGeometry(firstGeometry)
        val secondLegLocations = replayRouteDriver.driveGeometry(secondGeometry)

        var time = 0L
        firstLegLocations.forEach {
            assertEquals(time, it.timeMillis)
            time += 1000L
        }
        secondLegLocations.forEach {
            assertEquals(time, it.timeMillis)
            time += 1000L
        }
    }

    @Test
    fun `should slow down at the end of a route`() {
        val geometry = """qnq_gAxdhmhFuvBlJe@?qC^^`GD|@bBpq@pB~{@om@xCqL\"""

        val locations = replayRouteDriver.driveGeometry(geometry)

        // This value is too high, need to slow down more
        locations.takeLast(3).map { it.speedMps }.fold(11.0) { lastSpeed, currentSpeed ->
            assertTrue("$currentSpeed < $lastSpeed", currentSpeed < lastSpeed)
            currentSpeed
        }
    }

    @Test
    fun `should travel along the route at each step`() {
        val geometry = """inq_gAxdhmhF}vBlJe@?qC^mDLmcAfE]LqCNNpGF\`Bnr@pBp{@rBp{@bA|_@"""

        val locations = replayRouteDriver.driveGeometry(geometry)

        var previous = locations[0]
        for (i in 1 until locations.size - 1) {
            val current = locations[i]
            val distance = TurfMeasurement.distance(previous.point, current.point, TurfConstants.UNIT_METERS)
            assertTrue("$i $distance > 0.0", distance > 0.0)
            previous = current
        }
    }

    @Test
    fun `should segment a short route`() {
        val geometry = """wt}ohAj||tfFoD`Sm_@iMcKgD"""

        val locations = replayRouteDriver.driveGeometry(geometry)

        assertTrue("${locations.size} > 10", locations.size > 10)
    }

    @Test
    fun `should segment a ride with a u turn`() {
        val geometry = """wt}ohAj||tfFoD`Sm_@iMcPeFbPdFl_@hMcKvl@"""

        val locations = replayRouteDriver.driveGeometry(geometry)

        assertTrue(locations.size > 10)
    }
}
