package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2CompartmentUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2NewUIModel;
import com.vp.plugin.model.IActivity;
import com.vp.plugin.model.IActivityAction;
import com.vp.plugin.model.IActivityFinalNode;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IAnchor;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDecisionNode;
import com.vp.plugin.model.IFlowFinalNode;
import com.vp.plugin.model.IForkNode;
import com.vp.plugin.model.IInitialNode;
import com.vp.plugin.model.IJoinNode;
import com.vp.plugin.model.IMergeNode;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Activity diagrams: the flow runs from the initial node at the top to the activity final node at
 * the bottom. With swimlanes the layered layout decides the rows, every node then stands in the
 * column of its lane and the lanes grow to hold their nodes. Decision branches leave from different
 * vertices of the diamond, merges take their inputs on different sides, the branches of a fork and
 * the inputs of a join meet the bar each at its own place straight above or below the node at the
 * other end. Page frames ("Xử lí tại ...") are containers around the nodes they were made for.
 */
public final class ActivityLayoutProfile extends LayoutProfile {

  /** The lanes of the swimlane frame, as the tools hold them in Visual Paradigm. */
  public interface Lanes {
    /**
     * Number of lanes, left to right.
     *
     * @return the count
     */
    int size();

    /**
     * Index of the lane that holds a node shape.
     *
     * @param node the node shape
     * @return the lane, or -1 when the node stands outside the lanes
     */
    int laneOf(IShapeUIModel node);

    /**
     * Left edge of the first lane.
     *
     * @return x
     */
    int left();

    /**
     * Top edge of the frame (the lane headers).
     *
     * @return y
     */
    int top();

    /**
     * Height of the lane headers.
     *
     * @return the height
     */
    int headerHeight();

    /**
     * Least width of a lane (room for its title).
     *
     * @param lane the lane
     * @return the width
     */
    int minWidth(int lane);

    /**
     * Least height of the body of the lanes (asked for when the partitions were added).
     *
     * @return the height
     */
    int minBodyHeight();

    /**
     * Put the lanes side by side and size the frame around them.
     *
     * @param xs left edge of each lane
     * @param widths width of each lane
     * @param bodyHeight height of the lane bodies below the headers
     */
    void apply(int[] xs, int[] widths, int bodyHeight);

    /**
     * Left edge of each lane now.
     *
     * @return the edges
     */
    int[] xs();

    /**
     * Width of each lane now.
     *
     * @return the widths
     */
    int[] widths();
  }

  /** Gap between a node and the name hanging beside it. */
  private static final int NAME_GAP = 6;

  /** Free space inside a lane around its nodes, and between nodes of one row. */
  private static final int LANE_PADDING = 24;

  private static final int ROW_NODE_GAP = 40;

  /** Free space under the lowest node of a lane. */
  private static final int LANE_BOTTOM = 30;

  private final Lanes lanes;
  private final Map<String, Set<String>> frames;

  /**
   * A profile.
   *
   * @param lanes the lanes of the swimlane frame, or null when the diagram has none
   * @param frames page frames: shape id of a frame to the shape ids of the nodes it surrounds
   */
  public ActivityLayoutProfile(Lanes lanes, Map<String, Set<String>> frames) {
    this.lanes = lanes != null && lanes.size() > 0 ? lanes : null;
    this.frames = frames == null ? new LinkedHashMap<>() : frames;
  }

  @Override
  public boolean routesAll() {
    return true;
  }

  @Override
  public ElkLayout.Options options(IDiagramUIModel diagram) {
    return new ElkLayout.Options()
        .direction(ElkLayout.Dir.DOWN)
        .gaps(60, 80, 18)
        .padding(44, 26)
        .labelAllowance(56)
        .flowOrder();
  }

