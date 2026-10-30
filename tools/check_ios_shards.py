#!/usr/bin/env python3
"""Fails when the iOS test shards do not cover every module with common tests exactly once.

CI runs the iOS simulator tests in parallel jobs, one per line of tools/ios-test-shards.txt. The first
line is the quick shard, which every push runs (tools/ios_test_plan.py says when the rest run too); the
others group modules by the part of the tree they compile (Lift, the interval side's low layers, its
features), since a shard's cost is mostly its first, cold compile. Nothing else notices a module left
out, so CI runs this first: a new module with a `test/` directory fails here until it is given a line.
Standard library only; run from anywhere: `python3 tools/check_ios_shards.py`.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHARDS = ROOT / "tools" / "ios-test-shards.txt"


def main() -> None:
    lines = [line.split() for line in SHARDS.read_text().splitlines() if line.strip()]
    listed = [module for line in lines for module in line]
    tested = sorted(path.parent.name for path in ROOT.glob("*/test") if path.is_dir())
    problems = []
    for module in sorted(set(listed)):
        if listed.count(module) > 1:
            problems.append(f"{module} is in more than one shard")
        if not (ROOT / module / "module.yaml").is_file():
            problems.append(f"{module} is not a module")
    for module in tested:
        if module not in listed:
            problems.append(f"{module} has tests but no shard: add it to a line of {SHARDS.name}")
    if problems:
        sys.exit("\n".join(problems))
    print(f"{len(listed)} modules in {len(lines)} iOS test shards")


if __name__ == "__main__":
    main()
