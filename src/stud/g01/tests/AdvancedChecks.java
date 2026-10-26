package stud.g01.tests;

import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import stud.g01.heuristic.PuzzleHeuristics;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;
import stud.g01.runner.LabRunner;
import stud.g01.solver.IdAStar;

/** Bounded independent 15-puzzle oracles and regression checks for solver limits. */
public final class AdvancedChecks {
  private static long checks;
  private static int searches;
  private static final int MAX_ORACLE_STATES = 30000;
  private static final int[] STANDARD_4 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
  private static final HeuristicType[] TYPES = {
    HeuristicType.MISPLACED, HeuristicType.MANHATTAN, HeuristicType.DISJOINT_PATTERN
  };

  public static void main(String[] args) {
    long start = System.nanoTime();
    String[] properties = {"lab.patternSize", "lab.ttPower", "lab.timeoutSeconds", "lab.nodeLimit"};
    Map<String, String> saved = new LinkedHashMap<>();
    for (String property : properties) saved.put(property, System.getProperty(property));
    try {
      System.setProperty("lab.ttPower", "8");
      System.setProperty("lab.timeoutSeconds", "20");
      System.setProperty("lab.nodeLimit", "1000000");
      // Production-sized 6-6-3 PDB at the standard target, with a bounded exact oracle.
      System.setProperty("lab.patternSize", "6");
      Map<Long, Integer> standard = exactWithin(STANDARD_4, 4, 10);
      checkHeuristicsAndSearches(STANDARD_4, 4, standard, 10, TYPES);
      checkCustomPredictor(STANDARD_4, 4, standard);

      // Smaller patterns keep arbitrary-target cache construction bounded; same code path.
      System.setProperty("lab.patternSize", "3");
      int[] offDiagonal = moveArray(moveArray(moveArray(STANDARD_4, 11), 10), 6);
      int[] diagonal = moveArray(offDiagonal, 5);
      int arbitraryStates = 0;
      for (int[] target : List.of(offDiagonal, diagonal)) {
        Map<Long, Integer> exact = exactWithin(target, 4, 8);
        arbitraryStates += exact.size();
        checkHeuristicsAndSearches(
            target,
            4,
            exact,
            8,
            new HeuristicType[] {HeuristicType.MANHATTAN, HeuristicType.DISJOINT_PATTERN});
      }

      // This used to expose the invalid assumption that a 3-tile 2x2 PDB is connected.
      int[] goal2 = {1, 2, 3, 0};
      Map<Long, Integer> two = exactWithin(goal2, 2, 10);
      check(two.size() == 12, "2x2 component must contain 12 states");
      checkHeuristicsAndSearches(goal2, 2, two, 6, TYPES);
      checkCustomPredictor(goal2, 2, two);
      checkLimits(standard);
      System.out.printf(
          Locale.ROOT,
          "{\"status\":\"PASS\",\"suite\":\"AdvancedChecks\",\"assertions\":%d,"
              + "\"optimal_searches_checked\":%d,\"standard_15puzzle_depth\":10,"
              + "\"standard_15puzzle_states\":%d,\"arbitrary_15puzzle_depth\":8,"
              + "\"arbitrary_goal_states_total\":%d,\"two_by_two_states\":%d,"
              + "\"wall_seconds\":%.6f}%n",
          checks,
          searches,
          standard.size(),
          arbitraryStates,
          two.size(),
          (System.nanoTime() - start) / 1e9);
    } finally {
      for (var entry : saved.entrySet()) {
        if (entry.getValue() == null) System.clearProperty(entry.getKey());
        else System.setProperty(entry.getKey(), entry.getValue());
      }
    }
  }

