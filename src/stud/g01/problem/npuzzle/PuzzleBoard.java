package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;
import java.util.List;
import java.util.Objects;

/** Immutable 2 x 2, 3 x 3 or 4 x 4 puzzle. Zero is the blank. */
public final class PuzzleBoard extends State {
  private static final List<PuzzleMove> MOVES =
      List.of(PuzzleMove.UP, PuzzleMove.DOWN, PuzzleMove.LEFT, PuzzleMove.RIGHT);
  private final int size;
  private final long packed;
  private final int blank;
  private final int hash;

  public PuzzleBoard(int size, int[] tiles) {
    if (size < 2 || size > 4) throw new IllegalArgumentException("Puzzle size must be 2, 3 or 4");
    Objects.requireNonNull(tiles, "tiles");
    if (tiles.length != size * size)
      throw new IllegalArgumentException("Expected " + (size * size) + " tiles");
    int seen = 0, zero = -1;
    long value = 0;
    for (int i = 0; i < tiles.length; i++) {
      int tile = tiles[i];
      if (tile < 0 || tile >= tiles.length || (seen & (1 << tile)) != 0) {
        throw new IllegalArgumentException(
            "Tiles must contain every value 0.." + (tiles.length - 1) + " exactly once");
      }
      seen |= 1 << tile;
      value |= (long) tile << (4 * i);
      if (tile == 0) zero = i;
    }
    this.size = size;
    this.packed = value;
    this.blank = zero;
    this.hash = stateHash(size, value);
  }

  private PuzzleBoard(int size, long packed, int blank) {
    this.size = size;
    this.packed = packed;
    this.blank = blank;
    this.hash = stateHash(size, packed);
  }

  public int getSize() {
    return size;
  }

  public long packed() {
    return packed;
  }

  public int blankIndex() {
    return blank;
  }

  public int tileAt(int index) {
    if (index < 0 || index >= size * size) throw new IndexOutOfBoundsException(index);
    return (int) ((packed >>> (4 * index)) & 15L);
  }

  /** A fresh array is returned; callers cannot mutate this board. */
  public int[] toArray() {
    int[] tiles = new int[size * size];
    for (int i = 0; i < tiles.length; i++) tiles[i] = tileAt(i);
    return tiles;
  }

  public boolean canMove(PuzzleMove move) {
    if (move == null) return false;
    int row = blank / size + move.dr(), col = blank % size + move.dc();
    return row >= 0 && row < size && col >= 0 && col < size;
  }

  @Override
  public PuzzleBoard next(Action action) {
    if (!(action instanceof PuzzleMove))
      throw new IllegalArgumentException("Expected a PuzzleMove");
    return next((PuzzleMove) action);
  }

  public PuzzleBoard next(PuzzleMove move) {
    if (!canMove(move)) throw new IllegalArgumentException("Illegal blank move: " + move);
    int target = blank + move.dr() * size + move.dc();
    long tile = (packed >>> (target * 4)) & 15L;
    long moved = packed ^ (tile << (target * 4)) ^ (tile << (blank * 4));
    return new PuzzleBoard(size, moved, target);
  }

  /** Candidate actions; Problem.applicable filters moves crossing an edge. */
  @Override
  public Iterable<PuzzleMove> actions() {
    return MOVES;
  }

  @Override
  public void draw() {
    System.out.println(this);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof PuzzleBoard)) return false;
    PuzzleBoard board = (PuzzleBoard) other;
    return size == board.size && packed == board.packed;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static int stateHash(int size, long value) {
    long mixed = value ^ (0x9e3779b97f4a7c15L * size);
    mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
    mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
    mixed ^= mixed >>> 31;
    return (int) (mixed ^ (mixed >>> 32));
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < size * size; i++) {
      if (i > 0) result.append(i % size == 0 ? '\n' : ' ');
      int tile = tileAt(i);
      if (size == 4 && tile < 10) result.append(' ');
      result.append(tile);
    }
    return result.toString();
  }
}
