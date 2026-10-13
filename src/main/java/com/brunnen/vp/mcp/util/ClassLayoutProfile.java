package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IAssociationUIModel;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IUsage;
import java.awt.Rectangle;

/**
 * Class and object diagrams: inheritance and realization decide the layers (the general class or
 * interface above its specializations), compositions and aggregations come next (the whole above
 * its parts), plain associations and dependencies are the weakest ties. Multiplicities sit at the
 * ends of an association, its name beside the longest leg.
 */
public final class ClassLayoutProfile extends LayoutProfile {

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options().direction(ElkLayout.Dir.DOWN).gaps(60, 90, 18).padding(44, 30);
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IGeneralization || model instanceof IRealization) {
      // VP draws both from the general class / interface down to the specialization
      spec.priority = 10;
      return spec;
    }
    if (model instanceof IAssociation) {
      IAssociation assoc = (IAssociation) model;
      IAssociationEnd from = (IAssociationEnd) assoc.getFromEnd();
      IAssociationEnd to = (IAssociationEnd) assoc.getToEnd();
      spec.priority = isWhole(from) || isWhole(to) ? 4 : 2;
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
    for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
      if (c instanceof IAssociationUIModel) {
        ((IAssociationUIModel) c).setShowFromRoleName(false);
        ((IAssociationUIModel) c).setShowToRoleName(false);
      }
    }
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    return new NodeSpec();
  }
}
