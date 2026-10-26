"""Summarize measured rows only; retain failures and never substitute synthetic timings."""
import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('files', nargs='+')
parser.add_argument('--output', default='results/summary.json')
args = parser.parse_args()
groups = defaultdict(list)
for name in args.files:
    for row in csv.DictReader(Path(name).open(encoding='utf-8')):
        key = (Path(name).name, row['case'], row['size'], row['algorithm'], row['heuristic'], row['frontier'])
        groups[key].append(row)
summary = []
for key, rows in groups.items():
    ok = [r for r in rows if r['status'] == 'SOLVED']
    values = [float(r['wall_seconds']) for r in ok]
    item = dict(zip(('file', 'case', 'size', 'algorithm', 'heuristic', 'frontier'), key))
    item.update(measurements=len(rows), successes=len(ok), statuses=[r['status'] for r in rows])
    item['moves'] = sorted({int(r['moves']) for r in ok})
    item['wall_median_seconds'] = statistics.median(values) if values else None
    item['wall_min_seconds'] = min(values) if values else None
    item['wall_max_seconds'] = max(values) if values else None
    item['expanded_median'] = statistics.median(int(r['expanded']) for r in ok) if ok else None
    summary.append(item)
target = Path(args.output)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{len(summary)} groups -> {target}')
