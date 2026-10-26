package stud.g01.solver;

import core.problem.*;
import core.solver.algorithm.heuristic.*;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.*;
import java.util.*;
import stud.g01.heuristic.PuzzleHeuristics;
import stud.g01.problem.npuzzle.*;

/** Optimal IDA* with inverse-move pruning and incremental admissible estimates. */
public class IdAStar extends AbstractSearcher {
  private final Predictor predictor;
  private long generated, expanded, deadline, nodeLimit, searchStart;
  private double preparationSeconds, searchSeconds;
  private String status = "NOT_STARTED";
  private int[] board, path;
  private int[][] neighbors;
  private int solutionDepth;
  private PuzzleHeuristics.Cursor estimate;
  private PuzzleBoard targetBoard;
  private long goalPacked;
  private int puzzleSize;
  private boolean customPredictor;
  private long[] keys;
  private short[] depths;
  private int[] stamps;
  private int stamp;
  private static final int FOUND = -1;

  public IdAStar(Frontier frontier) {
    this(frontier, new PuzzleHeuristics(HeuristicType.MANHATTAN));
  }

  public IdAStar(Frontier frontier, Predictor predictor) {
    super(frontier);
    this.predictor = Objects.requireNonNull(predictor);
  }

  public String getStatus() {
    return status;
  }

  public long getGeneratedCount() {
    return generated;
  }

  public long getExpandedCount() {
    return expanded;
  }

  public double getPreparationSeconds() {
    return preparationSeconds;
  }

  public double getSearchSeconds() {
    return searchSeconds;
  }

  @Override
  public Deque<Node> search(Problem problem) {
    generated = expanded = 0;
    nodesGenerated = nodesExpanded = 0;
    preparationSeconds = searchSeconds = 0;
    if (!problem.solvable()) {
      status = "UNSOLVABLE";
      return null;
    }
    if (!(problem instanceof NPuzzleProblem))
      throw new IllegalArgumentException("IdAStar requires NPuzzleProblem");
    NPuzzleProblem puzzle = (NPuzzleProblem) problem;
    PuzzleBoard initial = puzzle.getInitialBoard(), goal = puzzle.getGoalBoard();
    board = initial.toArray();
    int size = initial.getSize();
    puzzleSize = size;
    targetBoard = goal;
    goalPacked = goal.packed();
    if (size > 4) throw new IllegalArgumentException("Packed IDA* supports sizes up to 4");
    long begin = System.nanoTime();
    customPredictor = !(predictor instanceof PuzzleHeuristics);
    PuzzleHeuristics h =
        customPredictor
            ? new PuzzleHeuristics(HeuristicType.MANHATTAN)
            : (PuzzleHeuristics) predictor;
    estimate = h.prepare(goal).cursor(board);
    preparationSeconds = (System.nanoTime() - begin) / 1e9;
    neighbors = new int[board.length][];
    for (int p = 0; p < board.length; p++) {
      int[] temp = new int[4];
      int count = 0;
      if (p >= size) temp[count++] = p - size;
      if (p + size < board.length) temp[count++] = p + size;
      if (p % size > 0) temp[count++] = p - 1;
      if (p % size + 1 < size) temp[count++] = p + 1;
      neighbors[p] = Arrays.copyOf(temp, count);
    }
    path = new int[256];
    int power = Integer.getInteger("lab.ttPower", 21);
    if (power < 0 || power > 25) throw new IllegalArgumentException("lab.ttPower must be 0..25");
    int count = power == 0 ? 0 : 1 << power;
    keys = new long[count];
    depths = new short[count];
    stamps = new int[count];
    stamp = 0;
    double seconds = Double.parseDouble(System.getProperty("lab.timeoutSeconds", "60"));
    nodeLimit = Long.getLong("lab.nodeLimit", Long.MAX_VALUE);
    searchStart = System.nanoTime();
    deadline = seconds <= 0 ? Long.MAX_VALUE : searchStart + (long) (seconds * 1e9);
    int bound = currentHeuristic();
    long packed = initial.packed();
    status = "SEARCHING";
    try {
      while (true) {
        stamp++;
        int next = dfs(initial.blankIndex(), -1, 0, bound, packed);
        if (next == FOUND) {
          status = "SOLVED";
          break;
        }
        if (next == Integer.MAX_VALUE) {
          status = "UNSOLVABLE";
          return null;
        }
        bound = next;
      }
    } catch (SearchLimit e) {
      status = e.getMessage();
      return null;
    } finally {
      searchSeconds = (System.nanoTime() - searchStart) / 1e9;
      nodesGenerated = (int) Math.min(Integer.MAX_VALUE, generated);
      nodesExpanded = (int) Math.min(Integer.MAX_VALUE, expanded);
      keys = null;
      depths = null;
      stamps = null;
    }
    Deque<Node> result = new ArrayDeque<>();
    PuzzleBoard state = initial;
    Node parent = new Node(state, null, null, 0);
    int blank = initial.blankIndex();
    for (int depth = 0; depth < solutionDepth; depth++) {
      int to = path[depth], delta = to - blank;
      PuzzleMove move =
          delta == -size
              ? PuzzleMove.UP
              : delta == size ? PuzzleMove.DOWN : delta == -1 ? PuzzleMove.LEFT : PuzzleMove.RIGHT;
      state = (PuzzleBoard) state.next(move);
      Node node = new Node(state, parent, move, depth + 1);
      result.addLast(node);
      parent = node;
      blank = to;
    }
    return result;
  }

  private int dfs(int blank, int previous, int depth, int bound, long packed) {
    int h = currentHeuristic(), f = depth + h;
    if (f > bound) return f;
    if (packed == goalPacked) {
      solutionDepth = depth;
      return FOUND;
    }
    if (expanded >= nodeLimit) throw new SearchLimit("NODE_LIMIT");
    if ((expanded & 16383) == 0) {
      if (System.nanoTime() > deadline) throw new SearchLimit("TIMEOUT");
      if (Thread.currentThread().isInterrupted()) throw new SearchLimit("INTERRUPTED");
    }
    if (keys.length > 0) {
      int slot = (int) mix(packed) & (keys.length - 1);
      if (stamps[slot] == stamp && keys[slot] == packed && depths[slot] <= depth)
        return Integer.MAX_VALUE;
      keys[slot] = packed;
      depths[slot] = (short) depth;
      stamps[slot] = stamp;
    }
    expanded++;
    int minimum = Integer.MAX_VALUE;
    for (int next : neighbors[blank]) {
      if (next == previous) continue;
      int tile = board[next];
      generated++;
      board[blank] = tile;
      board[next] = 0;
      estimate.move(tile, next, blank);
      long child = packed ^ ((long) tile << (next * 4)) ^ ((long) tile << (blank * 4));
      path[depth] = next;
      int result = dfs(next, blank, depth + 1, bound, child);
      estimate.move(tile, blank, next);
      board[next] = tile;
      board[blank] = 0;
      if (result == FOUND) return FOUND;
      if (result < minimum) minimum = result;
    }
    return minimum;
  }

  private int currentHeuristic() {
    return customPredictor
        ? predictor.heuristics(new PuzzleBoard(puzzleSize, board), targetBoard)
        : estimate.value();
  }

  private static long mix(long x) {
    x ^= x >>> 33;
    x *= 0xff51afd7ed558ccdl;
    x ^= x >>> 33;
    x *= 0xc4ceb9fe1a85ec53l;
    return x ^ (x >>> 33);
  }

  private static final class SearchLimit extends RuntimeException {
    SearchLimit(String message) {
      super(message);
    }
  }
}
