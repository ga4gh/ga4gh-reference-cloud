"""Resolve allowlist workflow sources (D20) to a pinned git repository and commit

git and TRS entries are pinned to a full commit sha, resolved when the entry is added. Only GitHub-hosted repositories
are supported for now: the workflow_url a researcher submits is GitHub's URL for the main script at that commit.
"""

import json
import os
import re
import shutil
import subprocess  # noqa: S404 - runs `git ls-remote` with a fixed argument list (no shell); inputs are validated
import urllib.error
import urllib.parse
import urllib.request

FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
SHORT_SHA = re.compile(r"^[0-9a-f]{7,39}$")
GITHUB_REPOSITORY = re.compile(
    r"^https://github\.com/(?P<owner>[A-Za-z0-9_.-]+)/(?P<repo>[A-Za-z0-9_.-]+?)(?:\.git)?/?$"
)
TRS_DESCRIPTOR_URL = re.compile(
    r"^(?P<server>https://[^/?#]+(?:/[^?#]*)?/ga4gh/trs/v2)/tools/(?P<tool>[^/?#]+)"
    r"/versions/(?P<version>[^/?#]+)/(?P<type>[A-Z_]+)/descriptor$"
)
RAW_GITHUB_URL = re.compile(r"^https://raw\.githubusercontent\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+)/")
BUNDLED_NAME = re.compile(r"^[a-z0-9][a-z0-9-]{0,62}$")

UNSUPPORTED_REPOSITORY = (
    "only GitHub-hosted repositories are currently supported; other repository systems (e.g. GitLab, Bitbucket) "
    "are not supported yet"
)


class SourceError(Exception):
    """A workflow source can't be resolved to a pinned, supported git commit"""


def github_repository(repository_url):
    """Canonical https://github.com/{owner}/{repo} for a GitHub repository URL"""
    match = GITHUB_REPOSITORY.match(repository_url)
    if match is None:
        raise SourceError(f"repository ({repository_url}): {UNSUPPORTED_REPOSITORY}")
    return f"https://github.com/{match['owner']}/{match['repo']}"


def resolve_commit(repository_url, ref):
    """Resolve a tag or full commit sha to a full commit sha. Branches are refused: they move, so they can't pin"""
    if FULL_SHA.match(ref):
        return ref
    if SHORT_SHA.match(ref):
        raise SourceError(f"ref ({ref}) looks like a short commit sha; use the full 40-character sha")

    git = shutil.which("git")
    if git is None:
        raise SourceError("git is required to resolve tags to commits, but the git command was not found")

    tag, peeled_tag, branch = f"refs/tags/{ref}", f"refs/tags/{ref}^{{}}", f"refs/heads/{ref}"
    try:
        result = subprocess.run(  # noqa: S603 - fixed argv, https-only github url validated above, no shell
            [git, "ls-remote", repository_url, tag, peeled_tag, branch],
            capture_output=True,
            text=True,
            timeout=60,
            check=True,
            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},  # never prompt for credentials
        )
    except subprocess.CalledProcessError as error:
        raise SourceError(f"git ls-remote failed for {repository_url}: {error.stderr.strip()}") from None
    except subprocess.TimeoutExpired:
        raise SourceError(f"git ls-remote timed out for {repository_url}") from None

    # ls-remote prints "<sha>\t<ref name>" per matching ref
    refs = {}
    for line in result.stdout.splitlines():
        sha, separator, name = line.partition("\t")
        if separator:
            refs[name] = sha
    # an annotated tag points at a tag object; its peeled ref (^{}) is the commit
    commit = refs.get(peeled_tag) or refs.get(tag)
    if commit:
        return commit
    if branch in refs:
        raise SourceError(f"ref ({ref}) is a branch in {repository_url}; pin a tag or a full commit sha instead")
    raise SourceError(f"ref ({ref}) was not found as a tag in {repository_url}")


def github_blob_url(repository_url, commit, main_script):
    """The workflow_url for a git entry: GitHub's URL for the main script at the pinned commit"""
    return f"{repository_url}/blob/{commit}/{main_script.lstrip('/')}"


def resolve_trs(descriptor_url, expected_type):
    """Parse a TRS descriptor URL and resolve the version to its GitHub repository and commit

    Returns (canonical descriptor url, trs server url, tool id, version id, repository url, commit)
    """
    match = TRS_DESCRIPTOR_URL.match(descriptor_url)
    if match is None:
        raise SourceError(
            f"({descriptor_url}) is not a TRS descriptor URL; expected "
            "https://{host}/.../ga4gh/trs/v2/tools/{tool_id}/versions/{version_id}/{type}/descriptor"
        )
    if match["type"] != expected_type:
        raise SourceError(f"descriptor type is {match['type']}, but this platform runs {expected_type} workflows")

    server = match["server"]
    tool_id = urllib.parse.unquote(match["tool"])
    version_id = urllib.parse.unquote(match["version"])
    # one canonical spelling, so the url a researcher copies from the allowlist always matches exactly
    canonical_url = (
        f"{server}/tools/{urllib.parse.quote(tool_id, safe='')}"
        f"/versions/{urllib.parse.quote(version_id, safe='')}/{expected_type}/descriptor"
    )

    descriptor = _fetch_json(canonical_url)
    raw_match = RAW_GITHUB_URL.match(descriptor.get("url") or "")
    if raw_match is None:
        raise SourceError(
            f"TRS tool ({tool_id}) is not hosted on GitHub ({descriptor.get('url')}): {UNSUPPORTED_REPOSITORY}"
        )

    repository_url = github_repository(f"https://github.com/{raw_match['owner']}/{raw_match['repo']}")
    commit = resolve_commit(repository_url, version_id)
    return canonical_url, server, tool_id, version_id, repository_url, commit


def _fetch_json(url):
    if not url.startswith("https://"):
        raise SourceError(f"refusing to fetch a non-https url ({url})")
    request = urllib.request.Request(url, headers={"Accept": "application/json"})  # noqa: S310 - https-only
    try:
        with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310 - https-only, checked above
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SourceError(f"TRS request failed ({error.code}) for {url}") from None
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        raise SourceError(f"TRS request failed for {url}: {error}") from None
