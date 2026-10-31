#!/usr/bin/env python3
"""Fails when a module depends on one it shouldn't, by the rules CLAUDE.md's "Modules" section describes.

Each module has a side (its folder: core, interval, lift, apps, tooling) and a kind (time, core-platform, core,
core-testing, domain, data, platform, ui, testing, feature, entry, app, host). The rules:
- sides: core sees only core; interval sees interval, lift and core; lift sees lift and core, never interval;
  apps sees the rest; tooling sees nothing. Test dependencies follow this rule too.
- kinds: each kind may depend on the kinds below it (`ALLOWED`). A feature depends on another feature only
  where `FEATURE_EDGES` lists the pair; interval may use only Lift's domain, data and ui.
- the `*-testing` modules and `core-testing` are for `test-dependencies`, outside the testing modules. Any other
  test dependency is one the module's `dependencies` could take.
- the pure layers (time, core-platform, domain, data) take no Compose: no `$compose.*`, `$libs.compose.*` or
  `$libs.koin.compose`, no `compose-lib`, and no `settings: compose: enabled`.
- every library (all but the hosts and the tooling plugins) applies a template from `templates/`.
- no source under `interval/*/src*` names `LiftRepository`: the interval side reads Lift through `LiftActivity`.

Dependencies are read from every module's `dependencies`, `dependencies@<platform>` and `test-dependencies`
sections, after the templates under `apply:` are expanded. Standard library only:
`python3 tools/check_module_rules.py`, or `--self-test` to see each rule catch a breach.
"""

import re
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROJECT = ROOT / "project.yaml"

SIDES_SEEN = {
    "core": {"core"},
    "interval": {"interval", "lift", "core"},
    "lift": {"lift", "core"},
    "apps": {"apps", "interval", "lift", "core"},
    "tooling": set(),
}

# What each kind of module may depend on, under `dependencies` (any platform).
FOUNDATION = {"time", "core-platform", "core"}
ALLOWED = {
    "time": set(),
    "core-platform": {"time"},
    "core": {"time", "core-platform"},
    "core-testing": {"time", "core-platform", "core"},
    "domain": {"time", "domain"},
    "data": {"time", "core-platform", "domain"},
    "platform": FOUNDATION | {"domain", "data"},
    "ui": FOUNDATION | {"domain"},
    "testing": FOUNDATION | {"core-testing", "domain", "data", "platform", "testing"},
    "feature": FOUNDATION | {"domain", "data", "platform", "ui", "feature"},
    "entry": FOUNDATION | {"domain", "data", "platform", "ui", "feature"},
    "app": FOUNDATION | {"domain", "data", "platform", "ui", "feature", "entry"},
    "host": {"app"},
    "tooling": set(),
}

# The only features that build on another: the editor the Timer and the workout open, and the exercise
# picker a session logs from.
FEATURE_EDGES = {
    ("interval-timer", "interval-presets"),
    ("interval-workout", "interval-presets"),
    ("lift-session", "lift-library"),
}

# What the interval side may use of Lift's: never its features, platform or navigation.
CROSS_SIDE_KINDS = {"domain", "data", "ui"}

TEST_ONLY = {"testing", "core-testing"}
NO_COMPOSE = {"time", "core-platform", "domain", "data"}
# What brings Compose in under `dependencies`.
COMPOSE_PREFIXES = ("$compose.", "$libs.compose.", "$libs.koin.compose")
# The kinds that apply no template: the hosts are applications, the tooling plugins build plugins.
UNTEMPLATED = {"host", "tooling"}
# The interval side reads Lift through `LiftActivity`, never the whole repository.
LIFT_REPOSITORY = re.compile(r"\bLiftRepository\b")

CORE_KINDS = {"core-time": "time", "core-platform": "core-platform", "core": "core", "core-testing": "core-testing"}
SUFFIX_KINDS = {"domain", "data", "platform", "ui", "testing"}


@dataclass
class Module:
    name: str
    side: str
    deps: set[str] = field(default_factory=set)  # module names under `dependencies*`
    test_deps: set[str] = field(default_factory=set)  # module names under `test-dependencies*`
    compose: list[str] = field(default_factory=list)  # what brings Compose in, if anything
    templates: list[str] = field(default_factory=list)  # its own `apply:` entries


def kind_of(name: str, side: str) -> str:
    if side == "core":
        return CORE_KINDS[name]
    if side == "tooling":
        return "tooling"
    if side == "apps":
        return "app" if name == "app" else "host"
    if name == "lift-nav":
        return "entry"
    suffix = name.rsplit("-", 1)[-1]
    return suffix if suffix in SUFFIX_KINDS else "feature"


def module_paths() -> list[str]:
    """The module paths in project.yaml's `modules:` list."""
    paths, in_modules = [], False
    for line in PROJECT.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" "):
            in_modules = line.startswith("modules:")
        elif in_modules and line.strip().startswith("- "):
            paths.append(line.strip()[2:].strip())
    return paths


