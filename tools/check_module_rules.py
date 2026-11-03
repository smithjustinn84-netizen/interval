#!/usr/bin/env python3
"""Fails when a module depends on one it shouldn't, by the rules CLAUDE.md's "Modules" section describes.

Each module has a side (its folder: core, interval, lift, apps, tooling) and a kind (time, core-platform, core,
core-testing, domain, api, data, platform, ui, testing, feature, entry, app, host). The rules:
- sides: core sees only core; interval sees interval, lift and core; lift sees lift and core, never interval;
  apps sees the rest; tooling sees nothing. Test dependencies follow this rule too.
- kinds: each kind may depend on the kinds below it (`ALLOWED`). A feature depends on another feature only
  where `FEATURE_EDGES` lists the pair; interval may use only Lift's domain, api and ui, never its storage.
- a platform with an api module (`interval-platform-api`) is seen through it: features and ui take the api,
  and only the app, the testing modules and the platform's own tests see the platform.
- the `*-testing` modules and `core-testing` are for `test-dependencies`, outside the testing modules. Any other
  test dependency is one the module's `dependencies` could take.
- the pure layers (time, core-platform, domain, api, data) take no Compose: no `$compose.*`, `$libs.compose.*` or
  `$libs.koin.compose`, no `compose-lib`, and no `settings: compose: enabled`.
- every library (all but the hosts and the tooling plugins) applies a template from `templates/`.
- no source under `interval/*/src*` names `LiftRepository`: the interval side reads Lift through `LiftActivity`.
- the module diagram in docs/architecture.md is the one these modules draw (`--diagram` prints it,
  `--write-diagram` puts it in the doc): each module's `dependencies`, the testing modules and the build plugins
  left out, reduced to the edges no other already implies (`app` lists `interval-domain`, but reaches it through
  `interval-timer`), one subgraph per layer.

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
ARCHITECTURE = ROOT / "docs" / "architecture.md"

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
    # The two domains stand apart: a Lift session becomes interval's `LiftWorkout` in interval-history.
    "domain": {"time"},
    # The contracts a side offers: the platform's services (`interval-platform-api`), the ports between the sides
    # (`lift-api`). Over a domain, with nothing of the platform's or the storage's.
    "api": {"time", "domain"},
    "data": {"time", "core-platform", "domain", "api"},
    "platform": FOUNDATION | {"domain", "api", "data"},
    "ui": FOUNDATION | {"domain", "api"},
    "testing": FOUNDATION | {"core-testing", "domain", "api", "data", "platform", "testing"},
    "feature": FOUNDATION | {"domain", "api", "data", "platform", "ui", "feature"},
    "entry": FOUNDATION | {"domain", "api", "data", "platform", "ui", "feature"},
    "app": FOUNDATION | {"domain", "api", "data", "platform", "ui", "feature", "entry"},
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

# What the interval side may use of Lift's: its ports, never its storage, features, platform or navigation.
CROSS_SIDE_KINDS = {"domain", "api", "ui"}
# Who may see a platform that has an api module; everyone else takes the api.
PLATFORM_SEERS = {"app", "testing"}

TEST_ONLY = {"testing", "core-testing"}
NO_COMPOSE = {"time", "core-platform", "domain", "api", "data"}
# What brings Compose in under `dependencies`.
COMPOSE_PREFIXES = ("$compose.", "$libs.compose.", "$libs.koin.compose")
# The kinds that apply no template: the hosts are applications, the tooling plugins build plugins.
UNTEMPLATED = {"host", "tooling"}
# The interval side reads Lift through `LiftActivity`, never the whole repository.
LIFT_REPOSITORY = re.compile(r"\bLiftRepository\b")

CORE_KINDS = {"core-time": "time", "core-platform": "core-platform", "core": "core", "core-testing": "core-testing"}
SUFFIX_KINDS = {"domain", "api", "data", "platform", "ui", "testing"}

# The diagram's layers, top down, and the kind of module each draws; a feature's is its side's. The testing
# modules and the build plugins are not drawn: nothing the app runs depends on them. `app` stands alone between
# the hosts and the features.
LAYERS = [
    "Application targets",
    "",
    "Interval features",
    "Lift features",
    "Shared UI",
    "Platform services",
    "Data",
    "Contracts",
    "Pure layers",
]
LAYER_OF_KIND = {
    "host": "Application targets",
    "app": "",
    "entry": "Lift features",
    "ui": "Shared UI",
    "core": "Shared UI",
    "platform": "Platform services",
    "data": "Data",
    "api": "Contracts",
    "domain": "Pure layers",
    "core-platform": "Pure layers",
    "time": "Pure layers",
}
# The doc's diagram: the one fenced Mermaid graph in docs/architecture.md.
DIAGRAM = re.compile(r"(?<=```mermaid\n)graph TD\n.*?(?=```)", re.S)


@dataclass
class Module:
    name: str
    side: str
    deps: set[str] = field(default_factory=set)  # module names under `dependencies*`
    test_deps: set[str] = field(default_factory=set)  # module names under `test-dependencies*`
    compose: list[str] = field(default_factory=list)  # what brings Compose in, if anything
    templates: list[str] = field(default_factory=list)  # its own `apply:` entries


def a(kind: str) -> str:
    """[kind] with its article: "a data", "an api"."""
    return f"{'an' if kind[0] in 'aeiou' else 'a'} {kind}"


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
                    problems.append(f"{where}: {a(kind)} module's tests don't depend on {a(dep_kind)} module")
                continue
            if dep_kind in TEST_ONLY and kind not in {"testing", "core-testing"}:
                problems.append(f"{where}: a testing module belongs under test-dependencies")
            elif dep_kind not in ALLOWED[kind]:
                problems.append(f"{where}: {a(kind)} module doesn't depend on {a(dep_kind)} module")
            elif module.side == "interval" and dep.side == "lift" and dep_kind not in CROSS_SIDE_KINDS:
                problems.append(f"{where}: the interval side uses only Lift's domain, api and ui")
            elif dep_kind == "platform" and f"{dep.name}-api" in modules and kind not in PLATFORM_SEERS:
                problems.append(f"{where}: {a(kind)} module takes {dep.name}-api, not the platform")
            elif kind == "feature" and dep_kind == "feature" and (module.name, dep.name) not in FEATURE_EDGES:
                problems.append(f"{where}: features don't depend on each other (add it to FEATURE_EDGES if it must)")
        if kind in NO_COMPOSE and module.compose:
            problems.append(f"{module.name}: {a(kind)} module takes no Compose ({', '.join(sorted(set(module.compose)))})")
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


def layer_of(module: Module) -> str | None:
    """The diagram layer [module] is drawn in, or None for one that isn't."""
    kind = kind_of(module.name, module.side)
    if kind == "feature":
        return "Interval features" if module.side == "interval" else "Lift features"
    return LAYER_OF_KIND.get(kind)