  private static void checkHeuristicsAndSearches(
      int[] target, int size, Map<Long, Integer> oracle, int maxDepth, HeuristicType[] types) {
    PuzzleBoard goal = new PuzzleBoard(size, target);
    for (HeuristicType type : types) {
      PuzzleHeuristics predictor = new PuzzleHeuristics(type);
      check(predictor.heuristics(goal, goal) == 0, "goal estimate must be zero");
      for (var entry : oracle.entrySet()) {
        int[] state = unpack(entry.getKey(), target.length);
        int h = predictor.heuristics(new PuzzleBoard(size, state), goal);
        check(h >= 0 && h <= entry.getValue(), "15-puzzle admissibility: " + type);
        int blank = findBlank(state);
        for (int next : neighbors(blank, size)) {
          int[] child = moveArray(state, next);
          int childH = predictor.heuristics(new PuzzleBoard(size, child), goal);
          check(Math.abs(h - childH) <= 1, "unit-edge consistency: " + type);
        }
      }
      checkCursor(target, size, predictor);
      for (long state : sampleByDepth(oracle, maxDepth)) {
        NPuzzleProblem problem =
            new NPuzzleProblem(new PuzzleBoard(size, unpack(state, target.length)), goal, size);
        Deque<Node> a = new BestFirstSearcher(new PqFrontier(), predictor).search(problem);
        check(a != null && a.size() == oracle.get(state), "A* differs from independent BFS");
        LabRunner.verifyPath(problem, a);
        searches++;
        IdAStar ida = new IdAStar(new PqFrontier(), predictor);
        Deque<Node> b = ida.search(problem);
        check(
            b != null && ida.getStatus().equals("SOLVED") && b.size() == oracle.get(state),
            "IDA* differs from independent BFS: " + ida.getStatus());
        LabRunner.verifyPath(problem, b);
        searches++;
      }
    }
  }

  private static void checkCursor(int[] target, int size, PuzzleHeuristics predictor) {
    PuzzleBoard goal = new PuzzleBoard(size, target);
    int[] current = target.clone();
    PuzzleHeuristics.Cursor cursor = predictor.prepare(goal).cursor(current);
    Random random = new Random(20261026L + predictor.getType().ordinal() + size);
    for (int step = 0; step < 600; step++) {
      int before = predictor.heuristics(new PuzzleBoard(size, current), goal);
      check(cursor.value() == before, "incremental cursor drift before move");
      int blank = findBlank(current);
      int[] adjacent = neighbors(blank, size);
      int next = adjacent[random.nextInt(adjacent.length)], tile = current[next];
      int[] child = moveArray(current, next);
      cursor.move(tile, next, blank);
      int childH = predictor.heuristics(new PuzzleBoard(size, child), goal);
      check(cursor.value() == childH, "incremental cursor differs from fresh estimate");
      cursor.move(tile, blank, next);
      check(cursor.value() == before, "undo must restore cursor exactly");
      cursor.move(tile, next, blank);
      current = child;
    }
  }

  private static void checkCustomPredictor(int[] target, int size, Map<Long, Integer> oracle) {
    PuzzleBoard goal = new PuzzleBoard(size, target);
    long state =
        oracle.entrySet().stream()
            .filter(e -> e.getValue() == 6)
            .findFirst()
            .orElseThrow()
            .getKey();
    NPuzzleProblem problem =
        new NPuzzleProblem(new PuzzleBoard(size, unpack(state, target.length)), goal, size);
    for (int power : new int[] {0, 1, 8}) {
      System.setProperty("lab.ttPower", Integer.toString(power));
      int[] calls = {0};
      Predictor zero =
          (board, end) -> {
            calls[0]++;
            return 0;
          };
      IdAStar solver = new IdAStar(new PqFrontier(), zero);
      Deque<Node> path = solver.search(problem);
      check(calls[0] > 1, "custom zero predictor must be used");
      check(
          path != null && path.size() == 6 && solver.getStatus().equals("SOLVED"),
          "TT disabled/collision-heavy zero-heuristic result");
      LabRunner.verifyPath(problem, path);
      searches++;
    }
    System.setProperty("lab.ttPower", "8");
  }

