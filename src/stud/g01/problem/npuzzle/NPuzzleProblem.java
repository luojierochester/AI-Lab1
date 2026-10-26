package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;
import java.util.Deque;

/** Unit-cost sliding puzzle, including solvability relative to an arbitrary goal. */
public class NPuzzleProblem extends Problem {
  private final PuzzleBoard initialBoard;
  private final PuzzleBoard goalBoard;

  public NPuzzleProblem(State initialState, State goal) {
    this(asBoard(initialState), asBoard(goal), asBoard(initialState).getSize());
  }

  public NPuzzleProblem(State initialState, State goal, int size) {
    this(asBoard(initialState), asBoard(goal), size);
  }

  public NPuzzleProblem(PuzzleBoard start, PuzzleBoard goal, int size) {
    super(start, goal, size);
    if (start == null || goal == null || start.getSize() != size || goal.getSize() != size) {
      throw new IllegalArgumentException("Initial and goal boards must have the given size");
    }
    this.initialBoard = start;
    this.goalBoard = goal;
  }

  private static PuzzleBoard asBoard(State state) {
    if (!(state instanceof PuzzleBoard))
      throw new IllegalArgumentException("Expected a PuzzleBoard");
    return (PuzzleBoard) state;
  }

  public PuzzleBoard getInitialBoard() {
    return initialBoard;
  }

  public PuzzleBoard getGoalBoard() {
    return goalBoard;
  }

  @Override
  public boolean solvable() {
    return parity(initialBoard) == parity(goalBoard);
  }

  private static int parity(PuzzleBoard board) {
    int inversions = 0, cells = board.getSize() * board.getSize();
    for (int i = 0; i < cells; i++) {
      int left = board.tileAt(i);
      if (left == 0) continue;
      for (int j = i + 1; j < cells; j++) {
        int right = board.tileAt(j);
        if (right != 0 && left > right) inversions++;
      }
    }
    if ((board.getSize() & 1) == 0) {
      inversions += board.getSize() - board.blankIndex() / board.getSize();
    }
    return inversions & 1;
  }

  @Override
  public int stepCost(State state, Action action) {
    if (!(state instanceof PuzzleBoard)
        || ((PuzzleBoard) state).getSize() != size
        || !(action instanceof PuzzleMove)) {
      throw new IllegalArgumentException("Expected a puzzle state and move");
    }
    return 1;
  }

  @Override
  public boolean applicable(State state, Action action) {
    return state instanceof PuzzleBoard
        && ((PuzzleBoard) state).getSize() == size
        && action instanceof PuzzleMove
        && ((PuzzleBoard) state).canMove((PuzzleMove) action);
  }

  @Override
  public void showSolution(Deque<Node> path) {
    if (path == null) {
      System.out.println("No solution");
      return;
    }
    System.out.println("Step 0 (initial)");
    initialBoard.draw();
    int step = 0;
    for (Node node : path) {
      System.out.println("Step " + (++step) + " (blank " + node.getAction() + ")");
      node.getState().draw();
    }
    System.out.println("Moves: " + path.size());
  }
}
