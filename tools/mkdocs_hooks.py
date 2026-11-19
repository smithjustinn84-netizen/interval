"""MkDocs hooks for the documentation site (mkdocs.yml).

The developer docs are the files in docs/ as they are, and they link to things the site doesn't publish: files at
the repository's root (../CHANGELOG.md, ../project.yaml), the sprint plans, the backlog and the archive. A relative
link whose target is not a page or file on the site is pointed at the file on GitHub instead, so the docs read
the same in the repository and on the site, and `mkdocs build --strict` finds no broken link.
"""

import posixpath
import re

REPO_BLOB = "https://github.com/smithjustinn84-netizen/interval/blob/main/"

# [text](target) or [text](target#anchor), not images' own (those are always on the site), not absolute URLs.
LINK = re.compile(r"(?<!!)\[(?P<text>[^\]]*)\]\((?P<target>[^)\s#]+)(?P<anchor>#[^)\s]*)?\)")


def on_page_markdown(markdown, page, config, files, **kwargs):
    published = {f.src_uri for f in files if not f.inclusion.is_excluded()}
    page_dir = posixpath.dirname(page.file.src_uri)

    def rewrite(match):
        target = match.group("target")
        if re.match(r"^[a-z][a-z0-9+.-]*:", target) or target.startswith("/"):
            return match.group(0)
        resolved = posixpath.normpath(posixpath.join(page_dir, target))
        if resolved in published:
            return match.group(0)
        # Relative to the repository's root, where docs/ is the docs_dir.
        in_repo = posixpath.normpath(posixpath.join("docs", resolved))
        anchor = match.group("anchor") or ""
        return f"[{match.group('text')}]({REPO_BLOB}{in_repo}{anchor})"

    return LINK.sub(rewrite, markdown)
