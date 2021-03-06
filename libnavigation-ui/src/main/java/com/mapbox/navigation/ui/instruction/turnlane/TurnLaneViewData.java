package com.mapbox.navigation.ui.instruction.turnlane;

import com.mapbox.navigation.trip.notification.internal.maneuver.ManeuverModifier;

class TurnLaneViewData {

  static final String DRAW_LANE_SLIGHT_RIGHT = "draw_lane_slight_right";
  static final String DRAW_LANE_RIGHT = "draw_lane_right";
  static final String DRAW_LANE_STRAIGHT = "draw_lane_straight";
  static final String DRAW_LANE_UTURN = "draw_lane_uturn";
  static final String DRAW_LANE_RIGHT_ONLY = "draw_lane_right_only";
  static final String DRAW_LANE_STRAIGHT_ONLY = "draw_lane_straight_only";

  private boolean shouldFlip;
  private String drawMethod;

  TurnLaneViewData(String laneIndications, String maneuverModifier) {
    buildDrawData(laneIndications, maneuverModifier);
  }

  boolean shouldBeFlipped() {
    return shouldFlip;
  }

  String getDrawMethod() {
    return drawMethod;
  }

  private void buildDrawData(String laneIndications, String maneuverModifier) {

    // U-turn
    if (laneIndications.contentEquals(ManeuverModifier.UTURN)) {
      drawMethod = DRAW_LANE_UTURN;
      shouldFlip = true;
      return;
    }

    // Straight
    if (laneIndications.contentEquals(ManeuverModifier.STRAIGHT)) {
      drawMethod = DRAW_LANE_STRAIGHT;
      return;
    }

    // Right or left
    if (laneIndications.contentEquals(ManeuverModifier.RIGHT)) {
      drawMethod = DRAW_LANE_RIGHT;
      return;
    } else if (laneIndications.contentEquals(ManeuverModifier.LEFT)) {
      drawMethod = DRAW_LANE_RIGHT;
      shouldFlip = true;
      return;
    }

    // Slight right or slight left
    if (laneIndications.contentEquals(ManeuverModifier.SLIGHT_RIGHT)) {
      drawMethod = DRAW_LANE_SLIGHT_RIGHT;
      return;
    } else if (laneIndications.contentEquals(ManeuverModifier.SLIGHT_LEFT)) {
      drawMethod = DRAW_LANE_SLIGHT_RIGHT;
      shouldFlip = true;
      return;
    }

    // Straight and right or left
    if (isStraightPlusIndication(laneIndications, ManeuverModifier.RIGHT)) {
      setDrawMethodWithModifier(maneuverModifier);
    } else if (isStraightPlusIndication(laneIndications, ManeuverModifier.LEFT)) {
      setDrawMethodWithModifier(maneuverModifier);
      shouldFlip = true;
    }
  }

  private void setDrawMethodWithModifier(String maneuverModifier) {
    if (maneuverModifier.contains(ManeuverModifier.RIGHT)) {
      drawMethod = DRAW_LANE_RIGHT_ONLY;
    } else if (maneuverModifier.contains(ManeuverModifier.STRAIGHT)) {
      drawMethod = DRAW_LANE_STRAIGHT_ONLY;
    } else {
      drawMethod = DRAW_LANE_RIGHT_ONLY;
    }
  }

  private boolean isStraightPlusIndication(String laneIndications, String turnLaneIndication) {
    return laneIndications.contains(ManeuverModifier.STRAIGHT)
      && laneIndications.contains(turnLaneIndication);
  }
}
