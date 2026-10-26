package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import java.util.ArrayList;
import java.util.Objects;
import stud.g01.heuristic.PuzzleHeuristics;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

/** Parses size, initial tiles and goal tiles, with one problem per line. */
public final class PuzzleFeeder extends EngineFeeder {
  @Override
  public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
    Objects.requireNonNull(problemLines, "problemLines");
    ArrayList<Problem> problems = new ArrayList<>();
    for (int row = 0; row < problemLines.size(); row++) {
      String line = problemLines.get(row);
      if (line == null) throw new IllegalArgumentException("Line " + (row + 1) + " is null");
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      try {
        String[] fields = line.split("\\s+");
        int size = Integer.parseInt(fields[0]);
        if (size < 2 || size > 4) throw new IllegalArgumentException("Size must be 2, 3 or 4");
        int cells = size * size;
        if (fields.length != 1 + 2 * cells) {
          throw new IllegalArgumentException(
              "Expected " + (1 + 2 * cells) + " integers, found " + fields.length);
        }
        int[] initial = new int[cells], goal = new int[cells];
        for (int i = 0; i < cells; i++) {
          initial[i] = Integer.parseInt(fields[i + 1]);
          goal[i] = Integer.parseInt(fields[i + 1 + cells]);
        }
        problems.add(
            new NPuzzleProblem(new PuzzleBoard(size, initial), new PuzzleBoard(size, goal), size));
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Line " + (row + 1) + ": " + ex.getMessage(), ex);
      }
    }
    if (problems.isEmpty()) throw new IllegalArgumentException("No puzzle problems were provided");
    return problems;
  }

  @Override
  public Frontier getFrontier(EvaluationType type) {
    return new PqFrontier(type);
  }

  @Override
  public Predictor getPredictor(HeuristicType type) {
    return new PuzzleHeuristics(type);
  }
}
