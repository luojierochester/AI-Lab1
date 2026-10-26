package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.*;
import core.solver.algorithm.searcher.*;
import core.solver.queue.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import stud.g01.heuristic.PuzzleHeuristics;
import stud.g01.problem.npuzzle.*;
import stud.g01.queue.PqFrontier;
import stud.g01.solver.IdAStar;
import stud.queue.ListFrontier;

/** Reproducible experiment entry point; leaves the supplied core classes unchanged. */
public final class LabRunner {
  private LabRunner() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0 || args[0].equals("help")) {
      usage();
      return;
    }
    Map<String, String> options = parse(args);
    if (args[0].equals("verify")) {
      stud.g01.tests.ModelChecks.main(new String[0]);
      stud.g01.tests.VerificationSuite.main(new String[0]);
      return;
    }
    String input = options.getOrDefault("input", "resources/problems.txt");
    Path requestedOutput =
        Path.of(
            options.getOrDefault(
                "output",
                args[0].equals("benchmark") ? "results/benchmark.csv" : "results/solution.npz"));
    writeMetadata(requestedOutput, args, input, options);
    var problems =
        new PuzzleFeeder()
            .getProblems(
                new ArrayList<>(Files.readAllLines(Path.of(input), StandardCharsets.UTF_8)));
    if (args[0].equals("solve")) {
      int index = Integer.parseInt(options.getOrDefault("case", "1")) - 1;
      if (index < 0 || index >= problems.size())
        throw new IllegalArgumentException("case is outside the input file");
      String algorithm = options.getOrDefault("algorithm", "astar");
      String heuristic = options.getOrDefault("heuristic", "manhattan");
      String frontier = options.getOrDefault("frontier", "heap");
      double timeout = Double.parseDouble(options.getOrDefault("timeout", "60"));
      long limit =
          Long.parseLong(
              options.getOrDefault("nodes", algorithm.equals("astar") ? "1000000" : "500000000"));
      Result result =
          run((NPuzzleProblem) problems.get(index), algorithm, heuristic, frontier, timeout, limit);
      System.out.println(result.json());
      Path output = Path.of(options.getOrDefault("output", "results/solution.npz"));
      if (result.path != null)
        exportPath(output, (NPuzzleProblem) problems.get(index), result.path);
      else if (Files.isRegularFile(output)) {
        Path stale = Path.of(output + ".stale-" + System.currentTimeMillis());
        Files.move(output, stale);
        System.err.println("No solution path written; preserved older output at " + stale);
      }
      Path json = Path.of(output.toString().replaceAll("\\.npz$", "") + ".json");
      write(json, result.json() + "\n");
      if (!result.status.equals("SOLVED") && !result.status.equals("UNSOLVABLE")) System.exit(2);
    } else if (args[0].equals("benchmark")) {
      benchmark(problems, options);
    } else throw new IllegalArgumentException("Unknown command: " + args[0]);
  }

  private static void usage() {
    System.out.println(
        "NPuzzle laboratory: JDK 17+, no external Java dependencies\n"
            + "solve --input resources/problems3.txt --case 10 --algorithm ida --heuristic pdb"
            + " --timeout 60 --output results/hard15.npz\n"
            + "benchmark --input resources/problems.txt --size 3 --repeats 5 --output"
            + " results/benchmark.csv\n"
            + "verify\n"
            + "Algorithms: astar, ida. Heuristics: misplaced, manhattan, pdb. Frontiers: heap,"
            + " list.");
  }

  private static Map<String, String> parse(String[] args) {
    Map<String, String> out = new LinkedHashMap<>();
    Set<String> allowed =
        Set.of(
            "input",
            "case",
            "algorithm",
            "heuristic",
            "frontier",
            "timeout",
            "nodes",
            "output",
            "size",
            "repeats",
            "algorithms",
            "heuristics",
            "frontiers");
    for (int i = 1; i < args.length; i += 2) {
      if (!args[i].startsWith("--") || i + 1 == args.length)
        throw new IllegalArgumentException("Expected --option value");
      if (!allowed.contains(args[i].substring(2)))
        throw new IllegalArgumentException("Unknown option " + args[i]);
      out.put(args[i].substring(2), args[i + 1]);
    }
    return out;
  }

  private static HeuristicType type(String name) {
    return switch (name) {
      case "misplaced" -> HeuristicType.MISPLACED;
      case "manhattan" -> HeuristicType.MANHATTAN;
      case "pdb" -> HeuristicType.DISJOINT_PATTERN;
      default -> throw new IllegalArgumentException("Unknown heuristic " + name);
    };
  }

  public static Result run(
      NPuzzleProblem problem,
      String algorithm,
      String heuristic,
      String frontierType,
      double timeout,
      long nodeLimit) {
    if (!Double.isFinite(timeout) || timeout <= 0 || nodeLimit < 1)
      throw new IllegalArgumentException("Positive timeout and node limit required");
    System.setProperty("lab.timeoutSeconds", Double.toString(timeout));
    System.setProperty("lab.nodeLimit", Long.toString(nodeLimit));
    PuzzleHeuristics predictor = new PuzzleHeuristics(type(heuristic));
    long totalStart = System.nanoTime();
    long prepareStart = System.nanoTime();
    int initialHeuristic = predictor.heuristics(problem.getInitialBoard(), problem.getGoalBoard());
    double prepare = (System.nanoTime() - prepareStart) / 1e9;
    Comparator<Node> ordering =
        Comparator.comparingInt(Node::evaluation)
            .thenComparing(Comparator.comparingInt(Node::getPathCost).reversed())
            .thenComparingLong(n -> ((PuzzleBoard) n.getState()).packed());
    Frontier base =
        switch (frontierType) {
          case "heap" -> new PqFrontier(ordering);
          case "list" -> new ListFrontier(ordering);
          default -> throw new IllegalArgumentException("Unknown frontier " + frontierType);
        };
    GuardedFrontier guarded = new GuardedFrontier(base, timeout, nodeLimit);
    AbstractSearcher searcher =
        switch (algorithm) {
          case "astar" -> new BestFirstSearcher(guarded, predictor);
          case "ida" -> new IdAStar(base, predictor);
          default -> throw new IllegalArgumentException("Unknown algorithm " + algorithm);
        };
    long start = System.nanoTime();
    var bean = ManagementFactory.getThreadMXBean();
    boolean cpuSupported = bean.isCurrentThreadCpuTimeSupported();
    if (cpuSupported && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
    long cpuStart = cpuSupported ? bean.getCurrentThreadCpuTime() : 0;
    Deque<Node> path = null;
    String status;
    try {
      path = searcher.search(problem);
      status = path == null ? "UNSOLVABLE" : "SOLVED";
      if (searcher instanceof IdAStar ida && path == null) status = ida.getStatus();
    } catch (SearchLimit ex) {
      status = ex.getMessage();
    }
    double wall = (System.nanoTime() - start) / 1e9;
    double cpu = cpuSupported ? (bean.getCurrentThreadCpuTime() - cpuStart) / 1e9 : Double.NaN;
    if (path != null) verifyPath(problem, path);
    long generated = searcher.nodesGenerated(), expanded = searcher.nodesExpanded();
    if (searcher instanceof IdAStar ida) {
      generated = ida.getGeneratedCount();
      expanded = ida.getExpandedCount();
    }
    return new Result(
        status,
        algorithm,
        heuristic,
        frontierType,
        path,
        wall,
        cpu,
        prepare,
        (System.nanoTime() - totalStart) / 1e9,
        generated,
        expanded,
        algorithm.equals("ida") ? -1 : guarded.peak,
        initialHeuristic);
  }

  /** Validate every transition independently of the solver and of Action.next(). */
  public static void verifyPath(NPuzzleProblem problem, Deque<Node> path) {
    int[] previous = problem.getInitialBoard().toArray();
    int size = problem.getInitialBoard().getSize(), step = 0;
    for (Node node : path) {
      int[] current = ((PuzzleBoard) node.getState()).toArray();
      int z = -1, nz = -1, changes = 0;
      if (current.length != previous.length) throw new AssertionError("Board dimension changed");
      for (int i = 0; i < current.length; i++) {
        if (previous[i] == 0) z = i;
        if (current[i] == 0) nz = i;
        if (previous[i] != current[i]) changes++;
      }
      if (changes != 2
          || Math.abs(z / size - nz / size) + Math.abs(z % size - nz % size) != 1
          || previous[nz] != current[z]) throw new AssertionError("Illegal path step " + step);
      if (node.getPathCost() != ++step) throw new AssertionError("Wrong path cost");
      previous = current;
    }
    if (!Arrays.equals(previous, problem.getGoalBoard().toArray()))
      throw new AssertionError("Path does not reach target");
  }

  public static void exportPath(Path output, NPuzzleProblem problem, Deque<Node> path)
      throws Exception {
    verifyPath(problem, path);
    StringBuilder text =
        new StringBuilder("NPZ1\n")
            .append(problem.getInitialBoard().getSize())
            .append(' ')
            .append(path.size())
            .append('\n');
    appendBoard(text, problem.getInitialBoard());
    for (Node node : path) appendBoard(text, (PuzzleBoard) node.getState());
    write(output, text.toString());
  }

  private static void appendBoard(StringBuilder out, PuzzleBoard board) {
    for (int tile : board.toArray()) out.append(tile).append(' ');
    out.setLength(out.length() - 1);
    out.append('\n');
  }

  private static void benchmark(ArrayList<Problem> problems, Map<String, String> options)
      throws Exception {
    int size = Integer.parseInt(options.getOrDefault("size", "3"));
    int repeats = Integer.parseInt(options.getOrDefault("repeats", "5"));
    if (repeats < 1 || repeats > 100) throw new IllegalArgumentException("repeats must be 1..100");
    double timeout = Double.parseDouble(options.getOrDefault("timeout", "30"));
    String algorithms = options.getOrDefault("algorithms", "astar,ida");
    String heuristics = options.getOrDefault("heuristics", "misplaced,manhattan,pdb");
    String frontiers = options.getOrDefault("frontiers", "heap");
    Path output = Path.of(options.getOrDefault("output", "results/benchmark.csv"));
    write(
        output,
        "case,size,algorithm,heuristic,frontier,repeat,status,moves,initial_h,wall_seconds,cpu_seconds,preparation_seconds,total_seconds,generated,expanded,peak_frontier,path_verified\n");
    for (String algorithm : algorithms.split(","))
      for (String heuristic : heuristics.split(","))
        for (String frontier : frontiers.split(",")) {
          if (algorithm.equals("ida") && !frontier.equals("heap")) continue;
          // JIT/cache warmup on a tiny instance; never include warmup as an experimental row.
          int[] goal = new int[size * size];
          for (int j = 0; j < goal.length - 1; j++) goal[j] = j + 1;
          int[] easy = goal.clone();
          easy[easy.length - 1] = easy[easy.length - 2];
          easy[easy.length - 2] = 0;
          run(
              new NPuzzleProblem(new PuzzleBoard(size, easy), new PuzzleBoard(size, goal), size),
              algorithm,
              heuristic,
              frontier,
              timeout,
              1000000);
          for (int i = 0; i < problems.size(); i++) {
            NPuzzleProblem problem = (NPuzzleProblem) problems.get(i);
            if (problem.getInitialBoard().getSize() != size) continue;
            for (int repeat = 1; repeat <= repeats; repeat++) {
              long limit =
                  Long.parseLong(
                      options.getOrDefault(
                          "nodes", algorithm.equals("astar") ? "1000000" : "500000000"));
              Result r = run(problem, algorithm, heuristic, frontier, timeout, limit);
              Files.writeString(
                  output,
                  r.csv(i + 1, size, repeat) + "\n",
                  StandardCharsets.UTF_8,
                  StandardOpenOption.APPEND);
              System.out.printf(
                  Locale.ROOT,
                  "case=%d %s/%s/%s repeat=%d %s moves=%d wall=%.6fs expanded=%d%n",
                  i + 1,
                  algorithm,
                  heuristic,
                  frontier,
                  repeat,
                  r.status,
                  r.path == null ? -1 : r.path.size(),
                  r.wall,
                  r.expanded);
              if (r.path != null)
                exportPath(
                    output
                        .toAbsolutePath()
                        .getParent()
                        .resolve(
                            output.getFileName()
                                + "-paths/case"
                                + (i + 1)
                                + "-"
                                + algorithm
                                + "-"
                                + heuristic
                                + "-"
                                + frontier
                                + "-r"
                                + repeat
                                + ".npz"),
                    problem,
                    r.path);
              if (!r.status.equals("SOLVED")) break;
            }
          }
        }
  }

  private static void write(Path path, String contents) throws Exception {
    if (path.toAbsolutePath().getParent() != null)
      Files.createDirectories(path.toAbsolutePath().getParent());
    Files.writeString(path, contents, StandardCharsets.UTF_8);
  }

  private static String quoted(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        + "\"";
  }

  private static void writeMetadata(
      Path output, String[] args, String input, Map<String, String> options) throws Exception {
    StringBuilder json =
        new StringBuilder("{\n  \"recorded_utc\": ")
            .append(quoted(java.time.Instant.now().toString()));
    json.append(",\n  \"args\": [");
    for (int i = 0; i < args.length; i++) {
      if (i > 0) json.append(',');
      json.append(quoted(args[i]));
    }
    json.append(']');
    json.append(",\n  \"java\": ").append(quoted(System.getProperty("java.runtime.version")));
    json.append(",\n  \"os\": ")
        .append(quoted(System.getProperty("os.name") + " " + System.getProperty("os.arch")));
    json.append(",\n  \"input_sha256\": ")
        .append(
            quoted(
                HexFormat.of()
                    .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(Path.of(input))))));
    json.append(",\n  \"max_heap_bytes\": ").append(Runtime.getRuntime().maxMemory());
    json.append(",\n  \"pattern_size\": ").append(Integer.getInteger("lab.patternSize", 6));
    json.append(",\n  \"pdb_symmetry\": ")
        .append(Boolean.parseBoolean(System.getProperty("lab.pdbSymmetry", "true")));
    json.append(",\n  \"tt_power\": ").append(Integer.getInteger("lab.ttPower", 21));
    json.append(",\n  \"pdb_cache\": ")
        .append(quoted(System.getProperty("lab.pdbCache", ".cache/pdb")));
    json.append(",\n  \"timeout_seconds\": ")
        .append(
            Double.parseDouble(
                options.getOrDefault("timeout", args[0].equals("benchmark") ? "30" : "60")));
    json.append(",\n  \"warmup\": ")
        .append(
            quoted(
                args[0].equals("benchmark")
                    ? "one simple instance per configuration; cached PDB; JIT not guaranteed fully"
                          + " warm"
                    : "none"));
    json.append("\n}\n");
    write(Path.of(output + ".meta.json"), json.toString());
  }

  public record Result(
      String status,
      String algorithm,
      String heuristic,
      String frontier,
      Deque<Node> path,
      double wall,
      double cpu,
      double preparation,
      double total,
      long generated,
      long expanded,
      long peak,
      int initialH) {
    public String json() {
      return String.format(
          Locale.ROOT,
          "{\"status\":\"%s\",\"algorithm\":\"%s\",\"heuristic\":\"%s\",\"frontier\":\"%s\",\"moves\":%d,\"initial_h\":%d,\"wall_seconds\":%.9f,\"cpu_seconds\":%s,\"preparation_seconds\":%.9f,\"total_seconds\":%.9f,\"generated\":%d,\"expanded\":%d,\"peak_frontier\":%d,\"path_verified\":%s}",
          status,
          algorithm,
          heuristic,
          frontier,
          path == null ? -1 : path.size(),
          initialH,
          wall,
          Double.isFinite(cpu) ? String.format(Locale.ROOT, "%.9f", cpu) : "null",
          preparation,
          total,
          generated,
          expanded,
          peak,
          path != null);
    }

    String csv(int id, int size, int repeat) {
      return String.format(
          Locale.ROOT,
          "%d,%d,%s,%s,%s,%d,%s,%d,%d,%.9f,%.9f,%.9f,%.9f,%d,%d,%d,%s",
          id,
          size,
          algorithm,
          heuristic,
          frontier,
          repeat,
          status,
          path == null ? -1 : path.size(),
          initialH,
          wall,
          cpu,
          preparation,
          total,
          generated,
          expanded,
          peak,
          path != null);
    }
  }

  private static final class SearchLimit extends RuntimeException {
    SearchLimit(String status) {
      super(status);
    }
  }

  /** Bounds the supplied A* without editing it; counts logical queue size, not stale records. */
  private static final class GuardedFrontier implements Frontier {
    final Frontier delegate;
    final long nanos, nodeLimit;
    long started, polls, checks, peak;

    GuardedFrontier(Frontier d, double seconds, long limit) {
      delegate = d;
      nanos = (long) (seconds * 1e9);
      nodeLimit = limit;
    }

    private void check() {
      if ((++checks & 1023) == 0 && System.nanoTime() - started > nanos)
        throw new SearchLimit("TIMEOUT");
    }

    public Node poll() {
      if (System.nanoTime() - started > nanos) throw new SearchLimit("TIMEOUT");
      if (++polls > nodeLimit) throw new SearchLimit("NODE_LIMIT");
      return delegate.poll();
    }

    public void clear() {
      delegate.clear();
      started = System.nanoTime();
      polls = checks = peak = 0;
    }

    public int size() {
      return delegate.size();
    }

    public boolean isEmpty() {
      check();
      return delegate.isEmpty();
    }

    public boolean contains(Node n) {
      return delegate.contains(n);
    }

    public boolean offer(Node n) {
      check();
      boolean v = delegate.offer(n);
      peak = Math.max(peak, delegate.size());
      return v;
    }
  }
}