  /**
   * The node kind of a model: an {@code ActivityDiagramUtils.KIND_*}, or null for anything else.
   */
  public static String kindOf(IModelElement model) {
    if (model instanceof IActivityAction) {
      return ActivityDiagramUtils.KIND_ACTION;
    }
    if (model instanceof IObjectNode) {
      return ActivityDiagramUtils.KIND_OBJECT;
    }
    if (model instanceof IInitialNode) {
      return ActivityDiagramUtils.KIND_INITIAL;
    }
    if (model instanceof IActivityFinalNode) {
      return ActivityDiagramUtils.KIND_FINAL;
    }
    if (model instanceof IFlowFinalNode) {
      return ActivityDiagramUtils.KIND_FLOW_FINAL;
    }
    if (model instanceof IDecisionNode) {
      return ActivityDiagramUtils.KIND_DECISION;
    }
    if (model instanceof IMergeNode) {
      return ActivityDiagramUtils.KIND_MERGE;
    }
    if (model instanceof IForkNode) {
      return ActivityDiagramUtils.KIND_FORK;
    }
    if (model instanceof IJoinNode) {
      return ActivityDiagramUtils.KIND_JOIN;
    }
    return null;
  }

  private static boolean isLanePart(IShapeUIModel shape) {
    return shape instanceof IActivitySwimlane2NewUIModel
        || shape instanceof IActivityPartitionHeaderUIModel
        || shape instanceof IActivitySwimlane2CompartmentUIModel;
  }

  @Override
  public NodeSpec node(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    if (model == null || isLanePart(shape)) {
      return null;
    }
    NodeSpec spec = new NodeSpec();
    String kind = kindOf(model);
    if (kind == null) {
      if (model instanceof IActivity) {
        // a page frame: a container, its title across the top
        spec.minSize = new int[] {TextMetrics.width(model.getName(), true) + 40, 0};
      }
      return spec;
    }
    spec.growable = false;
    switch (kind) {
      case ActivityDiagramUtils.KIND_INITIAL:
        spec.layer = ElkLayout.FIRST_LAYER;
        spec.outline = ShapeOutline.Kind.ELLIPSE;
        spec.size = sizeForEnds(shape, shape.getWidth(), shape.getHeight(), 3);
        break;
      case ActivityDiagramUtils.KIND_FINAL:
        spec.layer = ElkLayout.LAST_LAYER;
        spec.outline = ShapeOutline.Kind.ELLIPSE;
        spec.size = sizeForEnds(shape, shape.getWidth(), shape.getHeight(), 3);
        break;
      case ActivityDiagramUtils.KIND_FLOW_FINAL:
        spec.outline = ShapeOutline.Kind.ELLIPSE;
        spec.size = sizeForEnds(shape, shape.getWidth(), shape.getHeight(), 3);
        break;
      case ActivityDiagramUtils.KIND_DECISION:
      case ActivityDiagramUtils.KIND_MERGE:
        spec.outline = ShapeOutline.Kind.DIAMOND;
        spec.ownSides = true;
        spec.size = sizeForEnds(shape, shape.getWidth(), shape.getHeight(), 4);
        break;
      case ActivityDiagramUtils.KIND_FORK:
      case ActivityDiagramUtils.KIND_JOIN:
        spec.bar = true;
        spec.size = ActivityDiagramUtils.defaultControlNodeSize(kind);
        break;
      default:
        spec.growable = true;
        break;
    }
    int[] name = nameBox(shape, kind);
    if (name != null) {
      spec.outsideLabel = name;
      spec.labelSide = ElkLayout.Side.RIGHT;
    }
    return spec;
  }

  @Override
  public IShapeUIModel container(IShapeUIModel shape) {
    if (shape.getModelElement() instanceof IActivity) {
      return null;
    }
    IDiagramUIModel diagram = shape.getDiagramUIModel();
    if (diagram == null) {
      return null;
    }
    IShapeUIModel[] all = diagram.toShapeUIModelArray();
    for (Map.Entry<String, Set<String>> frame : frames.entrySet()) {
      if (frame.getValue().contains(shape.getId())) {
        for (IShapeUIModel s : all) {
          if (s.getId().equals(frame.getKey())) {
            return s;
          }
        }
      }
    }
    // a frame the tools did not make (a diagram opened from a file) holds what lies inside it
    IShapeUIModel smallest = null;
    for (IShapeUIModel s : all) {
      if (s.getModelElement() instanceof IActivity
          && !frames.containsKey(s.getId())
          && s.getX() <= shape.getX()
          && s.getY() <= shape.getY()
          && s.getX() + s.getWidth() >= shape.getX() + shape.getWidth()
          && s.getY() + s.getHeight() >= shape.getY() + shape.getHeight()
          && (smallest == null
              || s.getWidth() * s.getHeight() < smallest.getWidth() * smallest.getHeight())) {
        smallest = s;
      }
    }
    return smallest;
  }

