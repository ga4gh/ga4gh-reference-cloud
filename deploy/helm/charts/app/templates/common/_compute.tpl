{{/*
The local compute connection (nextflow-funnel-kubernetes-local), as YAML: Nextflow Jobs and Funnel in this
release's compute namespace, with run storage on the minikube node. Read with fromYaml by
templates/common/compute-connections.yaml (the connections file) and templates/common/compute-resource-quota.yaml
(the quota is computed from its limits).

Its Nextflow and nf-ga4gh versions must match the Nextflow image's (compute/images/nextflow/Dockerfile): the image
refuses to run otherwise. Native mode pins the same versions (deploy/local/compute/connections.yml).

Its limits are sized for minikube. Each run is a Nextflow Job (cpu-cores, memory-gb), and each task in flight is a
Funnel worker plus an executor with the task's resources.
*/}}
{{- define "refcloud.localComputeConnection" -}}
{{- $namespace := include "refcloud.computeNamespace" . }}
{{- $local := dict
      "type" "nextflow-funnel-kubernetes"
      "engine" (dict
        "nextflow-version" "26.10.5"
        "nf-ga4gh-version" "1.5.0"
        "image" (include "refcloud.image" (dict "root" . "repository" "refcloud-nextflow"))
        "image-pull-policy" (.Values.image.pullPolicy | kebabcase)
        "namespace" $namespace
        "cpu-cores" 1
        "memory-gb" 1)
      "executor" (dict "tes-url" (printf "http://funnel.%s.svc:8000" $namespace))
      "storage" (dict "type" "shared-filesystem" "root" (include "refcloud.computeStorageRoot" .))
      "limits" (dict
        "max-concurrent-runs" 2
        "max-tasks-in-flight" 2
        "max-cpu-cores-in-flight" 2
        "max-memory-gb-in-flight" 2
        "max-task-cpu-cores" 1
        "max-task-memory-gb" 1
        "max-task-disk-gb" 10
        "max-run-duration-seconds" 3600
        "default-task-cpu-cores" 1
        "default-task-memory-gb" 1
        "max-run-inputs-gb" 10) }}
{{- toYaml $local }}
{{- end -}}
