package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import java.util.ArrayList;
import java.util.List;

/**
 * What the shared graph layout ({@link VpGraphLayout}) needs to know about one diagram type: which
 * shapes are nodes (and which of them are containers), how connectors point and what labels they
 * carry, how a node's outline looks and where its labels are written. A family overrides what
 * differs from the default: every shape is a rectangular node, every connector an unlabelled edge
 * that points from its from-shape to its to-shape.
 */
public class LayoutProfile {

  /** How one shape takes part in the layout. */
  public static final class NodeSpec {
    /**
     * {@link ElkLayout#FIRST_LAYER}, {@link ElkLayout#LAST_LAYER} or {@link ElkLayout#ANY_LAYER}.
     */
    public int layer = ElkLayout.ANY_LAYER;

    /** Outline of the shape, for where connectors end on it. */
    public ShapeOutline.Kind outline = ShapeOutline.Kind.RECT;

    /** Width and height of a text hanging below the shape (an actor's name), 0 for none. */
    public int[] bottomLabel;

    /** Whether the layout may enlarge the shape when a side carries more connectors than fit. */
    public boolean growable = true;

    /** Least width and height of a container (room for its title), null for none. */
    public int[] minSize;
  }

  /** A label of a connector. */
  public static final class LabelSpec {
    /** What the label is, handed back to {@link LayoutProfile#applyLabel}. */
    public final String key;

    /** Width. */
    public final int w;

    /** Height. */
    public final int h;

    /** Near the from-shape, near the to-shape or beside the middle. */
    public final ElkLayout.Slot slot;

    /**
     * A label.
     *
     * @param key what the label is
     * @param w width
     * @param h height
     * @param slot where it sits
     */
    public LabelSpec(String key, int w, int h, ElkLayout.Slot slot) {
      this.key = key;
      this.w = w;
      this.h = h;
      this.slot = slot;
    }
  }

  /** How one connector takes part in the layout. */
  public static final class EdgeSpec {
    /** Layer the to-shape above/before the from-shape (child to parent drawn as parent first). */
    public boolean reverse;

    /** How strongly the edge wants to be short, straight and along the flow. */
    public int priority = 1;

    /**
     * The connector does not take part in the layering: it is routed after the shapes have their
     * places (the lines between the actors of a generalization, drawn in a channel of their own).
     */
    public boolean deferred;

    /**
     * Side of the from-shape the connector may leave by: 0 north, 1 east, 2 south, 3 west, -1 any.
     */
    public int fromSide = -1;

    /** Side of the to-shape the connector may enter by, same values. */
    public int toSide = -1;

    /** Side the connector prefers to leave the from-shape by (soft, routed connectors), -1 none. */
    public int preferFromSide = -1;

    /** Side the connector prefers to enter the to-shape by (soft, routed connectors), -1 none. */
    public int preferToSide = -1;

    /** The labels of the connector. */
    public final List<LabelSpec> labels = new ArrayList<>();
  }

  /**
   * Whether the layered layout may move the shapes of this diagram type. A family that places its
   * shapes itself as they are added (nested containers, explicit grids) answers false: only its
   * connectors and labels are routed.
   *
   * @return true when the shapes are laid out by the shared layout
   */
  public boolean placesShapes() {
    return true;
  }

  /**
   * Layout options of the diagram: flow direction and gaps.
   *
   * @param diagram the diagram
   * @return the options
   */
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options();
  }

  /**
   * The role of a shape in the layout.
   *
   * @param shape a shape of the diagram
   * @return how it takes part, or null when it is not part of the graph and stays where it is
   */
  public NodeSpec node(IShapeUIModel shape) {
    return new NodeSpec();
  }

  /**
   * The shape that contains {@code shape} in the layout (a package, a node, a system boundary).
   *
   * @param shape a shape of the diagram
   * @return the container shape, or null
   */
  public IShapeUIModel container(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  /**
   * The role of a connector in the layout.
   *
   * @param connector a connector of the diagram
   * @return how it takes part, or null when it is left alone
   */
  public EdgeSpec edge(IConnectorUIModel connector) {
    return new EdgeSpec();
  }

  /**
   * The rectangle {x, y, w, h} of what hangs outside a shape's bounds and is drawn with it (an
   * actor's name): connectors and labels keep off it like off the shape.
   *
   * @param shape the shape
   * @return the rectangle, or null when nothing hangs outside
   */
  public int[] hanging(IShapeUIModel shape) {
    return null;
  }

  /**
   * Draw a label of a connector at a place.
   *
   * @param connector the connector
   * @param key the label's key from its {@link LabelSpec}
   * @param rect where it goes {x, y, w, h}, absolute
   */
  public void applyLabel(IConnectorUIModel connector, String key, int[] rect) {
    ICaptionUIModel caption = connector.getCaptionUIModel();
    if (caption != null) {
      caption.setVisible(true);
      caption.setBounds(rect[0], rect[1], rect[2], rect[3]);
    }
  }

  /**
   * Called once a shape has been put at its new place (its detached labels follow).
   *
   * @param shape the shape
   */
  public void placed(IShapeUIModel shape) {
    // nothing by default
  }

  /**
   * Called before the layout reads the diagram (refit shapes, reset captions).
   *
   * @param diagram the diagram
   */
  public void beforeLayout(IDiagramUIModel diagram) {
    // nothing by default
  }

  /**
   * Called after the layout was applied.
   *
   * @param diagram the diagram
   */
  public void afterLayout(IDiagramUIModel diagram) {
    // nothing by default
  }
}