def sections(path: Path) -> dict[str, list[str]]:
    """A module or template file's top-level list sections, each item with any `: exported` dropped."""
    found, current = {}, None
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" "):
            current = line.split(":", 1)[0].strip()
            found.setdefault(current, [])
        elif current is not None and line.strip().startswith("- "):
            item = line.strip()[2:].strip()
            found[current].append(item.split(":", 1)[0].strip() if item.startswith(("//", "$")) else item)
    return found


def compose_enabled(path: Path) -> bool:
    """Whether a module or template file turns Compose on: `settings: compose: enabled`, or `enabled: true` under it."""
    in_settings = in_compose = False
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        key, _, value = line.strip().partition(":")
        value = value.strip()
        if indent == 0:
            in_settings, in_compose = key == "settings", False
        elif in_settings and indent == 2:
            in_compose = key == "compose"
            if in_compose and value == "enabled":
                return True
        elif in_compose and indent == 4 and key == "enabled" and value == "true":
            return True
    return False


def expanded(path: Path, seen: frozenset = frozenset()) -> dict[str, list[str]]:
    """[sections] with every template under `apply:` merged in, recursively."""
    own = sections(path)
    merged: dict[str, list[str]] = {}
    for template in own.get("apply", []):
        target = ROOT / template.removeprefix("//")
        if target in seen:
            continue
        for key, items in expanded(target, seen | {target}).items():
            merged.setdefault(key, []).extend(items)
        if "compose-lib" in template:
            merged.setdefault("compose-lib", []).append(template)
    if compose_enabled(path):
        merged.setdefault("compose-lib", []).append(f"settings.compose in {path.relative_to(ROOT)}")
    for key, items in own.items():
        merged.setdefault(key, []).extend(items)
    return merged


def load() -> dict[str, Module]:
    by_path = {path: Path(path).name for path in module_paths()}
    modules = {}
    for path, name in by_path.items():
        found = expanded(ROOT / path / "module.yaml")
        module = Module(name, path.split("/", 1)[0] if "/" in path else "apps")
        module.templates = sections(ROOT / path / "module.yaml").get("apply", [])
        for key, items in found.items():
            refs = {by_path.get(item[2:], item[2:]) for item in items if item.startswith("//") and "templates/" not in item}
            if key.startswith("test-dependencies"):
                module.test_deps |= refs
            elif key.startswith("dependencies"):
                module.deps |= refs
                module.compose += [item for item in items if item.startswith(COMPOSE_PREFIXES)]
        module.compose += found.get("compose-lib", [])
        modules[name] = module
    return modules


def check(modules: dict[str, Module]) -> list[str]:
    problems = []
    for module in modules.values():
        kind = kind_of(module.name, module.side)
        for dep_name, test in [(d, False) for d in sorted(module.deps)] + [(d, True) for d in sorted(module.test_deps)]:
            dep = modules.get(dep_name)
            where = f"{module.name} -> {dep_name}" + (" (test)" if test else "")
            if dep is None:
                problems.append(f"{where}: no such module in project.yaml")
                continue
            dep_kind = kind_of(dep.name, dep.side)
            if dep.side not in SIDES_SEEN[module.side]:
                problems.append(f"{where}: {module.side}/ never depends on {dep.side}/")
                continue
            if test:
                if dep_kind not in TEST_ONLY and dep_kind not in ALLOWED[kind]:
                    problems.append(f"{where}: a {kind} module's tests don't depend on a {dep_kind} module")
                continue
            if dep_kind in TEST_ONLY and kind not in {"testing", "core-testing"}:
                problems.append(f"{where}: a testing module belongs under test-dependencies")
            elif dep_kind not in ALLOWED[kind]:
                problems.append(f"{where}: a {kind} module doesn't depend on a {dep_kind} module")
            elif module.side == "interval" and dep.side == "lift" and dep_kind not in CROSS_SIDE_KINDS:
                problems.append(f"{where}: the interval side uses only Lift's domain, data and ui")
            elif kind == "feature" and dep_kind == "feature" and (module.name, dep.name) not in FEATURE_EDGES:
                problems.append(f"{where}: features don't depend on each other (add it to FEATURE_EDGES if it must)")
        if kind in NO_COMPOSE and module.compose:
            problems.append(f"{module.name}: a {kind} module takes no Compose ({', '.join(sorted(set(module.compose)))})")
        if kind not in UNTEMPLATED and not module.templates:
            problems.append(f"{module.name}: a library applies a template from templates/")
    return problems


def lift_repository_uses(root: Path) -> list[str]:
    """The interval side's main sources under [root] that name `LiftRepository`."""
    return [
        f"{source.relative_to(root)}: the interval side reads Lift through LiftActivity, not LiftRepository"
        for source in sorted(root.glob("interval/*/src*/**/*.kt"))
        if LIFT_REPOSITORY.search(source.read_text())
    ]