  private static void checkLimits(Map<Long, Integer> oracle) {
    PuzzleBoard goal = new PuzzleBoard(4, STANDARD_4);
    long state =
        oracle.entrySet().stream()
            .filter(e -> e.getValue() == 8)
            .findFirst()
            .orElseThrow()
            .getKey();
    NPuzzleProblem problem = new NPuzzleProblem(new PuzzleBoard(4, unpack(state, 16)), goal, 4);
    for (String algorithm : List.of("astar", "ida")) {
      LabRunner.Result limited = LabRunner.run(problem, algorithm, "manhattan", "heap", 10, 1);
      check(
          limited.path() == null && limited.status().equals("NODE_LIMIT"),
          "node limit must not claim unsolvable");
      check(limited.expanded() <= 1, "node limit must bound expanded nodes");
      LabRunner.Result timeout =
          LabRunner.run(problem, algorithm, "manhattan", "heap", 0.000000001, 1000000);
      check(
          timeout.path() == null && timeout.status().equals("TIMEOUT"),
          "timeout must not claim unsolvable");
    }
    System.setProperty("lab.timeoutSeconds", "0.000000001");
    System.setProperty("lab.nodeLimit", "1000000");
    IdAStar reused = new IdAStar(new PqFrontier(), new PuzzleHeuristics(HeuristicType.MANHATTAN));
    check(
        reused.search(problem) == null && reused.getStatus().equals("TIMEOUT"),
        "reusable solver times out");
    System.setProperty("lab.timeoutSeconds", "20");
    Deque<Node> recovered = reused.search(problem);
    check(
        recovered != null && recovered.size() == 8 && reused.getStatus().equals("SOLVED"),
        "reuse after timeout resets state");
    LabRunner.verifyPath(problem, recovered);
    searches++;
    int[] impossible = STANDARD_4.clone();
    impossible[0] = 2;
    impossible[1] = 1;
    check(
        reused.search(new NPuzzleProblem(new PuzzleBoard(4, impossible), goal, 4)) == null
            && reused.getStatus().equals("UNSOLVABLE")
            && reused.getExpandedCount() == 0,
        "unsolvable after solved resets counters and status");
  }

  /** Exact shortest distances up to a strict depth/state budget, using array transitions. */
  private static Map<Long, Integer> exactWithin(int[] target, int size, int maximumDepth) {
    Map<Long, Integer> distance = new LinkedHashMap<>();
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    distance.put(pack(target), 0);
    queue.add(target.clone());
    while (!queue.isEmpty()) {
      int[] state = queue.remove();
      int depth = distance.get(pack(state));
      if (depth == maximumDepth) continue;
      for (int next : neighbors(findBlank(state), size)) {
        int[] child = moveArray(state, next);
        if (distance.putIfAbsent(pack(child), depth + 1) == null) {
          if (distance.size() > MAX_ORACLE_STATES)
            throw new AssertionError("Oracle state budget exceeded");
          queue.add(child);
        }
      }
    }
    return distance;
  }

  private static List<Long> sampleByDepth(Map<Long, Integer> oracle, int maxDepth) {
    List<Long> sample = new ArrayList<>();
    boolean[] selected = new boolean[maxDepth + 1];
    for (var entry : oracle.entrySet()) {
      int depth = entry.getValue();
      if (depth <= maxDepth && !selected[depth]) {
        sample.add(entry.getKey());
        selected[depth] = true;
      }
    }
    for (int depth = 0; depth <= maxDepth; depth++)
      check(selected[depth], "BFS depth layer missing");
    return sample;
  }

  private static int[] moveArray(int[] input, int destination) {
    int[] output = input.clone();
    int blank = findBlank(input);
    output[blank] = input[destination];
    output[destination] = 0;
    return output;
  }

  private static int findBlank(int[] state) {
    for (int i = 0; i < state.length; i++) if (state[i] == 0) return i;
    throw new AssertionError("Missing blank");
  }

  private static int[] neighbors(int blank, int size) {
    int[] result = new int[4];
    int count = 0;
    for (int q = 0; q < size * size; q++) {
      if (Math.abs(q / size - blank / size) + Math.abs(q % size - blank % size) == 1)
        result[count++] = q;
    }
    return Arrays.copyOf(result, count);
  }

  private static long pack(int[] state) {
    long packed = 0;
    for (int i = 0; i < state.length; i++) packed |= (long) state[i] << (4 * i);
    return packed;
  }

  private static int[] unpack(long packed, int cells) {
    int[] state = new int[cells];
    for (int i = 0; i < cells; i++) state[i] = (int) (packed >>> (4 * i)) & 15;
    return state;
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
