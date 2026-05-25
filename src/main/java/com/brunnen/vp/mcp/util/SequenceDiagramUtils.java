package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
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
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IInteractionLifeLine) {
          IInteractionLifeLine lifeline = (IInteractionLifeLine) model;
          if (name.equals(lifeline.getName())) {
            return lifeline;
          }
        }
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
}
