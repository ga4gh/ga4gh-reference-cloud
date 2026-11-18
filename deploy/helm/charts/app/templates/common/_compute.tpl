{{/*
The local compute connection (nextflow-funnel-kubernetes-local), as YAML: Nextflow Jobs and Funnel in this
release's compute namespace, with run storage in the S3-compatible RustFS (templates/apps/rustfs/, spec 0012):
core-api's own RustFS user, from refcloud-compute-s3-core-api in its environment, and STS credentials limited to each
run's prefix for its Nextflow Job. RustFS issues those for core-api's user itself and ignores the role ARN (any
well-formed one does), for up to 12 hours. Read with fromYaml by templates/common/compute-connections.yaml (the
connections file), templates/common/compute-resource-quota.yaml (the quota is computed from its limits),
templates/common/compute-limit-range.yaml and Funnel's config.

Its Nextflow and nf-ga4gh versions must match the Nextflow image's (compute/images/nextflow/Dockerfile): the image
refuses to run otherwise. Native mode pins the same versions (deploy/local/compute/connections.yml).

Its limits fit minikube's default container (2 CPUs, about 6 GB) beside the rest of the release, which used 3.3 GB at
rest (spec 0011, Task 7): at most about 2.5 GB more at full load, as one run's Nextflow Job (cpu-cores, memory-gb),
1 GB of tasks in flight (each an executor with its resources) and their Funnel workers (up to 256Mi each). The demo
platform registered on it (scripts/refcloud-run-helm.sh) sets no per-user limits, so it follows these.
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
      "storage" (dict
        "type" "s3"
        "bucket" (include "refcloud.localComputeBucket" .)
        "region" "us-east-1"
        "endpoint" (printf "http://rustfs.%s.svc.cluster.local:9000" .Release.Namespace)
        "path-style-access" true
        "access-key-id-env" "REFCLOUD_COMPUTE_S3_ACCESS_KEY_ID"
        "secret-access-key-env" "REFCLOUD_COMPUTE_S3_SECRET_ACCESS_KEY"
        "run-role-arn" "arn:aws:iam::000000000000:role/refcloud-compute-run"
        "run-credentials-max-seconds" 43200)
      "limits" (dict
        "max-concurrent-runs" 1
        "max-tasks-in-flight" 2
        "max-cpu-cores-in-flight" 2
        "max-memory-gb-in-flight" 1
        "max-task-cpu-cores" 1
        "max-task-memory-gb" 1
        "max-task-disk-gb" 10
        "max-run-duration-seconds" 3600
        "default-task-cpu-cores" 1
        "default-task-memory-gb" 1
        "max-run-inputs-gb" 10) }}
{{- toYaml $local }}
{{- end -}}

{{/*
Egress rules for the compute namespace's NetworkPolicies (templates/common/compute-network-policies.yaml), as YAML
list items. The Kubernetes API rule renders nothing without peers (helm template).
Usage: {{- include "refcloud.computeEgressToKubernetesApi" (dict "peers" $apiPeers "ports" $apiPorts) | nindent 4 }}
*/}}
{{- define "refcloud.computeEgressToKubernetesApi" -}}
{{- if .peers }}
# the Kubernetes API server
- to:
    {{- toYaml .peers | nindent 4 }}
  ports:
    {{- toYaml .ports | nindent 4 }}
{{- end }}
{{- end -}}

{{- define "refcloud.computeEgressToDns" -}}
# DNS (kube-dns)
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
  ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
{{- end -}}

{{- define "refcloud.computeEgressToRustfs" -}}
# RustFS, the run storage, in the release namespace (spec 0012)
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: {{ .Release.Namespace }}
      podSelector:
        matchLabels:
          app.kubernetes.io/name: rustfs
  ports:
    - protocol: TCP
      port: 9000
{{- end -}}
