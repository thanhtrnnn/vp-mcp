package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IShapeUIModel;

/**
 * Deployment diagrams: the tools place nodes, artifacts and components themselves (nested inside
 * their node), so the shapes stay where they are put; every communication path and dependency is
 * routed around them with horizontal and vertical lines and its label placed beside it.
 */
public final class DeploymentLayoutProfile extends LayoutProfile {

  @Override
  public boolean placesShapes() {
    return false;
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    return shape.getModelElement() == null ? null : new NodeSpec();
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    ICaptionUIModel caption = c.getCaptionUIModel();
    if (caption != null && caption.getWidth() > 0 && caption.getHeight() > 0) {
      spec.labels.add(
          new LabelSpec("name", caption.getWidth(), caption.getHeight(), ElkLayout.Slot.CENTER));
    }
    return spec;
  }
}
