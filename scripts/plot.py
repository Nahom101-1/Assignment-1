#!/usr/bin/env python3
"""Draws the two graphs the assignment asks for.

    python3 scripts/plot.py output/naive_server.txt

1) turn-around time per query number (from the output file)
2) queue length over time for every server (from the *_queue.log files)

Needs matplotlib:  python3 -m pip install matplotlib
"""
import re
import sys
from pathlib import Path

import matplotlib.pyplot as plt

TURNAROUND = re.compile(r"turnaround time: (\d+) ms")


def plot_turnaround(report: Path) -> None:
    times = [int(m.group(1)) for line in report.read_text().splitlines()
             if (m := TURNAROUND.search(line))]
    plt.figure()
    plt.plot(range(1, len(times) + 1), times, linewidth=0.6)
    plt.xlabel("query number")
    plt.ylabel("turn-around time (ms)")
    plt.title(report.stem)
    out = report.with_suffix(".turnaround.png")
    plt.savefig(out, dpi=150)
    print("wrote", out)


def plot_queues(folder: Path, prefix: str) -> None:
    logs = sorted(folder.glob(f"{prefix}*server_*_queue.log")) or sorted(folder.glob("server_*_queue.log"))
    if not logs:
        print("no queue logs found")
        return
    plt.figure()
    for log in logs:
        rows = [line.split(";") for line in log.read_text().splitlines()[1:] if ";" in line]
        if not rows:
            continue
        start = int(rows[0][0])
        x = [(int(r[0]) - start) / 1000 for r in rows]
        y = [int(r[1]) for r in rows]
        plt.step(x, y, where="post", linewidth=0.7, label=log.stem)
    plt.xlabel("seconds since start")
    plt.ylabel("requests in the waiting list")
    plt.legend(fontsize=7)
    out = folder / f"{prefix or 'run'}queues.png"
    plt.savefig(out, dpi=150)
    print("wrote", out)


if __name__ == "__main__":
    report_path = Path(sys.argv[1] if len(sys.argv) > 1 else "output/naive_server.txt")
    plot_turnaround(report_path)
    plot_queues(report_path.parent, report_path.stem + "_")

