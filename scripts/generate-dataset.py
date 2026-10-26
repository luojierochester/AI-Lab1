"""Regenerate the fixed-seed supplementary dataset (standard library only)."""
import argparse
import random
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--output', default='resources/generated.txt')
args = parser.parse_args()
lines = []
for size in (3, 4):
    for seed, depth in ((20261026, 10), (20261027, 20), (20261028, 30), (20261029, 40)):
        rng = random.Random(seed)
        board = list(range(1, size * size)) + [0]
        target = board.copy()
        zero, previous = size * size - 1, -1
        for _ in range(depth):
            candidates = [q for q in range(size * size)
                          if abs(q // size - zero // size) + abs(q % size - zero % size) == 1 and q != previous]
            q = rng.choice(candidates)
            board[zero], board[q] = board[q], board[zero]
            previous, zero = zero, q
        lines.append(' '.join(map(str, [size] + board + target)))
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(('\r\n'.join(lines) + '\r\n').encode('utf-8'))
print(output)
