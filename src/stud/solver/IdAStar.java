package stud.solver;

import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Frontier;

/** Compatibility entry point for the unchanged teacher-supplied EngineFeeder. */
public final class IdAStar extends stud.g01.solver.IdAStar {
  public IdAStar(Frontier frontier, Predictor predictor) {
    super(frontier, predictor);
  }
}