def reduced(graph: dict[str, set[str]]) -> dict[str, set[str]]:
    """Each node's edges that no path through its other edges already implies: the transitive reduction."""
    reach: dict[str, set[str]] = {}

    def reachable(node: str) -> set[str]:
        if node not in reach:
            reach[node] = set()  # stops a cycle, which the rules refuse anyway
            found = set()
            for dep in graph.get(node, ()):
                found.add(dep)
                found |= reachable(dep)
            reach[node] = found
        return reach[node]

    return {
        node: {dep for dep in deps if not any(dep in reachable(other) for other in deps if other != dep)}
        for node, deps in graph.items()
    }


def node_id(name: str) -> str:
    """A Mermaid id for a module: `interval-ui` is `intervalUi`."""
    first, *rest = name.split("-")
    return first + "".join(part.capitalize() for part in rest)


def diagram(modules: dict[str, Module]) -> str:
    """The Mermaid graph of the modules drawn (`layer_of`), a subgraph per layer, the edges reduced."""
    drawn = {name: module for name, module in modules.items() if layer_of(module) is not None}
    edges = reduced({name: {dep for dep in module.deps if dep in drawn} for name, module in drawn.items()})
    lines, order = ["graph TD"], []
    for layer in LAYERS:
        members = sorted(name for name, module in drawn.items() if layer_of(module) == layer)
        order += members
        if layer:
            lines.append(f'    subgraph {node_id(layer.lower().replace(" ", "-"))} ["{layer}"]')
            lines += [f'        {node_id(name)}["{name}"]' for name in members]
            lines.append("    end")
        else:
            lines += [f'    {node_id(name)}["{name}"]' for name in members]
        lines.append("")
    for layer in LAYERS:
        drawn_edges = [
            f"    {node_id(name)} --> {node_id(dep)}"
            for name in order
            if layer_of(drawn[name]) == layer
            for dep in sorted(edges[name], key=order.index)
        ]
        if drawn_edges:
            lines += drawn_edges + [""]
    return "\n".join(lines[:-1]) + "\n"


def doc_diagram(doc: Path) -> str | None:
    """The diagram [doc] holds, or None when it has none."""
    found = DIAGRAM.search(doc.read_text())
    return found.group(0) if found else None


def write_diagram(doc: Path, drawn: str) -> None:
    doc.write_text(DIAGRAM.sub(lambda _: drawn, doc.read_text(), count=1))


