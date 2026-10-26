package stud.g01.tests;

import core.solver.algorithm.heuristic.*;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import stud.g01.heuristic.PuzzleHeuristics;
import stud.g01.problem.npuzzle.*;
import stud.g01.queue.PqFrontier;
import stud.g01.runner.LabRunner;
import stud.g01.solver.IdAStar;

/** Exact independent BFS oracle; this is not a second copy of the search implementation. */
public final class VerificationSuite {
  private static long assertions;
  private static final int[] GOAL = {1, 2, 3, 4, 5, 6, 7, 8, 0};

  private static void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    long start = System.nanoTime();
    checkFramework();
    Map<Long, Integer> oracle = exactDistances(GOAL, 3);
    check(oracle.size() == 181440, "Wrong 8-puzzle reachable component size");
    int max = Collections.max(oracle.values());
    check(max == 31, "Wrong exact 8-puzzle diameter");
    PuzzleBoard goal = new PuzzleBoard(3, GOAL);
    List<PuzzleHeuristics> predictors =
        List.of(
            new PuzzleHeuristics(HeuristicType.MISPLACED),
            new PuzzleHeuristics(HeuristicType.MANHATTAN),
            new PuzzleHeuristics(HeuristicType.DISJOINT_PATTERN));
    long edges = 0;
    List<Map<Long, Integer>> cached = new ArrayList<>();
    for (PuzzleHeuristics predictor : predictors) {
      Map<Long, Integer> values = new HashMap<>(250000);
      for (var entry : oracle.entrySet()) {
        int h = predictor.heuristics(new PuzzleBoard(3, unpack(entry.getKey(), 9)), goal);
        check(h >= 0 && h <= entry.getValue(), "Inadmissible " + predictor.getType());
        values.put(entry.getKey(), h);
      }
      cached.add(values);
    }
    for (long state : oracle.keySet()) {
      int zero = zero(state, 9);
      for (int next : neighbors(zero, 3)) {
        long child = swap(state, zero, next);
        edges++;
        for (Map<Long, Integer> values : cached)
          check(values.get(state) <= 1 + values.get(child), "Inconsistent heuristic");
      }
      check(
          cached.get(1).get(state) >= cached.get(0).get(state),
          "Manhattan should dominate misplaced");
      check(
          cached.get(2).get(state) >= cached.get(1).get(state),
          "PDB+max should dominate Manhattan");
    }
    Random random = new Random(20261026L);
    List<Long> states = new ArrayList<>(oracle.keySet());
    Collections.sort(states);
    List<Long> chosen = new ArrayList<>();
    chosen.add(pack(GOAL));
    chosen.add(pack(new int[] {8, 6, 7, 2, 5, 4, 3, 0, 1}));
    chosen.add(pack(new int[] {6, 4, 7, 8, 5, 0, 3, 2, 1}));
    for (int i = 0; i < 30; i++) chosen.add(states.get(random.nextInt(states.size())));
    int searches = 0;
    System.setProperty("lab.timeoutSeconds", "20");
    System.setProperty("lab.nodeLimit", "100000000");
    // A*, all three estimates, checked against exact distances on a deterministic sample.
    for (long state : chosen)
      for (PuzzleHeuristics predictor : predictors) {
        NPuzzleProblem p = new NPuzzleProblem(new PuzzleBoard(3, unpack(state, 9)), goal, 3);
        var path = new BestFirstSearcher(new PqFrontier(), predictor).search(p);
        check(path != null && path.size() == oracle.get(state), "A* non-optimal result");
        LabRunner.verifyPath(p, path);
        searches++;
      }
    // IDA*: hard misplaced search is deliberately sampled only up to depth 20.
    for (long state : chosen)
      for (PuzzleHeuristics predictor : predictors) {
        if (predictor.getType() == HeuristicType.MISPLACED && oracle.get(state) > 20) continue;
        NPuzzleProblem p = new NPuzzleProblem(new PuzzleBoard(3, unpack(state, 9)), goal, 3);
        IdAStar solver = new IdAStar(new PqFrontier(), predictor);
        var path = solver.search(p);
        check(
            path != null && path.size() == oracle.get(state),
            "IDA* non-optimal result: " + solver.getStatus());
        LabRunner.verifyPath(p, path);
        searches++;
      }
    // Arbitrary goals (including off-diagonal blank): target-relative PDB and parity.
    for (int trial = 0; trial < 4; trial++) {
      int[] arbitrary = unpack(states.get(random.nextInt(states.size())), 9);
      Map<Long, Integer> distances = exactDistances(arbitrary, 3);
      PuzzleBoard target = new PuzzleBoard(3, arbitrary);
      PuzzleHeuristics predictor = new PuzzleHeuristics(HeuristicType.DISJOINT_PATTERN);
      for (int i = 0; i < 10; i++) {
        long state = states.get(random.nextInt(states.size()));
        NPuzzleProblem p = new NPuzzleProblem(new PuzzleBoard(3, unpack(state, 9)), target, 3);
        var path = new IdAStar(new PqFrontier(), predictor).search(p);
        check(path != null && path.size() == distances.get(state), "Arbitrary-goal IDA* mismatch");
        LabRunner.verifyPath(p, path);
        searches++;
      }
    }
    // Unsolvable inputs must remain distinct from timeout and produce no path.
    NPuzzleProblem unsolvable =
        new NPuzzleProblem(new PuzzleBoard(3, new int[] {2, 1, 3, 4, 5, 6, 7, 8, 0}), goal, 3);
    IdAStar reuse = new IdAStar(new PqFrontier(), predictors.get(1));
    check(
        reuse.search(unsolvable) == null && reuse.getStatus().equals("UNSOLVABLE"),
        "Unsolvability status");
    System.setProperty("lab.timeoutSeconds", "0.000000001");
    NPuzzleProblem hard =
        new NPuzzleProblem(new PuzzleBoard(3, new int[] {8, 6, 7, 2, 5, 4, 3, 0, 1}), goal, 3);
    check(
        reuse.search(hard) == null && reuse.getStatus().equals("TIMEOUT"),
        "Timeout must not claim unsolvable");
    System.setProperty("lab.timeoutSeconds", "20");
    check(
        reuse.search(new NPuzzleProblem(goal, goal, 3)).isEmpty(),
        "Solved root path should be empty");
    long[] histogram = new long[32];
    for (int d : oracle.values()) histogram[d]++;
    String result =
        String.format(
            Locale.ROOT,
            "{\"status\":\"PASS\",\"seed\":20261026,\"reachable_8puzzle_states\":%d,\"directed_8puzzle_edges\":%d,\"diameter\":%d,\"heuristics\":3,\"optimal_searches_checked\":%d,\"assertions\":%d,\"wall_seconds\":%.6f,\"distance_histogram\":%s}\n",
            oracle.size(),
            edges,
            max,
            searches,
            assertions,
            (System.nanoTime() - start) / 1e9,
            Arrays.toString(histogram));
    Path verificationOutput =
        Path.of(System.getProperty("lab.verificationOutput", "build/verification.json"));
    Files.createDirectories(verificationOutput.toAbsolutePath().getParent());
    Files.writeString(verificationOutput, result);
    System.out.print(result);
  }

  private static void checkFramework() throws Exception {
    String manifest = Files.readString(Path.of("verification/framework-baseline.json"));
    var paths =
        Pattern.compile(
                "\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"sha256\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .matcher(manifest);
    int count = 0;
    while (paths.find()) {
      count++;
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(Files.readAllBytes(Path.of(paths.group(1)))));
      check(hash.equals(paths.group(2)), "Protected source changed: " + paths.group(1));
    }
    check(count == 26, "Baseline manifest missing entries");
  }

  private static Map<Long, Integer> exactDistances(int[] target, int size) {
    Map<Long, Integer> distances = new HashMap<>(250000);
    ArrayDeque<Long> queue = new ArrayDeque<>();
    long goal = pack(target);
    distances.put(goal, 0);
    queue.add(goal);
    while (!queue.isEmpty()) {
      long current = queue.remove();
      int d = distances.get(current), z = zero(current, size * size);
      for (int next : neighbors(z, size)) {
        long child = swap(current, z, next);
        if (distances.putIfAbsent(child, d + 1) == null) queue.add(child);
      }
    }
    return distances;
  }

  private static int[] neighbors(int blank, int size) {
    int[] n = new int[4];
    int count = 0;
    for (int q = 0; q < size * size; q++)
      if (Math.abs(q / size - blank / size) + Math.abs(q % size - blank % size) == 1)
        n[count++] = q;
    return Arrays.copyOf(n, count);
  }

  private static long pack(int[] a) {
    long result = 0;
    for (int i = 0; i < a.length; i++) result |= (long) a[i] << (4 * i);
    return result;
  }

  private static int[] unpack(long key, int count) {
    int[] a = new int[count];
    for (int i = 0; i < count; i++) a[i] = (int) (key >>> (4 * i)) & 15;
    return a;
  }

  private static int zero(long key, int count) {
    for (int i = 0; i < count; i++) if (((key >>> (4 * i)) & 15) == 0) return i;
    throw new AssertionError("No blank");
  }

  private static long swap(long key, int zero, int next) {
    long tile = (key >>> (next * 4)) & 15;
    return key ^ (tile << (zero * 4)) ^ (tile << (next * 4));
  }
}