  @Override
  public EdgeSpec edge(IConnectorUIModel c) {
    EdgeSpec spec = new EdgeSpec();
    IModelElement model = c.getModelElement();
    if (model instanceof IAnchor) {
      spec.reverse = true; // a note stands next to what it explains
      return spec;
    }
    if (!(model instanceof IControlFlow || model instanceof IActivityObjectFlow)) {
      return null;
    }
    String guard = guardOf(model);
    if (guard != null) {
      ICaptionUIModel caption = c.getCaptionUIModel();
      int w =
          Math.max(
              TextMetrics.width("[" + guard + "]", false) + 8,
              caption == null ? 0 : caption.getWidth());
      spec.labels.add(new LabelSpec("guard", w, TextMetrics.LINE_HEIGHT, ElkLayout.Slot.TAIL));
    }
    return spec;
  }

  private static String guardOf(IModelElement flow) {
    String guard =
        flow instanceof IControlFlow
            ? ((IControlFlow) flow).getGuard()
            : flow instanceof IActivityObjectFlow ? ((IActivityObjectFlow) flow).getGuard() : null;
    return guard == null || guard.trim().isEmpty() ? null : guard.trim();
  }

  /** Width and height of the name a control node shows beside it, null when it shows none. */
  private static int[] nameBox(IShapeUIModel shape, String kind) {
    if (!ActivityDiagramUtils.showsLabel(kind)) {
      return null;
    }
    String name = shape.getModelElement().getName();
    ICaptionUIModel caption = shape.getCaptionUIModel();
    int w = Math.max(TextMetrics.width(name, false) + 8, caption == null ? 0 : caption.getWidth());
    return new int[] {w, TextMetrics.LINE_HEIGHT + 2};
  }

  @Override
  public int[] hanging(IShapeUIModel shape) {
    String kind = kindOf(shape.getModelElement());
    int[] name = kind == null ? null : nameBox(shape, kind);
    if (name == null) {
      return null;
    }
    if (ActivityDiagramUtils.KIND_DECISION.equals(kind)) {
      // above the diamond and right of the flow that enters it from above
      return new int[] {
        shape.getX() + shape.getWidth() / 2 + NAME_GAP, shape.getY() - name[1] - 2, name[0], name[1]
      };
    }
    return new int[] {
      shape.getX() + shape.getWidth() + NAME_GAP,
      shape.getY() + shape.getHeight() / 2 - name[1] / 2,
      name[0],
      name[1]
    };
  }

  @Override
  public void placed(IShapeUIModel shape) {
    String kind = kindOf(shape.getModelElement());
    shape.resetCaption(); // the title of a frame, the text of a box, the name of a control node
    if (kind == null
        || ActivityDiagramUtils.KIND_ACTION.equals(kind)
        || ActivityDiagramUtils.KIND_OBJECT.equals(kind)) {
      return;
    }
    // the name of a control node is a detached label beside it
    ICaptionUIModel caption = shape.getCaptionUIModel();
    int[] spot = hanging(shape);
    if (caption == null) {
      return;
    }
    if (spot == null) {
      caption.setVisible(false); // merge, fork and join are unlabelled in UML
    } else {
      caption.setVisible(true);
      caption.setBounds(spot[0], spot[1], spot[2], spot[3]);
    }
  }

  @Override
  public int[] barRange(IShapeUIModel bar) {
    int lane = lanes == null ? -1 : lanes.laneOf(bar);
    if (lane < 0) {
      return null;
    }
    int[] xs = lanes.xs();
    int[] widths = lanes.widths();
    return new int[] {xs[lane] + LANE_PADDING / 2, xs[lane] + widths[lane] - LANE_PADDING / 2};
  }

