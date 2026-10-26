package stud.g01.queue;

import core.problem.State;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Indexed min-heap: O(log n) insert/update/pop and expected O(1) lookup. */
public class PqFrontier implements Frontier {
  private final ArrayList<Node> heap = new ArrayList<>();
  private final Map<State, Integer> indices = new HashMap<>();
  private final Comparator<Node> order;

  public PqFrontier() {
    this(EvaluationType.FULL);
  }

  public PqFrontier(EvaluationType type) {
    this(comparator(type));
  }

  public PqFrontier(Comparator<Node> comparator) {
    this.order = Objects.requireNonNull(comparator, "comparator");
  }

  private static Comparator<Node> comparator(EvaluationType type) {
    Objects.requireNonNull(type, "type");
    if (type == EvaluationType.FULL) {
      // Equal f: prefer the node closer to the goal (larger g).
      return Comparator.comparingInt(Node::evaluation)
          .thenComparing(Comparator.comparingInt(Node::getPathCost).reversed());
    }
    return Node.evaluator(type);
  }

  @Override
  public boolean offer(Node node) {
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(node.getState(), "node.state");
    Integer oldIndex = indices.get(node.getState());
    if (oldIndex != null) {
      if (node.getPathCost() >= heap.get(oldIndex).getPathCost()) return false;
      heap.set(oldIndex, node);
      int moved = siftUp(oldIndex);
      siftDown(moved);
      return true;
    }
    int index = heap.size();
    heap.add(node);
    indices.put(node.getState(), index);
    siftUp(index);
    return true;
  }

  @Override
  public Node poll() {
    if (heap.isEmpty()) return null;
    Node result = heap.get(0);
    Node last = heap.remove(heap.size() - 1);
    indices.remove(result.getState());
    if (!heap.isEmpty()) {
      heap.set(0, last);
      indices.put(last.getState(), 0);
      siftDown(0);
    }
    return result;
  }

  @Override
  public boolean contains(Node node) {
    return node != null && indices.containsKey(node.getState());
  }

  @Override
  public int size() {
    return heap.size();
  }

  @Override
  public boolean isEmpty() {
    return heap.isEmpty();
  }

  @Override
  public void clear() {
    heap.clear();
    indices.clear();
  }

  private int siftUp(int index) {
    while (index > 0) {
      int parent = (index - 1) >>> 1;
      if (order.compare(heap.get(parent), heap.get(index)) <= 0) break;
      swap(index, parent);
      index = parent;
    }
    return index;
  }

  private void siftDown(int index) {
    while (2 * index + 1 < heap.size()) {
      int child = 2 * index + 1;
      if (child + 1 < heap.size() && order.compare(heap.get(child + 1), heap.get(child)) < 0)
        child++;
      if (order.compare(heap.get(index), heap.get(child)) <= 0) break;
      swap(index, child);
      index = child;
    }
  }

  private void swap(int left, int right) {
    Node a = heap.get(left), b = heap.get(right);
    heap.set(left, b);
    heap.set(right, a);
    indices.put(b.getState(), left);
    indices.put(a.getState(), right);
  }
}
