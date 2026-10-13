package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivation;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility class for sequence diagram operations. */
public final class SequenceDiagramUtils {

  private SequenceDiagramUtils() {}

  /**
   * Get all sequence diagrams in the current project.
   *
   * @return list of sequence diagrams
   */
  public static List<IInteractionDiagramUIModel> getAllSequenceDiagrams() {
    return DiagramUtils.findAllDiagrams(IInteractionDiagramUIModel.class).stream()
        .map(d -> (IInteractionDiagramUIModel) d)
        .collect(Collectors.toList());
  }

  /**
   * Find a lifeline by name in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @param name the lifeline name
   * @return the lifeline, or null if not found
   */
  public static IInteractionLifeLine findLifelineByName(
      IInteractionDiagramUIModel diagram, String name) {
    if (diagram == null || name == null) {
      return null;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (!(obj instanceof IDiagramElement)) {
        continue;
      }
      IDiagramElement de = (IDiagramElement) obj;
      IModelElement model = de.getModelElement();
      if (!(model instanceof IInteractionLifeLine)) {
        continue;
      }
      IInteractionLifeLine lifeline = (IInteractionLifeLine) model;
      // 1. Model name. When an alias/nickname is set VP returns the nickname here, so this also
      //    matches references by alias.
      if (name.equals(lifeline.getName())) {
        return lifeline;
      }
      // 2. Shape caption — addToDiagram sets it to the original lifelineName, which stays stable
      //    even after a nickname overrides getName(). This is the reliable identifier.
      if (de instanceof IShapeUIModel && name.equals(((IShapeUIModel) de).getCustomText())) {
        return lifeline;
      }
      // 3. Base classifier name (the className argument).
      IModelElement classifier = lifeline.getBaseClassifierAsModel();
      if (classifier != null && name.equals(classifier.getName())) {
        return lifeline;
      }
    }
    return null;
  }

  /**
   * Get all lifelines in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @return list of lifelines
   */
  public static List<IInteractionLifeLine> getAllLifelines(IInteractionDiagramUIModel diagram) {
    List<IInteractionLifeLine> lifelines = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IInteractionLifeLine) {
          lifelines.add((IInteractionLifeLine) model);
        }
      }
    }
    return lifelines;
  }

  /**
   * Get all messages in a sequence diagram.
   *
   * @param diagram the sequence diagram
   * @return list of messages
   */
  public static List<IMessage> getAllMessages(IInteractionDiagramUIModel diagram) {
    List<IMessage> messages = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IMessage) {
          messages.add((IMessage) model);
        }
      }
    }
    return messages;
  }

  /**
   * Build a reverse map from activation to lifeline name.
   *
   * @param diagram the sequence diagram
   * @return map from IActivation to lifeline name
   */
  public static Map<IActivation, String> buildActivationToLifelineMap(
      IInteractionDiagramUIModel diagram) {
    Map<IActivation, String> map = new HashMap<>();
    List<IInteractionLifeLine> lifelines = getAllLifelines(diagram);
    for (IInteractionLifeLine ll : lifelines) {
      Iterator<?> iter = ll.activationIterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof IActivation) {
          map.put((IActivation) obj, ll.getName());
        }
      }
    }
    return map;
  }

  /**
   * Validate a lifeline name.
   *
   * @param name the name to validate
   * @return true if valid
   */
  public static boolean isValidLifelineName(String name) {
    return name != null && !name.trim().isEmpty() && name.trim().length() >= 2;
  }

  /**
   * First message number of each operand of a combined fragment that covers messages {@code from}
   * to {@code to}. Explicit starts are used when given (one per operand after the first), otherwise
   * the messages are shared out evenly.
   *
   * @param from first covered top-level message number
   * @param to last covered top-level message number
   * @param operands number of operands
   * @param explicitStarts first message of operands 2..n, or null
   * @return first message number of every operand (length {@code operands})
   */
  public static int[] operandStarts(int from, int to, int operands, int[] explicitStarts) {
    int[] starts = new int[operands];
    starts[0] = from;
    int count = Math.max(1, to - from + 1);
    for (int k = 1; k < operands; k++) {
      if (explicitStarts != null && explicitStarts.length >= operands - 1) {
        starts[k] = explicitStarts[k - 1];
      } else {
        starts[k] = from + (int) Math.round((double) k * count / operands);
      }
      starts[k] = Math.max(starts[k], starts[k - 1] + 1);
    }
    return starts;
  }

  /**
   * Vertical layout of a combined fragment: {top, bottom, operand tops...}. The frame starts {@code
   * topPad} above the first covered message (just below the previous message, whose label sits
   * above its arrow), the first operand starts a header below the frame top (VP draws the operator
   * tab there), later operands start {@code separatorPad} above their first message (the dashed
   * separator between two message rows) and the frame ends {@code bottomPad} below the last
   * message.
   *
   * @param operandFirstYs y of the first message of each operand
   * @param lastY y of the last covered message
   * @param header height VP gives the operator tab row
   * @param topPad distance from the frame top to the first message
   * @param separatorPad distance from an operand separator to the operand's first message
   * @param bottomPad distance from the last message to the frame bottom
   * @return {frame top, frame bottom, operand 1 top, operand 2 top, ...}
   */
  public static int[] fragmentLayout(
      int[] operandFirstYs, int lastY, int header, int topPad, int separatorPad, int bottomPad) {
    int[] layout = new int[2 + operandFirstYs.length];
    layout[0] = operandFirstYs[0] - topPad;
    layout[1] = lastY + bottomPad;
    layout[2] = layout[0] + header;
    for (int i = 1; i < operandFirstYs.length; i++) {
      layout[2 + i] = operandFirstYs[i] - separatorPad;
    }
    return layout;
  }
}
