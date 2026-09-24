"""Draws the two graphs the assignment asks for, for every run in results/.

Usage:  python3 plot_graphs.py
"""

import re
from pathlib import Path

import matplotlib.pyplot as plt

RESULTS = Path(__file__).parent / "results"
GRAPHS = RESULTS / "graphs"

COLORS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4"]

RUNS = [
    ("naive", "Naive (no cache)"),
    ("server_fifo", "Server cache, FIFO"),
    ("server_oldest", "Server cache, OLDEST"),
    ("client_fifo", "Client cache, FIFO"),
    ("client_oldest", "Client cache, OLDEST"),
]


def read_turnaround(folder):
    """Turn-around time of every query in a run's output file."""
    for name in ("naive_server.txt", "server_cache.txt", "client_cache.txt"):
        path = folder / name
        if not path.exists():
            continue
        times = []
        for line in path.read_text().splitlines():
            if not line.strip():
                break  # the summary lines come after a blank line
            match = re.search(r"turnaround time: (\d+) ms", line)
            if match:
                times.append(int(match.group(1)))
        return times
    return []


def read_queue(path):
    """Timestamps and queue lengths from one server log."""
    times, lengths = [], []
    for line in path.read_text().splitlines():
        stamp, _, length = line.partition(",")
        if length:
            times.append(int(stamp))
            lengths.append(int(length))
    return times, lengths


def turnaround_graph(interval):
    # One panel per configuration. All five on the same axes is unreadable.
    figure, axes = plt.subplots(len(RUNS), 1, sharex=True, sharey=True,
                                figsize=(10, 10))

    for axis, color, (run, label) in zip(axes, COLORS, RUNS):
        times = read_turnaround(RESULTS / f"{run}_T{interval}")
        if times:
            axis.plot(range(1, len(times) + 1), times, color=color, linewidth=0.4)
        axis.set_title(label, fontsize=10, loc="left")
        axis.set_ylabel("ms")
        axis.grid(alpha=0.3)

    axes[-1].set_xlabel("Query number")
    axes[0].set_ylim(bottom=0)
    figure.suptitle(f"Turn-around time per query, T = {interval} ms")
    figure.tight_layout()
    figure.savefig(GRAPHS / f"turnaround_T{interval}.png", dpi=150)
    plt.close(figure)


def queue_graph(run, label, interval):
    folder = RESULTS / f"{run}_T{interval}"

    logs = []
    for path in sorted(folder.glob("server_*_queue.csv")):
        times, lengths = read_queue(path)
        if times:
            zone = path.stem.split("_")[1]
            logs.append((zone, times, lengths))

    if not logs:
        return

    # Show seconds from the start of the run instead of raw Unix values.
    start = min(times[0] for _, times, _ in logs)

    plt.figure(figsize=(10, 4.2))

    for color, (zone, times, lengths) in zip(COLORS, logs):
        seconds = [(stamp - start) / 1000 for stamp in times]
        plt.step(seconds, lengths, where="post",
                 color=color, linewidth=1.3, label=f"Server {zone}")

    plt.axhline(18, color="gray", linestyle="--", linewidth=1)
    plt.text(0, 18.5, "overload threshold (18)", color="gray", fontsize=9)

    plt.xlabel(f"Seconds since Unix time {start // 1000}")
    plt.ylabel("Queue length")
    plt.title(f"Queue length per server, {label}, T = {interval} ms")
    plt.legend(ncol=5)
    plt.grid(alpha=0.3)
    plt.ylim(bottom=0, top=20)
    plt.tight_layout()
    plt.savefig(GRAPHS / f"queue_{run}_T{interval}.png", dpi=150)
    plt.close()


GRAPHS.mkdir(parents=True, exist_ok=True)

for interval in (50, 20):
    turnaround_graph(interval)
    for run, label in RUNS:
        queue_graph(run, label, interval)

print("Graphs written to", GRAPHS)
