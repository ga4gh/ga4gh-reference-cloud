package org.ga4gh.refcloud.api.compute.kubernetes;

// something core-api's service account must be allowed to do in a namespace: a verb on a resource in an API group
// ("" for the core group, e.g. pods and secrets; "batch" for jobs)
public record KubernetesPermission(String group, String resource, String verb) {

    public String describe() {
        return verb + " " + (group.isEmpty() ? resource : resource + "." + group);
    }
}
