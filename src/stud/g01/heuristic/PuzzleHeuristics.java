package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import java.util.*;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** Admissible puzzle estimates. The blank is never counted as a tile. */
public final class PuzzleHeuristics implements Predictor {
  private final HeuristicType type;
  private Prepared prepared;

  public PuzzleHeuristics(HeuristicType type) {
    this.type = Objects.requireNonNull(type);
  }

  public HeuristicType getType() {
    return type;
  }

  public synchronized Prepared prepare(PuzzleBoard goal) {
    int[] tiles = goal.toArray();
    if (prepared == null || !Arrays.equals(prepared.goal, tiles))
      prepared = new Prepared(tiles, goal.getSize(), type);
    return prepared;
  }

  @Override
  public int heuristics(State state, State goal) {
    PuzzleBoard board = (PuzzleBoard) state;
    return prepare((PuzzleBoard) goal).cursor(board.toArray()).value();
  }

  public static final class Prepared {
    final int[] goal;
    final int size;
    final HeuristicType type;
    final int[][] distance;
    final int[] goalPosition;
    final List<PatternDatabase.Pattern> patterns = new ArrayList<>();
    final int[] group, shift, mirrorGroup, mirrorShift;
    final boolean mirrored;

    Prepared(int[] goal, int size, HeuristicType type) {
      this.goal = goal.clone();
      this.size = size;
      this.type = type;
      int cells = goal.length;
      goalPosition = new int[cells];
      for (int p = 0; p < cells; p++) goalPosition[goal[p]] = p;
      distance = new int[cells][cells];
      for (int t = 1; t < cells; t++)
        for (int p = 0; p < cells; p++)
          distance[t][p] =
              Math.abs(p / size - goalPosition[t] / size)
                  + Math.abs(p % size - goalPosition[t] % size);
      group = new int[cells];
      shift = new int[cells];
      mirrorGroup = new int[cells];
      mirrorShift = new int[cells];
      Arrays.fill(group, -1);
      Arrays.fill(mirrorGroup, -1);
      boolean usePatterns = type == HeuristicType.DISJOINT_PATTERN && size <= 4;
      mirrored =
          usePatterns
              && goalPosition[0] / size == goalPosition[0] % size
              && Boolean.parseBoolean(System.getProperty("lab.pdbSymmetry", "true"));
      if (usePatterns) {
        int[] order =
            size == 4
                ? new int[] {0, 1, 4, 5, 8, 9, 2, 3, 6, 7, 10, 11, 12, 13, 14, 15}
                : java.util.stream.IntStream.range(0, cells).toArray();
        List<Integer> tiles = new ArrayList<>();
        for (int pos : order) if (goal[pos] != 0) tiles.add(goal[pos]);
        int maximum = size == 4 ? Integer.getInteger("lab.patternSize", 6) : size == 3 ? 4 : 2;
        if (maximum < 1 || maximum > 7)
          throw new IllegalArgumentException("Pattern size must be 1..7");
        for (int start = 0; start < tiles.size(); start += maximum) {
          int k = Math.min(maximum, tiles.size() - start), g = patterns.size();
          int[] target = new int[k];
          for (int j = 0; j < k; j++) {
            int t = tiles.get(start + j);
            group[t] = g;
            shift[t] = j * 4;
            target[j] = goalPosition[t];
          }
          patterns.add(PatternDatabase.load(size, target));
        }
        if (mirrored)
          for (int t = 1; t < cells; t++) {
            int pos = goalPosition[t], mapped = goal[(pos % size) * size + pos / size];
            mirrorGroup[t] = group[mapped];
            mirrorShift[t] = shift[mapped];
          }
      }
    }

    public Cursor cursor(int[] tiles) {
      return new Cursor(this, tiles);
    }

    public boolean usesPatterns() {
      return !patterns.isEmpty();
    }
  }

  /** Mutable, allocation-free incremental estimate for one search. */
  public static final class Cursor {
    private final Prepared p;
    private int manhattan, misplaced, sum, mirrorSum;
    private final int[] indices, mirrorIndices;

    Cursor(Prepared p, int[] board) {
      this.p = p;
      indices = new int[p.patterns.size()];
      mirrorIndices = new int[indices.length];
      for (int pos = 0; pos < board.length; pos++) {
        int t = board[pos];
        if (t == 0) continue;
        manhattan += p.distance[t][pos];
        if (pos != p.goalPosition[t]) misplaced++;
        if (indices.length > 0) {
          indices[p.group[t]] |= pos << p.shift[t];
          if (p.mirrored) mirrorIndices[p.mirrorGroup[t]] |= transpose(pos) << p.mirrorShift[t];
        }
      }
      for (int g = 0; g < indices.length; g++) {
        sum += p.patterns.get(g).distance(indices[g]);
        if (p.mirrored) mirrorSum += p.patterns.get(g).distance(mirrorIndices[g]);
      }
    }

    private int transpose(int pos) {
      return (pos % p.size) * p.size + pos / p.size;
    }

    public int value() {
      return p.type == HeuristicType.MISPLACED
          ? misplaced
          : Math.max(manhattan, Math.max(sum, mirrorSum));
    }

    public void move(int tile, int oldPosition, int newPosition) {
      manhattan += p.distance[tile][newPosition] - p.distance[tile][oldPosition];
      misplaced +=
          (newPosition != p.goalPosition[tile] ? 1 : 0)
              - (oldPosition != p.goalPosition[tile] ? 1 : 0);
      if (indices.length == 0) return;
      int g = p.group[tile];
      PatternDatabase.Pattern pattern = p.patterns.get(g);
      sum -= pattern.distance(indices[g]);
      indices[g] ^= (oldPosition ^ newPosition) << p.shift[tile];
      sum += pattern.distance(indices[g]);
      if (p.mirrored) {
        g = p.mirrorGroup[tile];
        pattern = p.patterns.get(g);
        mirrorSum -= pattern.distance(mirrorIndices[g]);
        mirrorIndices[g] ^=
            (transpose(oldPosition) ^ transpose(newPosition)) << p.mirrorShift[tile];
        mirrorSum += pattern.distance(mirrorIndices[g]);
      }
    }
  }
}
