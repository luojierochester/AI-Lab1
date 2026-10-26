package stud.g01.tests;

import core.problem.Problem;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.queue.PqFrontier;
import stud.g01.runner.PuzzleFeeder;

/** Fast deterministic checks, run independently of the performance experiments. */
public final class ModelChecks {
  private static int checks;

  public static void main(String[] args) {
    ArrayList<int[]> permutations = new ArrayList<>();
    permute(new int[] {0, 1, 2, 3}, 0, permutations);
    checkBoards(permutations);
    checkSolvability(permutations);
    checkInput();
    checkFrontiers(permutations);
    System.out.println(
        "ModelChecks PASS: "
            + checks
            + " checks; exhaustive 2x2 parity,"
            + " immutable moves, strict input and randomized indexed-heap updates.");
  }

  private static void checkBoards(List<int[]> permutations) {
    for (int[] values : permutations) {
      int[] callerArray = values.clone();
      PuzzleBoard board = new PuzzleBoard(2, callerArray);
      PuzzleBoard duplicate = new PuzzleBoard(2, values);
      int hash = board.hashCode();
      callerArray[0] = -99;
      int[] exposed = board.toArray();
      exposed[1] = -99;
      check(Arrays.equals(board.toArray(), values), "board must not expose mutable storage");
      check(board.equals(duplicate) && hash == duplicate.hashCode(), "equal board hash contract");
      check(new HashSet<>(List.of(board)).contains(duplicate), "hash lookup with equal board");
      for (int i = 0; i < 4; i++) {
        check(((board.packed() >>> (4 * i)) & 15) == values[i], "packed tile order");
      }
      int zero = 0;
      while (values[zero] != 0) zero++;
      check(board.blankIndex() == zero, "blank index");
      for (PuzzleMove move : board.actions()) {
        int row = zero / 2 + move.dr(), col = zero % 2 + move.dc();
        boolean legal = row >= 0 && row < 2 && col >= 0 && col < 2;
        check(board.canMove(move) == legal, "move boundary");
        if (legal) {
          int[] expected = values.clone();
          int target = row * 2 + col;
          expected[zero] = expected[target];
          expected[target] = 0;
          PuzzleBoard child = board.next(move);
          check(Arrays.equals(child.toArray(), expected), "blank transition");
          check(child.equals(new PuzzleBoard(2, expected)), "transition equals constructor");
          check(child.hashCode() == new PuzzleBoard(2, expected).hashCode(), "transition hash");
          check(
              Arrays.equals(board.toArray(), values) && board.hashCode() == hash,
              "parent unchanged");
          check(move.stepCost() == 1, "unit move cost");
        } else {
          reject(() -> board.next(move), "illegal move must be rejected");
        }
      }
    }
    reject(() -> new PuzzleBoard(1, new int[] {0}), "unsupported size");
    reject(() -> new PuzzleBoard(2, new int[] {0, 1, 2}), "wrong tile count");
    reject(() -> new PuzzleBoard(2, new int[] {0, 1, 1, 3}), "duplicate tile");
    reject(() -> new PuzzleBoard(2, new int[] {0, 1, 2, 4}), "out-of-range tile");
    reject(() -> new PuzzleBoard(2, new int[] {-1, 1, 2, 3}), "negative tile");
  }

  private static void checkSolvability(List<int[]> permutations) {
    // Independent array-based BFS is the oracle; it does not use the parity formula.
    for (int[] goal : permutations) {
      Set<String> reachable = reachable2x2(goal);
      check(reachable.size() == 12, "2x2 connected component size");
      for (int[] initial : permutations) {
        NPuzzleProblem problem =
            new NPuzzleProblem(new PuzzleBoard(2, initial), new PuzzleBoard(2, goal), 2);
        check(
            problem.solvable() == reachable.contains(Arrays.toString(initial)),
            "arbitrary 2x2 goal parity");
      }
    }
    for (int size = 3; size <= 4; size++) {
      int[] ordered = new int[size * size];
      for (int i = 0; i < ordered.length - 1; i++) ordered[i] = i + 1;
      PuzzleBoard standard = new PuzzleBoard(size, ordered);
      PuzzleBoard arbitrary = standard.next(PuzzleMove.UP).next(PuzzleMove.LEFT);
      check(
          new NPuzzleProblem(standard, arbitrary, size).solvable(), "noncanonical reachable goal");
      check(new NPuzzleProblem(arbitrary, arbitrary, size).solvable(), "identity goal");
      int[] swapped = arbitrary.toArray();
      int a = 0, b = 0;
      for (int i = 0; i < swapped.length; i++) {
        if (swapped[i] == 1) a = i;
        if (swapped[i] == 2) b = i;
      }
      swapped[a] = 2;
      swapped[b] = 1;
      check(
          !new NPuzzleProblem(new PuzzleBoard(size, swapped), arbitrary, size).solvable(),
          "odd tile swap impossible");
      check(
          new NPuzzleProblem(arbitrary, standard, size).applicable(arbitrary, PuzzleMove.RIGHT),
          "problem applicability");
    }
  }

