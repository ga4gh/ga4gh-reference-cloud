package org.ga4gh.refcloud.api.compute.platformworkflow;

// where an allowlisted workflow comes from (D20); matches the postgres enum compute_workflow_source_kind
public enum ComputeWorkflowSourceKind {
    // a git repository pinned to a commit
    git,
    // a version on an external TRS server, resolved to its source git repository and commit when allowlisted
    trs,
    // a workflow shipped in this repo
    bundled
}