def stale_diagram(doc: Path, drawn: str) -> list[str]:
    """A problem when [doc]'s diagram isn't [drawn]."""
    if doc_diagram(doc) == drawn:
        return []
    return [f"{doc.name}: the module diagram is not the modules' (python3 tools/check_module_rules.py --write-diagram)"]


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
            fake("lift-api", "lift", ["lift-domain"]),
            fake("lift-data", "lift", ["lift-domain", "lift-api", "core-platform"]),
            fake("lift-ui", "lift", ["lift-domain", "core"]),
            fake("lift-library", "lift", ["lift-data", "lift-ui", "core"]),
            fake("interval-domain", "interval", ["core-time"]),
            fake("interval-data", "interval", ["interval-domain", "core-platform"]),
            fake("interval-platform-api", "interval", ["interval-domain"], ["interval-testing"]),
            fake("interval-platform", "interval", ["interval-platform-api", "interval-data", "core-platform"]),
            fake("interval-testing", "interval", ["interval-platform-api", "interval-data", "core-testing"]),
            fake("interval-presets", "interval", ["interval-data", "core"], ["interval-testing"]),
            fake("interval-timer", "interval", ["interval-presets", "core"]),
            fake("interval-settings", "interval", ["interval-data", "interval-platform-api", "core"]),
            fake("interval-history", "interval", ["interval-data", "lift-api", "lift-domain", "lift-ui", "core"]),
            fake("app", "apps", ["interval-timer", "interval-platform", "lift-library", "core"], ["interval-testing"]),
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
        "the domains stand apart": ("interval-domain", "deps", "lift-domain", "doesn't depend on a domain module"),
        "testing only under tests": ("interval-presets", "deps", "interval-testing", "belongs under test-dependencies"),
        "features stand alone": ("interval-settings", "deps", "interval-presets", "features don't depend on each other"),
        "no Lift features on interval": ("interval-timer", "deps", "lift-library", "uses only Lift's domain, api and ui"),
        "no Lift storage on interval": ("interval-history", "deps", "lift-data", "uses only Lift's domain, api and ui"),
        "features take the platform's api": ("interval-settings", "deps", "interval-platform", "takes interval-platform-api"),
        "an api takes no storage": ("interval-platform-api", "deps", "interval-data", "an api module doesn't depend on a data"),
        "an api takes no core": ("lift-api", "deps", "core", "an api module doesn't depend on a core module"),
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
    graph["lift-api"].compose.append("$compose.runtime")
    assert any("takes no Compose" in p for p in check(graph)), "an api takes no Compose"

    graph = {k: fake(v.name, v.side, v.deps, v.test_deps, v.compose) for k, v in base.items()}
    graph["lift-ui"].templates.clear()
    assert any("applies a template" in p for p in check(graph)), "every library applies a template"

    assert reduced({"a": {"b", "c"}, "b": {"c"}, "c": set()}) == {"a": {"b"}, "b": {"c"}, "c": set()}, "an implied edge is dropped"
    assert reduced({"a": {"b", "c"}, "b": set(), "c": set()}) == {"a": {"b", "c"}, "b": set(), "c": set()}, "direct edges stay"
    assert node_id("interval-platform-api") == "intervalPlatformApi" and node_id("androidApp") == "androidApp"
    drawn = diagram(base)
    assert drawn.startswith("graph TD\n    subgraph applicationTargets [\"Application targets\"]\n        androidApp[\"androidApp\"]\n")
    assert '    app["app"]' in drawn, "app stands alone"
    assert "subgraph intervalFeatures" in drawn and "subgraph pureLayers" in drawn, "a subgraph per layer"
    assert "Testing" not in drawn and "testing" not in drawn, "the testing modules aren't drawn"
    assert "    app --> intervalTimer\n" in drawn and "    app --> core\n" not in drawn, "an edge another implies is left out"
    assert "    intervalTimer --> intervalPresets\n" in drawn and "    liftApi --> liftDomain\n" in drawn
    assert drawn == diagram(base), "the diagram is the same each time"

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        doc = root / "architecture.md"
        doc.write_text("# Architecture\n\n```mermaid\ngraph TD\n    old[\"old\"]\n```\n\nAfter.\n")
        assert doc_diagram(doc) == 'graph TD\n    old["old"]\n'
        assert stale_diagram(doc, drawn), "a diagram the modules don't draw is stale"
        write_diagram(doc, drawn)
        assert stale_diagram(doc, drawn) == [], "the written diagram is current"
        assert doc.read_text().startswith("# Architecture\n\n```mermaid\ngraph TD\n    subgraph") and doc.read_text().endswith("```\n\nAfter.\n")
        doc.write_text("# Architecture\n\nNo diagram.\n")
        assert doc_diagram(doc) is None and stale_diagram(doc, drawn), "a doc without the diagram is stale"

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
    print(f"{len(breaches) + 8} cases pass")


def main() -> None:
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    modules = load()
    if sys.argv[1:] == ["--diagram"]:
        print(diagram(modules), end="")
        return
    if sys.argv[1:] == ["--write-diagram"]:
        write_diagram(ARCHITECTURE, diagram(modules))
        print(f"{ARCHITECTURE.relative_to(ROOT)} draws the modules as they are")
        return
    problems = check(modules) + lift_repository_uses(ROOT) + stale_diagram(ARCHITECTURE, diagram(modules))
    if problems:
        sys.exit("\n".join(problems))
    edges = sum(len(m.deps) + len(m.test_deps) for m in modules.values())
    print(f"{len(modules)} modules and {edges} module dependencies keep the rules, and the diagram draws them")


if __name__ == "__main__":
    main()
