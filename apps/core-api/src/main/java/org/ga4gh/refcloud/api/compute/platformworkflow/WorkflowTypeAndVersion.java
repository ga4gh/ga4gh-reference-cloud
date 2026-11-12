package org.ga4gh.refcloud.api.compute.platformworkflow;

// a WES workflow_type and workflow_type_version pair, e.g. NFL and DSL2
public record WorkflowTypeAndVersion(String workflowType, String workflowTypeVersion) {}
