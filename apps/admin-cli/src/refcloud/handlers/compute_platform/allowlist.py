"""compute-platform allowlist add, remove and list (D20, D21)"""

from pathlib import Path

import click

from refcloud.config.database_engine import DatabaseEngine
from refcloud.handlers.compute_platform import fail, get_connection_or_fail, get_platform_or_fail
from refcloud.handlers.compute_platform.sources import (
    BUNDLED_NAME,
    SourceError,
    github_blob_url,
    github_repository,
    resolve_commit,
    resolve_trs,
)
from refcloud.handlers.visa import echo_table

# bundled workflows live in this repo under compute/workflows/{engine}/{name}; the stored path is relative to the
# repo root. checked from apps/admin-cli, where the CLI runs locally
BUNDLED_WORKFLOWS_DIRECTORY = "compute/workflows/nextflow"
REPO_ROOT_FROM_CLI = Path("../..")


def allowlist_add(
    platform_id,
    name,
    description,
    git_repository,
    git_ref,
    trs_descriptor_url,
    bundled,
    main_script,
    profiles,
    engine_env,
):
    platform = get_platform_or_fail(platform_id)
    connection = get_connection_or_fail(platform.connection_key)

    sources_given = [source for source in (git_repository, trs_descriptor_url, bundled) if source is not None]
    if len(sources_given) != 1:
        fail("give exactly one source: --git-repository (with --ref), --trs-descriptor-url, or --bundled")

    entry = {
        "compute_platform_id": platform_id,
        "name": name,
        "description": description,
        "workflow_type": connection.workflow_type,
        "workflow_type_version": connection.workflow_type_version,
        "engine_profiles": list(profiles),
        "engine_env": parse_engine_env(engine_env),
    }
    try:
        entry |= resolve_source(
            git_repository, git_ref, trs_descriptor_url, bundled, main_script, connection.workflow_type
        )
    except SourceError as error:
        fail(str(error))

    database_engine = DatabaseEngine()
    if database_engine.get_compute_platform_workflow_by_url(platform_id, entry["workflow_url"]) is not None:
        fail(f"workflow ({entry['workflow_url']}) is already on the allowlist of compute platform ({platform_id})")

    workflow_id = database_engine.insert_compute_platform_workflow(**entry)
    click.echo(f"done: added workflow {workflow_id} to the allowlist of compute platform ({platform_id})")
    click.echo(f"  researchers submit it with workflow_url: {entry['workflow_url']}")


def resolve_source(git_repository, git_ref, trs_descriptor_url, bundled, main_script, workflow_type):
    if git_repository is not None:
        if git_ref is None:
            raise SourceError("--git-repository needs --ref (a tag or a full commit sha)")
        repository_url = github_repository(git_repository)
        commit = resolve_commit(repository_url, git_ref)
        return {
            "source_kind": "git",
            "workflow_url": github_blob_url(repository_url, commit, main_script),
            "git_repository_url": repository_url,
            "git_commit": commit,
            "git_main_script": main_script,
        }

    if git_ref is not None:
        raise SourceError("--ref only applies to --git-repository")

    if trs_descriptor_url is not None:
        canonical_url, server, tool_id, version_id, repository_url, commit = resolve_trs(
            trs_descriptor_url, workflow_type
        )
        return {
            "source_kind": "trs",
            "workflow_url": canonical_url,
            "git_repository_url": repository_url,
            "git_commit": commit,
            "git_main_script": main_script,
            "trs_server_url": server,
            "trs_tool_id": tool_id,
            "trs_version_id": version_id,
        }

    if not BUNDLED_NAME.match(bundled):
        raise SourceError(f"bundled workflow name ({bundled}) must be lowercase letters, digits and hyphens")
    bundled_path = f"{BUNDLED_WORKFLOWS_DIRECTORY}/{bundled}"
    if not (REPO_ROOT_FROM_CLI / bundled_path).is_dir():
        # only checkable from a repo checkout; core-api checks again when it runs the workflow
        click.echo(f"warning: {bundled_path} was not found relative to the repo root; check the name")
    return {"source_kind": "bundled", "workflow_url": f"refcloud:bundled/{bundled}", "bundled_path": bundled_path}


def parse_engine_env(pairs):
    engine_env = {}
    for pair in pairs:
        key, separator, value = pair.partition("=")
        if not separator or not key:
            fail(f"--engine-env must be KEY=VALUE (got {pair})")
        engine_env[key] = value
    return engine_env


def allowlist_remove(platform_id, workflow_id):
    platform = get_platform_or_fail(platform_id)
    database_engine = DatabaseEngine()
    if not database_engine.delete_compute_platform_workflow(platform_id, workflow_id):
        fail(f"workflow {workflow_id} is not on the allowlist of compute platform ({platform_id})")
    click.echo(f"done: removed workflow {workflow_id} from the allowlist of compute platform ({platform_id})")
    if platform.enabled and not database_engine.list_compute_platform_workflows(platform_id):
        click.echo(f"warning: compute platform ({platform_id}) is enabled but its allowlist is now empty")


def allowlist_list(platform_id):
    get_platform_or_fail(platform_id)
    workflows = DatabaseEngine().list_compute_platform_workflows(platform_id)
    if not workflows:
        click.echo(f"compute platform ({platform_id}) has no allowlisted workflows")
        return

    rows = [
        [
            workflow.id,
            workflow.name,
            workflow.source_kind,
            pinned_source(workflow),
            " ".join(f"-profile {profile}" for profile in workflow.engine_profiles)
            + "".join(f" {key}={value}" for key, value in (workflow.engine_env or {}).items()),
            workflow.workflow_url,
        ]
        for workflow in workflows
    ]
    echo_table(["ID", "NAME", "SOURCE", "PINNED TO", "ENGINE SETTINGS", "WORKFLOW URL (submit exactly)"], rows)


def pinned_source(workflow):
    if workflow.source_kind == "bundled":
        return workflow.bundled_path
    pinned = f"{workflow.git_repository_url}@{workflow.git_commit[:12]}"
    if workflow.source_kind == "trs":
        return f"{workflow.trs_tool_id}:{workflow.trs_version_id} -> {pinned}"
    return pinned
