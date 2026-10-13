package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IAssociationUIModel;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IUsage;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class and object diagrams: inheritance and realization decide the layers (the general class or
 * interface above its specializations), compositions and aggregations come next (the whole above
 * its parts), plain associations and dependencies are the weakest ties. Multiplicities sit at the
 * ends of an association, its name beside the longest leg.
 */
public final class ClassLayoutProfile extends LayoutProfile {

  /** Layer of every shape by id: hierarchy depth first, then distance from the hierarchy. */
  private final Map<String, Integer> levels = new HashMap<>();

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).gaps(60, 90, 18).padding(44, 30);
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note is drawn after what it explains: next to it, not far away
      return spec;
    }
    if (model instanceof IGeneralization || model instanceof IRealization) {
      // VP draws both from the general class / interface down to the specialization
      spec.priority = 10;
      return spec;
    }
    if (model instanceof IAssociation) {
      IAssociation assoc = (IAssociation) model;
      IAssociationEnd from = (IAssociationEnd) assoc.getFromEnd();
      IAssociationEnd to = (IAssociationEnd) assoc.getToEnd();
      boolean whole = isWhole(from) || isWhole(to);
      // a plain association has no direction of its own: it never makes the diagram deeper than
      // the hierarchy needs; a whole stays above its part, a dependency above what it uses
      spec.reverse = !whole && againstLevels(c);
      spec.priority = whole ? 4 : 2;
      addMultiplicity(spec, "multFrom", from, ElkLayout.Slot.TAIL);
      addMultiplicity(spec, "multTo", to, ElkLayout.Slot.HEAD);
      addName(spec, c, assoc.getName());
      return spec;
    }
    if (model instanceof IDependency || model instanceof IUsage) {
      addName(spec, c, null);
      return spec;
    }
    return spec;
  }

  /**
   * Whether the weak connector points from a deeper to a shallower shape: such a connector is
   * layered the other way round, so associations never make the diagram deeper than the hierarchy
   * and the distance from it require.
   */
  private boolean againstLevels(IConnectorUIModel c) {
    if (c.getFromShape() == null || c.getToShape() == null) {
      return false;
    }
    Integer from = levels.get(c.getFromShape().getId());
    Integer to = levels.get(c.getToShape().getId());
    return from != null && to != null && from > to;
  }

  /**
   * Levels: the depth of a class in the generalization and realization forest (the general class at
   * 0), every other class one more than its nearest neighbour that has a level.
   */
  private void computeLevels(IDiagramUIModel diagram) {
    levels.clear();
    Map<String, List<String>> children = new HashMap<>();
    Map<String, List<String>> neighbours = new HashMap<>();
    Map<String, Integer> parentCount = new HashMap<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      neighbours.put(s.getId(), new java.util.ArrayList<>());
    }
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      if (c.getFromShape() == null || c.getToShape() == null) {
        continue;
      }
      String from = c.getFromShape().getId();
      String to = c.getToShape().getId();
      neighbours.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(to);
      neighbours.computeIfAbsent(to, k -> new java.util.ArrayList<>()).add(from);
      IModelElement model = c.getModelElement();
      if (model instanceof IGeneralization || model instanceof IRealization) {
        children.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(to);
        parentCount.merge(to, 1, Integer::sum);
        parentCount.putIfAbsent(from, 0);
      }
    }
    // longest path from the roots of the hierarchy
    ArrayDeque<String> queue = new ArrayDeque<>();
    Map<String, Integer> remaining = new HashMap<>(parentCount);
    for (Map.Entry<String, Integer> e : parentCount.entrySet()) {
      if (e.getValue() == 0) {
        levels.put(e.getKey(), 0);
        queue.add(e.getKey());
      }
    }
    while (!queue.isEmpty()) {
      String id = queue.poll();
      for (String child : children.getOrDefault(id, java.util.Collections.emptyList())) {
        levels.merge(child, levels.get(id) + 1, Math::max);
        if (remaining.merge(child, -1, Integer::sum) == 0) {
          queue.add(child);
        }
      }
    }
    // everything else hangs off the hierarchy by the shortest connection; a diagram without
    // any hierarchy starts from its best connected class
    if (levels.isEmpty()) {
      String hub = null;
      for (Map.Entry<String, List<String>> e : neighbours.entrySet()) {
        if (hub == null || e.getValue().size() > neighbours.get(hub).size()) {
          hub = e.getKey();
        }
      }
      if (hub != null) {
        levels.put(hub, 0);
      }
    }
    ArrayDeque<String> frontier = new ArrayDeque<>();
    levels.entrySet().stream()
        .sorted(Map.Entry.comparingByValue())
        .forEach(e -> frontier.add(e.getKey()));
    while (!frontier.isEmpty()) {
      String id = frontier.poll();
      for (String next : neighbours.getOrDefault(id, java.util.Collections.emptyList())) {
        if (!levels.containsKey(next)) {
          levels.put(next, levels.get(id) + 1);
          frontier.add(next);
        }
      }
    }
  }

  private static boolean isWhole(IAssociationEnd end) {
    String kind = end == null ? null : end.getAggregationKind();
    return kind != null
        && !IAssociationEnd.AGGREGATION_KIND_NONE.equalsIgnoreCase(kind)
        && !kind.isEmpty();
  }

  private static void addMultiplicity(
      EdgeSpec spec, String key, IAssociationEnd end, ElkLayout.Slot slot) {
    String text = end == null ? null : end.getMultiplicity();
    if (text == null || text.isEmpty() || "Unspecified".equalsIgnoreCase(text)) {
      return;
    }
    spec.labels.add(new LabelSpec(key, TextMetrics.width(text, false) + 6, 14, slot));
  }

  /** The connector's own caption (association name, keyword of a dependency). */
  private static void addName(EdgeSpec spec, IConnectorUIModel c, String name) {
    ICaptionUIModel caption = c.getCaptionUIModel();
    int w = caption == null ? 0 : caption.getWidth();
    int h = caption == null ? 0 : caption.getHeight();
    if (name != null && !name.isEmpty()) {
      w = Math.max(w, TextMetrics.caption(name)[0]);
      h = Math.max(h, TextMetrics.LINE_HEIGHT);
    }
    if (w > 0 && h > 0) {
      spec.labels.add(new LabelSpec("name", w, h, ElkLayout.Slot.CENTER));
    }
  }

  @Override
  public void applyLabel(IConnectorUIModel c, String key, int[] rect) {
    if (c instanceof IAssociationUIModel && !"name".equals(key)) {
      IAssociationUIModel assoc = (IAssociationUIModel) c;
      Rectangle r = new Rectangle(rect[0], rect[1], rect[2], rect[3]);
      if ("multFrom".equals(key)) {
        assoc.setMultiplicityARectangle(r);
      } else {
        assoc.setMultiplicityBRectangle(r);
      }
      return;
    }
    super.applyLabel(c, key, rect);
  }

  @Override
  public void beforeLayout(IDiagramUIModel diagram) {
    computeLevels(diagram);
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      if (c instanceof IAssociationUIModel) {
        ((IAssociationUIModel) c).setShowFromRoleName(false);
        ((IAssociationUIModel) c).setShowToRoleName(false);
      }
    }
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    NodeSpec spec = new NodeSpec();
    if (shape.getModelElement() instanceof IPackage) {
      // the name sits in the folder tab and across the top of the frame
      spec.minSize = new int[] {TextMetrics.width(shape.getModelElement().getName(), true) + 60, 0};
    }
    return spec;
  }
}
