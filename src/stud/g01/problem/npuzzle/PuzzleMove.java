package stud.g01.problem.npuzzle;

import core.problem.Action;

/** Directions describe movement of the blank, not movement of the tile. */
public final class PuzzleMove extends Action {
  public static final PuzzleMove UP = new PuzzleMove("UP", -1, 0);
  public static final PuzzleMove DOWN = new PuzzleMove("DOWN", 1, 0);
  public static final PuzzleMove LEFT = new PuzzleMove("LEFT", 0, -1);
  public static final PuzzleMove RIGHT = new PuzzleMove("RIGHT", 0, 1);
  private final String name;
  private final int dr, dc;

  private PuzzleMove(String name, int dr, int dc) {
    this.name = name;
    this.dr = dr;
    this.dc = dc;
  }

  public int dr() {
    return dr;
  }

  public int dc() {
    return dc;
  }

  @Override
  public int stepCost() {
    return 1;
  }

  @Override
  public void draw() {
    System.out.println(name);
  }

  @Override
  public String toString() {
    return name;
  }
}
