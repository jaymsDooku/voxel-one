"""Measure one or more native Basalt Courtyard bouts, or replays on the same frame clock."""
import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path


# Grounded sprint launches before and after the practice lift reduction.
GROUNDED_SPRINT_STEPS = (.4208, .4088)


def read(path):
    with path.open(newline="") as stream:
        return list(csv.DictReader(stream))


def point(row, prefix):
    return tuple(float(row[prefix + axis]) for axis in "XYZ")


def summary(values):
    if not values:
        return "n=0"
    ordered = sorted(values)
    return (f"n={len(values)}, median={statistics.median(ordered):.1f} ms, "
            f"p95={ordered[math.ceil(.95 * len(ordered)) - 1]:.1f} ms")


def bout(path, clicks_path, times, counts, phases, intervals):
    rows = read(path)
    accepted = read(clicks_path)
    clicks = {int(r["clickSequence"]): r for r in rows if r["clickSequence"]}
    frames = [r for r in rows if r["phase"] in ("FIGHT", "RESULT") and r["renderNs"]]
    for r in frames:
        r["sample"] = int(r["sampleNs"])
        r["render"] = int(r["renderNs"])
        r["tick"] = int(r["tick"])
    # A nonvisible actor can still have a confirmed lethal hit. Count it in the denominator.
    start = {}
    for i, r in enumerate(rows):
        if r["phase"] in ("FIGHT", "RESULT") and r["remoteHurtTick"] and int(r["remoteHurtTick"]) >= 0:
            start.setdefault(int(r["remoteHurtTick"]), i)
    counts["accepted"] += len(accepted)
    counts["ambiguous"] += sum(int(h["sequence"]) < 0 for h in accepted)
    counts["confirmed"] += len(start)
    by_tick = {int(hit["tick"]): hit for hit in accepted}
    hit_times = {}
    for hurt, i in start.items():
        r = rows[i]
        confirm = int(r["sampleNs"])
        hit = by_tick.get(hurt)
        click = clicks.get(int(hit["sequence"])) if hit else None
        if click and click["clickTarget"] != r["remoteId"]:
            click = None
        if click:
            times["click_confirm"].append((confirm - int(click["clickNs"])) / 1e6)
        following = []
        for f in rows[i:]:
            if int(f["remoteHurtTick"] or -1) != hurt or int(f["tick"]) > hurt + 5:
                break
            if f["renderNs"] and f["remoteTint"]:
                following.append(f)
        flash = next((f for f in following if float(f["remoteTint"]) > .5), None)
        if flash:
            stamp = int(flash["renderNs"])
            times["red"].append((stamp - confirm) / 1e6)
            hit_times[(hurt, "red")] = (stamp - confirm) / 1e6
            if click:
                times["click_red"].append((stamp - int(click["clickNs"])) / 1e6)
        # A previous knockback can still be descending on screen at the next hit.
        # Only attribute a rise if the rendered feet were near launch height before this hit.
        launch_y = float(r["remoteY"])
        displacement_y = float(r["remoteVy"])
        carried_y = float(r["remoteImpulseY"])
        # Older recordings held the launch for the following tick. New melee snapshots
        # already contain the first displacement and publish its post-gravity remainder.
        stepped = any(abs(displacement_y - step) < .001
                      and abs(carried_y - (step - .08) * .98) < .001
                      for step in GROUNDED_SPRINT_STEPS)
        pending = any(abs(carried_y - step) < .001 for step in GROUNDED_SPRINT_STEPS)
        if stepped:
            launch_y -= displacement_y
        if (int(r["tick"]) == hurt == int(r["remoteImpulseTick"])
                and (stepped or pending)):
            counts["grounded"] += 1
            if (i < 1 or not rows[i-1]["remoteDisplayY"]
                    or abs(float(rows[i-1]["remoteDisplayY"]) - launch_y) > .025):
                continue
            counts["eligible_rises"] += 1
            rise = next((f for f in following if float(f["remoteDisplayY"]) >= launch_y + .03), None)
            if rise:
                times["rise"].append((int(rise["renderNs"]) - confirm) / 1e6)
                hit_times[(hurt, "rise")] = times["rise"][-1]

    live = [r for r in frames if r["phase"] == "FIGHT"]
    times["frame"].extend((b["render"] - a["render"]) / 1e6 for a, b in zip(live, live[1:]))
    phases.append(len({float(r["remoteSwingProgress"]) for r in live if r["remoteSwingProgress"]
                       and 0 < float(r["remoteSwingProgress"]) < 1}))
    # Consecutive server poses must confirm travel over .03 blocks. Exclude packet gaps and teleports.
    bounds = [i for i, r in enumerate(live) if i == 0 or r["tick"] != live[i - 1]["tick"]]
    for who in ("remote", "local"):
        for impacted in (False, True):
            key = f"{who} {'impact ticks 0..4' if impacted else 'all motion'}"
            run = 0.0
            for left, right in zip(bounds, bounds[1:]):
                a, b = live[left], live[right]
                hurt = int(b[who + "HurtTick"])
                if ((impacted and not (hurt >= 0 and 0 <= b["tick"] - hurt <= 4))
                        or b["tick"] != a["tick"] + 1 or a[who + "MotionEpoch"] != b[who + "MotionEpoch"]
                        or math.dist(point(a, who), point(b, who)) < .03):
                    run = 0
                    continue
                for idx in range(left, right):
                    f, g = live[idx:idx + 2]
                    if not f[who + "DisplayX"] or not g[who + "DisplayX"]:
                        run = 0
                        continue
                    dt = (g["render"] - f["render"]) / 1e6
                    paused = math.dist(point(f, who + "Display"), point(g, who + "Display")) < .001
                    run = run + dt if paused else 0.0
                    intervals[key].append((dt, paused, run))
    return hit_times


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("traces", type=Path, nargs="+")
    parser.add_argument("--clicks", type=Path, help="override acceptedClicks.csv for a single trace")
    parser.add_argument("--paired", metavar="CSV", help="compare delays with this sibling replay of the same frames")
    args = parser.parse_args()
    if args.clicks and len(args.traces) != 1:
        parser.error("--clicks takes a single matching trace")
    times, counts, phases, intervals = defaultdict(list), defaultdict(int), [], defaultdict(list)
    changes = defaultdict(list)
    for path in args.traces:
        clicks = args.clicks or path.with_name("acceptedClicks.csv")
        current = bout(path, clicks, times, counts, phases, intervals)
        if args.paired:
            previous_path = path.with_name(args.paired)
            def clocks(file):
                return [(row["sampleNs"], row["tick"]) for row in read(file)]
            if clocks(previous_path) != clocks(path):
                parser.error("--paired requires the same frame and snapshot timestamps")
            previous = bout(previous_path, clicks, defaultdict(list), defaultdict(int), [], defaultdict(list))
            for key in current.keys() & previous.keys():
                changes[key[1]].append(previous[key] - current[key])
    print(f"Rounds={len(phases)}, accepted hit batches={counts['accepted']}, ambiguous={counts['ambiguous']}, "
          f"first observed hurt cues={counts['confirmed']}")
    print("Scripted click to first sampled confirmation:", summary(times["click_confirm"]))
    print("Scripted click to rendered red:", summary(times["click_red"]))
    print("Confirmation to rendered red:", summary(times["red"]))
    print(f"Confirmation to rendered rise of .03 blocks ({counts['eligible_rises']}/{counts['grounded']} "
          f"grounded launches eligible):", summary(times["rise"]))
    print("Fight frame interval:", summary(times["frame"]))
    print("Distinct in-swing phases per round:", phases)
    for key, values in intervals.items():
        total = sum(dt for dt, _, _ in values)
        paused = sum(dt for dt, stalled, _ in values if stalled)
        print(f"{key}: {sum(stalled for _, stalled, _ in values)}/{len(values)} intervals paused; "
              f"{paused:.1f}/{total:.1f} ms ({100 * paused / total:.2f}%); "
              f"longest {max(run for _, _, run in values):.1f} ms.")
    if args.paired:
        for metric in ("red", "rise"):
            print(f"Time saved on matched {metric} events:", summary(changes[metric]))
    print("Latency ends at the render callback before capture/swap. Replays share their source frame times.")


if __name__ == "__main__":
    main()