  @Override
  public void arrange(Map<String, IShapeUIModel> shapes, Map<String, int[]> rects) {
    if (lanes == null) {
      return;
    }
    int n = lanes.size();
    // the rows the layered layout found: nodes whose centers are on one line
    Map<String, Integer> lane = new HashMap<>();
    List<String> free = new ArrayList<>();
    for (String id : rects.keySet()) {
      IShapeUIModel s = shapes.get(id);
      int l = s == null ? -1 : lanes.laneOf(s);
      if (l >= 0 && l < n && kindOf(s.getModelElement()) != null) {
        lane.put(id, l);
      } else {
        free.add(id);
      }
    }
    List<String> ids = new ArrayList<>(lane.keySet());
    ids.sort((a, b) -> Integer.compare(centerY(rects.get(a)), centerY(rects.get(b))));
    List<List<String>> rows = new ArrayList<>();
    for (String id : ids) {
      List<String> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
      if (last != null && Math.abs(centerY(rects.get(last.get(0))) - centerY(rects.get(id))) <= 2) {
        last.add(id);
      } else {
        rows.add(new ArrayList<>(Collections.singletonList(id)));
      }
    }
    // width of each lane: its widest row of nodes (side by side) plus the padding
    int[] widths = new int[n];
    for (int i = 0; i < n; i++) {
      widths[i] = lanes.minWidth(i);
    }
    for (List<String> row : rows) {
      for (int i = 0; i < n; i++) {
        int total = 0;
        int count = 0;
        for (String id : row) {
          if (lane.get(id) == i) {
            total += rects.get(id)[2] + (count++ > 0 ? ROW_NODE_GAP : 0);
          }
        }
        if (count > 0) {
          widths[i] = Math.max(widths[i], total + 2 * LANE_PADDING);
        }
      }
    }
    int[] xs = new int[n];
    int cursor = lanes.left();
    for (int i = 0; i < n; i++) {
      xs[i] = cursor;
      cursor += widths[i];
    }
    // rows start below the headers; every node stands at the middle of its lane
    int minY = Integer.MAX_VALUE;
    for (String id : lane.keySet()) {
      minY = Math.min(minY, rects.get(id)[1]);
    }
    int shiftY = minY == Integer.MAX_VALUE ? 0 : lanes.top() + lanes.headerHeight() + 30 - minY;
    int bottom = 0;
    for (List<String> row : rows) {
      for (int i = 0; i < n; i++) {
        List<String> inLane = new ArrayList<>();
        for (String id : row) {
          if (lane.get(id) == i) {
            inLane.add(id);
          }
        }
        inLane.sort((a, b) -> Integer.compare(rects.get(a)[0], rects.get(b)[0]));
        int total = 0;
        for (String id : inLane) {
          total += rects.get(id)[2];
        }
        total += ROW_NODE_GAP * Math.max(inLane.size() - 1, 0);
        int x = xs[i] + (widths[i] - total) / 2;
        for (String id : inLane) {
          int[] r = rects.get(id);
          r[0] = x;
          r[1] += shiftY;
          x += r[2] + ROW_NODE_GAP;
          bottom = Math.max(bottom, r[1] + r[3]);
        }
      }
    }
    // nodes outside the lanes stay beside the frame, in the arrangement the layout gave them
    int freeShiftX = cursor + 80;
    int freeMinX = Integer.MAX_VALUE;
    for (String id : free) {
      freeMinX = Math.min(freeMinX, rects.get(id)[0]);
    }
    for (String id : free) {
      int[] r = rects.get(id);
      r[0] += freeShiftX - freeMinX;
      r[1] += shiftY;
    }
    int body =
        Math.max(lanes.minBodyHeight(), bottom + LANE_BOTTOM - lanes.top() - lanes.headerHeight());
    lanes.apply(xs, widths, body);
    laneGrowth = new int[] {body};
  }

  /** Body height the last arrangement gave the lanes (kept for {@link #afterLayout}). */
  private int[] laneGrowth;

  private static int centerY(int[] r) {
    return r[1] + r[3] / 2;
  }

  @Override
  public void afterLayout(IDiagramUIModel diagram) {
    if (lanes == null) {
      return;
    }
    // a node placed explicitly may stand lower than the lanes reach: they grow to hold it
    int bottom = 0;
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (kindOf(s.getModelElement()) != null && lanes.laneOf(s) >= 0) {
        bottom = Math.max(bottom, s.getY() + s.getHeight());
      }
    }
    int body =
        Math.max(
            Math.max(lanes.minBodyHeight(), laneGrowth == null ? 0 : laneGrowth[0]),
            bottom + LANE_BOTTOM - lanes.top() - lanes.headerHeight());
    lanes.apply(lanes.xs(), lanes.widths(), body);
  }
}
