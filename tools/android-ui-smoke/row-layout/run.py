#!/usr/bin/env python3
"""Verify viewport-bounded card creation in the opt-in rowbench APK."""
import argparse
import json
import os
import re
import statistics
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
parser.add_argument("--rounds", type=int, default=3)
parser.add_argument("--output", type=Path)
args = parser.parse_args()
if args.rounds < 1:
    parser.error("--rounds must be positive")
adb = [args.adb] + (["-s", args.serial] if args.serial else [])
package = "com.fongmi.android.tv.rowbench"


def command(*parts):
    return subprocess.check_output(adb + list(parts), text=True, timeout=30)


samples = []
try:
    for iteration in range(args.rounds):
        for baseline in ([True, False] if iteration % 2 == 0 else [False, True]):
            command("shell", "am", "force-stop", package)
            command("shell", "am", "start", "-W", "-n",
                    package + "/com.fongmi.android.tv.test.RowLayoutActivity",
                    "--ez", "baseline", str(baseline).lower())
            pid = command("shell", "pidof", package).strip()
            deadline = time.monotonic() + 30
            while True:
                log = command("logcat", "-d", "--pid=" + pid,
                              "-s", "RowLayoutBench:I", "*:S")
                match = re.search(r"created=(\d+) attachedRows=(\d+) firstLayoutMs=([\d.]+)", log)
                if match:
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError("No first-layout result for PID " + pid)
                time.sleep(0.2)
            sample = dict(baseline=baseline, created=int(match[1]),
                          attached_rows=int(match[2]), first_layout_ms=float(match[3]))
            samples.append(sample)
            print(json.dumps(sample), flush=True)
            if baseline:
                assert sample["created"] >= 500 and sample["attached_rows"] == 100, sample
            else:
                assert sample["created"] < 50 and sample["attached_rows"] < 10, sample
    summary = {"samples": samples}
    for baseline, name in [(True, "baseline"), (False, "fixed")]:
        summary[name + "_median_ms"] = statistics.median(
            s["first_layout_ms"] for s in samples if s["baseline"] == baseline)
    if args.output:
        args.output.write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary), flush=True)
finally:
    command("shell", "am", "force-stop", package)
