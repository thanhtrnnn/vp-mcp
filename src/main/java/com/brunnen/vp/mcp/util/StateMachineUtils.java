package com.brunnen.vp.mcp.util;

/** Utility class for UML state machine diagram operations. */
public final class StateMachineUtils {

  private StateMachineUtils() {}

  /**
   * Check whether a state name can be used as a handle for transitions.
   *
   * @param name the state name
   * @return true when the name is usable
   */
  public static boolean isValidStateName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Normalize a transition label. Blank labels become null so the connector stays unlabelled
   * instead of showing an empty caption box.
   *
   * @param label the raw label, UML form {@code event [guard] / effect}
   * @return the trimmed label, or null when nothing was supplied
   */
  public static String normalizeTransitionLabel(String label) {
    if (label == null) {
      return null;
    }
    String trimmed = label.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Build the caption of a state box: the state name plus the optional entry/do/exit compartment
   * lines in UML order.
   *
   * @param stateName the state name
   * @param entryAction the entry action, may be null or blank
   * @param doActivity the do activity, may be null or blank
   * @param exitAction the exit action, may be null or blank
   * @return the multi-line caption, or null when no action text was supplied
   */
  public static String buildStateCaption(
      String stateName, String entryAction, String doActivity, String exitAction) {
    StringBuilder actions = new StringBuilder();
    appendAction(actions, "entry / ", entryAction);
    appendAction(actions, "do / ", doActivity);
    appendAction(actions, "exit / ", exitAction);
    if (actions.length() == 0) {
      return null;
    }
    return stateName + "\n" + actions.toString().trim();
  }

  private static void appendAction(StringBuilder sb, String prefix, String action) {
    if (action != null && !action.trim().isEmpty()) {
      sb.append(prefix).append(action.trim()).append('\n');
    }
  }
}
