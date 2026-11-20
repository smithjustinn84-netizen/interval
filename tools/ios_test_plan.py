#!/usr/bin/env python3
"""Prints which iOS test shards a CI run needs, as `shards=[...]` for $GITHUB_OUTPUT.

Each iOS test binary takes minutes to link on a CI runner, so a push runs only the first line of
tools/ios-test-shards.txt, the quick shard: the pure domains, which link without Compose. Every line runs
when native behavior may have changed under the common tests, or nightly to catch the rest:
- a scheduled or manual run, or a version tag;
- a change to iOS code (`src@ios/`), to the build (`module.yaml`, `project.yaml`, `templates/`, the
  version catalog, the toolchain wrapper), or to the shards or this workflow;
- a push whose changes are unknown (a new branch, a force push, a failed diff).

Usage: `git diff --name-only A B | python3 tools/ios_test_plan.py <event> <ref>`, where an empty stdin
means the changes are unknown. `--self-test` checks the rules. Standard library only.
"""

import fnmatch
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHARDS = ROOT / "tools" / "ios-test-shards.txt"

# The changes after which every iOS test runs: what can change how the common code behaves natively.
FULL_RUN_PATHS = [
    "*/src@ios/*",
    "module.yaml",
    "*/module.yaml",
    "project.yaml",
    "templates/*",
    "libs.versions.toml",
    "kotlin",
    "tools/ios-test-shards.txt",
    ".github/workflows/ci.yml",
]


def needs_every_shard(event: str, ref: str, changed: list[str]) -> bool:
    if event in ("schedule", "workflow_dispatch") or ref.startswith("refs/tags/v"):
        return True
    if not changed:
        return True
    return any(fnmatch.fnmatch(path, pattern) for path in changed for pattern in FULL_RUN_PATHS)


def shard_count() -> int:
    return len([line for line in SHARDS.read_text().splitlines() if line.strip()])


def self_test() -> None:
    push = ("push", "refs/heads/main")
    cases = [
        (push, ["interval/interval-domain/src/timer/WorkoutEngine.kt"], False),
        (push, ["docs/testing.md", "apps/app/test@jvm/AppHarness.kt"], False),
        (push, ["core/core/src@ios/platform/SystemServices.ios.kt"], True),
        (push, ["lift/lift-data/module.yaml"], True),
        (push, ["libs.versions.toml"], True),
        (push, ["kotlin"], True),
        (push, ["kotlinx/whatever.kt"], False),
        (push, [], True),
        (("schedule", "refs/heads/main"), ["README.md"], True),
        (("workflow_dispatch", "refs/heads/feature"), ["README.md"], True),
        (("push", "refs/tags/v0.3.0"), ["README.md"], True),
        (("pull_request", "refs/pull/7/merge"), ["interval/interval-timer/src/home/TimerScreen.kt"], False),
    ]
    for (event, ref), changed, expected in cases:
        actual = needs_every_shard(event, ref, changed)
        assert actual == expected, f"{event} {ref} {changed}: expected {expected}, got {actual}"
    assert shard_count() > 1, f"{SHARDS.name} needs the quick shard and at least one more"
    print(f"{len(cases)} cases pass")


def main() -> None:
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
        return
    if len(args) != 2:
        sys.exit("usage: git diff --name-only A B | ios_test_plan.py <event> <ref>  (or --self-test)")
    event, ref = args
    changed = [line.strip() for line in sys.stdin if line.strip()]
    count = shard_count() if needs_every_shard(event, ref, changed) else 1
    print(f"shards={list(range(1, count + 1))}".replace(" ", ""))


if __name__ == "__main__":
    main()