def self_test() -> None:
    def fake(name: str, side: str, deps=(), test_deps=(), compose=(), templates=("lib",)) -> Module:
        return Module(name, side, set(deps), set(test_deps), list(compose), list(templates))

    base = {
        m.name: m
        for m in [
            fake("core-time", "core"),
            fake("core-platform", "core", ["core-time"]),
            fake("core", "core", ["core-platform", "core-time"], compose=["$compose.ui"]),
            fake("core-testing", "core", ["core"]),
            fake("lift-domain", "lift"),
            fake("lift-data", "lift", ["lift-domain", "core-platform"]),
            fake("lift-ui", "lift", ["lift-domain", "core"]),
            fake("lift-library", "lift", ["lift-data", "lift-ui", "core"]),
            fake("interval-domain", "interval", ["core-time", "lift-domain"]),
            fake("interval-data", "interval", ["interval-domain", "core-platform"]),
            fake("interval-testing", "interval", ["interval-data", "core-testing"]),
            fake("interval-presets", "interval", ["interval-data", "core"], ["interval-testing"]),
            fake("interval-timer", "interval", ["interval-presets", "core"]),
            fake("interval-settings", "interval", ["interval-data", "core"]),
            fake("app", "apps", ["interval-timer", "lift-library", "core"], ["interval-testing"]),
            fake("androidApp", "apps", ["app"]),
        ]
    }
    assert check(base) == [], check(base)

    breaches = {
        "lift never depends on interval": ("lift-library", "deps", "interval-data", "lift/ never depends on interval/"),
        "core sees only core": ("core", "deps", "interval-domain", "core/ never depends on interval/"),
        "the side rule covers tests": ("lift-ui", "test_deps", "interval-testing", "lift/ never depends on interval/"),
        "data takes no core": ("interval-data", "deps", "core", "a data module doesn't depend on a core module"),
        "a domain takes no data": ("interval-domain", "deps", "lift-data", "a domain module doesn't depend on a data"),
        "testing only under tests": ("interval-presets", "deps", "interval-testing", "belongs under test-dependencies"),
        "features stand alone": ("interval-settings", "deps", "interval-presets", "features don't depend on each other"),
        "no Lift features on interval": ("interval-timer", "deps", "lift-library", "uses only Lift's domain, data and ui"),
        "nothing depends on a host": ("app", "deps", "androidApp", "doesn't depend on a host module"),
        "every dependency is a module": ("app", "deps", "nowhere", "no such module"),
        "tests take what the module could": ("lift-ui", "test_deps", "lift-library", "tests don't depend on a feature"),
    }
    for rule, (name, attr, dep, expected) in breaches.items():
        graph = {k: fake(v.name, v.side, v.deps, v.test_deps, v.compose) for k, v in base.items()}
        getattr(graph[name], attr).add(dep)
        found = check(graph)
        assert any(expected in p for p in found), f"{rule}: expected '{expected}', got {found}"

    graph = {k: fake(v.name, v.side, v.deps, v.test_deps, v.compose) for k, v in base.items()}
    graph["lift-data"].compose.append("$compose.runtime")
    assert any("takes no Compose" in p for p in check(graph)), "pure layers take no Compose"

    graph = {k: fake(v.name, v.side, v.deps, v.test_deps, v.compose) for k, v in base.items()}
    graph["lift-ui"].templates.clear()
    assert any("applies a template" in p for p in check(graph)), "every library applies a template"

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        module = root / "module.yaml"
        module.write_text("settings:\n  kotlin:\n    serialization: json\n")
        assert not compose_enabled(module), "no compose settings, no Compose"
        module.write_text("settings:\n  compose:\n    enabled: true\n")
        assert compose_enabled(module), "settings: compose: enabled: true brings Compose"
        module.write_text("settings:\n  compose: enabled\n")
        assert compose_enabled(module), "settings: compose: enabled brings Compose"

        source = root / "interval/interval-history/src/history/HistoryViewModel.kt"
        source.parent.mkdir(parents=True)
        source.write_text("class HistoryViewModel(private val lift: LiftActivity)\n")
        assert lift_repository_uses(root) == [], "LiftActivity is fine"
        source.write_text("class HistoryViewModel(private val lift: LiftRepository)\n")
        assert lift_repository_uses(root), "the interval side never names LiftRepository"
    print(f"{len(breaches) + 5} cases pass")


def main() -> None:
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    modules = load()
    problems = check(modules) + lift_repository_uses(ROOT)
    if problems:
        sys.exit("\n".join(problems))
    edges = sum(len(m.deps) + len(m.test_deps) for m in modules.values())
    print(f"{len(modules)} modules and {edges} module dependencies keep the rules")


if __name__ == "__main__":
    main()