  private static Set<String> reachable2x2(int[] goal) {
    Set<String> visited = new HashSet<>();
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    queue.add(goal.clone());
    visited.add(Arrays.toString(goal));
    int[][] adjacent = {{1, 2}, {0, 3}, {0, 3}, {1, 2}};
    while (!queue.isEmpty()) {
      int[] state = queue.remove();
      int blank = 0;
      while (state[blank] != 0) blank++;
      for (int target : adjacent[blank]) {
        int[] next = state.clone();
        next[blank] = next[target];
        next[target] = 0;
        if (visited.add(Arrays.toString(next))) queue.add(next);
      }
    }
    return visited;
  }

  private static void checkInput() {
    PuzzleFeeder feeder = new PuzzleFeeder();
    ArrayList<Problem> problems =
        feeder.getProblems(new ArrayList<>(List.of("# comment", "", " 2  1 2 3 0\t1 2 0 3 ")));
    check(problems.size() == 1 && problems.get(0).solvable(), "whitespace and comment input");
    reject(() -> feeder.getProblems(new ArrayList<>()), "empty input");
    reject(() -> feeder.getProblems(new ArrayList<>(List.of("2 1 2 3"))), "short record");
    reject(
        () -> feeder.getProblems(new ArrayList<>(List.of("2 1 2 3 0 1 2 3 0 99"))), "long record");
    reject(
        () -> feeder.getProblems(new ArrayList<>(List.of("2 1 2 3 0 1 2 2 0"))), "invalid target");
    reject(
        () -> feeder.getProblems(new ArrayList<>(List.of("2 1 2 x 0 1 2 3 0"))),
        "noninteger input");
  }

  private static void checkFrontiers(List<int[]> permutations) {
    List<PuzzleBoard> boards = new ArrayList<>();
    for (int[] values : permutations) boards.add(new PuzzleBoard(2, values));
    for (EvaluationType type : EvaluationType.values()) {
      Comparator<Node> order =
          type == EvaluationType.FULL
              ? Comparator.comparingInt(Node::evaluation)
                  .thenComparing(Comparator.comparingInt(Node::getPathCost).reversed())
              : Node.evaluator(type);
      PqFrontier frontier = new PqFrontier(type);
      Map<PuzzleBoard, Node> oracle = new HashMap<>();
      Random random = new Random(20261026L + type.ordinal());
      for (int iteration = 0; iteration < 2000; iteration++) {
        if (!oracle.isEmpty() && random.nextInt(4) == 0) {
          Node actual = frontier.poll();
          check(actual != null && oracle.get(actual.getState()) == actual, "pop current node");
          for (Node candidate : oracle.values())
            check(order.compare(actual, candidate) <= 0, "minimum-priority pop");
          oracle.remove(actual.getState());
          check(!frontier.contains(actual), "popped state removed from index");
        } else {
          int index = random.nextInt(boards.size());
          PuzzleBoard board = boards.get(index);
          Node next = new Node(board, null, null, random.nextInt(100), index % 7);
          Node old = oracle.get(board);
          boolean shouldAccept = old == null || next.getPathCost() < old.getPathCost();
          check(frontier.offer(next) == shouldAccept, "same-state smaller-g update");
          if (shouldAccept) oracle.put(board, next);
          check(
              frontier.contains(new Node(new PuzzleBoard(2, board.toArray()), null, null, 0)),
              "value-based lookup");
        }
        check(
            frontier.size() == oracle.size() && frontier.isEmpty() == oracle.isEmpty(),
            "heap/index size consistency");
        if (iteration % 173 == 0) {
          frontier.clear();
          oracle.clear();
          check(frontier.poll() == null && frontier.isEmpty(), "clear resets heap and index");
        }
      }
    }
  }

  private static void permute(int[] values, int offset, List<int[]> out) {
    if (offset == values.length) {
      out.add(values.clone());
      return;
    }
    for (int i = offset; i < values.length; i++) {
      int temp = values[offset];
      values[offset] = values[i];
      values[i] = temp;
      permute(values, offset + 1, out);
      temp = values[offset];
      values[offset] = values[i];
      values[i] = temp;
    }
  }

  private static void reject(Runnable operation, String label) {
    try {
      operation.run();
    } catch (IllegalArgumentException expected) {
      checks++;
      return;
    }
    throw new AssertionError(label);
  }

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
}
