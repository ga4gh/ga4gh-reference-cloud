{{/*
The local compute connection (nextflow-funnel-kubernetes-local), as YAML: Nextflow Jobs and Funnel in this
release's compute namespace, with run storage in the S3-compatible RustFS (templates/apps/rustfs/, spec 0012):
core-api's own RustFS user, from refcloud-compute-s3-core-api in its environment, and STS credentials limited to each
run's prefix for its Nextflow Job. RustFS issues those for core-api's user itself and ignores the role ARN (any
well-formed one does), for up to 12 hours. Read with fromYaml by templates/common/compute-connections.yaml (the
connections file), templates/common/compute-resource-quota.yaml (the quota is computed from its limits),
templates/common/compute-limit-range.yaml and Funnel's config.

Its engine settings are refcloud.computeEngine's, shared with the AWS connection: the Nextflow and nf-ga4gh versions
must match the Nextflow image's (compute/images/nextflow/Dockerfile), which refuses to run otherwise. Native mode pins
the same versions (deploy/local/compute/connections.yml).

Its limits fit minikube's default container (2 CPUs, about 6 GB) beside the rest of the release, which used 3.3 GB at
rest (spec 0011, Task 7): at most about 2.5 GB more at full load, as one run's Nextflow Job (cpu-cores, memory-gb),
1 GB of tasks in flight (each an executor with its resources) and their Funnel workers (up to 256Mi each). The demo
platform registered on it (scripts/refcloud-run-helm.sh) sets no per-user limits, so it follows these.
*/}}
{{- define "refcloud.localComputeConnection" -}}
{{- $namespace := include "refcloud.computeNamespace" . }}
{{- $local := dict
      "type" "nextflow-funnel-kubernetes"
      "engine" (merge (dict "cpu-cores" 1 "memory-gb" 1) (include "refcloud.computeEngine" . | fromYaml))
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
The engine settings both of the chart's connections share, as YAML: the pinned versions, the Nextflow image (the
lockstep tag), the compute namespace, where the Jobs run (ga4gh.referenceCloud.compute's node selector and tolerations,
as the connection writes them: a node selector is a list of key and value pairs) and the SELinux level the compute
pods share (refcloud.computeSelinuxLevel). Each connection adds the Job's size.

The versions must match the Nextflow image's (compute/images/nextflow/Dockerfile): the image refuses to run otherwise.
*/}}
{{- define "refcloud.computeEngine" -}}
{{- $compute := .Values.ga4gh.referenceCloud.compute }}
{{- $engine := dict
      "nextflow-version" "26.10.5"
      "nf-ga4gh-version" "1.5.0"
      "image" (include "refcloud.image" (dict "root" . "repository" "refcloud-nextflow"))
      "image-pull-policy" (.Values.image.pullPolicy | kebabcase)
      "namespace" (include "refcloud.computeNamespace" .)
      "selinux-level" (include "refcloud.computeSelinuxLevel" .) }}
{{- with $compute.nodeSelector }}
{{- $labels := list }}
{{- range $key, $value := . }}
{{- $labels = append $labels (dict "key" $key "value" $value) }}
{{- end }}
{{- $_ := set $engine "node-selector" $labels }}
{{- end }}
{{- with $compute.tolerations }}
{{- $_ := set $engine "tolerations" . }}
{{- end }}
{{- toYaml $engine }}
{{- end -}}

{{/*
The AWS compute connection (nextflow-funnel-kubernetes-aws; spec 0013), as YAML, from ga4gh.referenceCloud.compute.aws
(values the infra repo's Terraform outputs): Nextflow Jobs and Funnel in this release's compute namespace, with run
storage in an S3 bucket, reached through compute's private S3 endpoint (path-style). core-api uses its own role through
EKS Pod Identity (no keys), and gives each run's Nextflow Job credentials limited to its prefix from the run role: on
AWS, roles assumed from core-api's own session last at most an hour. The engine's size and the limits are the values'
(kebab-case, as in a connection), the rest as the local connection.
*/}}
{{- define "refcloud.awsComputeConnection" -}}
{{- $namespace := include "refcloud.computeNamespace" . }}
{{- $aws := .Values.ga4gh.referenceCloud.compute.aws }}
{{- range $setting := list "region" "runRoleArn" "s3Endpoint" }}
{{- if not (index $aws $setting) }}
{{- fail (printf "ga4gh.referenceCloud.compute.aws.%s is required when aws.bucket is set (the infra repo's Terraform output, compute)" $setting) }}
{{- end }}
{{- end }}
{{- if not $aws.s3EndpointIps }}
{{- fail "ga4gh.referenceCloud.compute.aws.s3EndpointIps is required when aws.bucket is set (the infra repo's Terraform output, compute)" }}
{{- end }}
{{- $connection := dict
      "type" "nextflow-funnel-kubernetes"
      "engine" (merge (deepCopy $aws.engine) (include "refcloud.computeEngine" . | fromYaml))
      "executor" (dict "tes-url" (printf "http://funnel.%s.svc:8000" $namespace))
      "storage" (dict
        "type" "s3"
        "bucket" $aws.bucket
        "region" $aws.region
        "endpoint" $aws.s3Endpoint
        "path-style-access" true
        "run-role-arn" $aws.runRoleArn
        "run-credentials-max-seconds" 3600)
      "limits" $aws.limits }}
{{- toYaml $connection }}
{{- end -}}

{{/*
One of Funnel's job or storage templates (files/funnel/), which Funnel renders as a Go template for each task, with
the chart's placeholders replaced: whole lines first ("placeholders": the line, with its indentation and newline, and
its replacement, already indented, or "" to remove it), then single values ("values"). Fails if a placeholder is left.
Usage: {{ include "refcloud.funnelTemplate" (dict "root" $ "file" "worker-job.yaml" "placeholders" $lines "values" $values) }}
*/}}
{{- define "refcloud.funnelTemplate" -}}
{{- $content := .root.Files.Get (printf "files/funnel/%s" .file) }}
{{- range $line, $replacement := .placeholders }}
{{- $content = replace $line $replacement $content }}
{{- end }}
{{- range $placeholder, $value := .values }}
{{- $content = replace $placeholder $value $content }}
{{- end }}
{{- if contains "REFCLOUD_" $content }}
{{- fail (printf "files/funnel/%s has a placeholder the chart doesn't replace" .file) }}
{{- end }}
{{- $content }}
{{- end -}}

{{/*
Whether this release runs compute: always locally; on AWS when ga4gh.referenceCloud.compute.aws.bucket is set. Compute
is off otherwise, and the chart renders no compute namespace, Funnel or connection of its own ("true" or "").
*/}}
{{- define "refcloud.computeEnabled" -}}
{{- $environment := .Values.ga4gh.referenceCloud.cloudEnvironment }}
{{- if or (eq $environment "local") (and (eq $environment "aws") .Values.ga4gh.referenceCloud.compute.aws.bucket) -}}
true
{{- end }}
{{- end -}}

{{/*
The chart's own compute connection's name: nextflow-funnel-kubernetes-<cloudEnvironment> (local or aws).
*/}}
{{- define "refcloud.computeConnectionName" -}}
nextflow-funnel-kubernetes-{{ .Values.ga4gh.referenceCloud.cloudEnvironment }}
{{- end -}}

{{/*
The chart's own compute connection, as YAML: the local one, or the AWS one. Read with fromYaml by the connections file,
the compute namespace's ResourceQuota and LimitRange, and Funnel's config. Only when refcloud.computeEnabled.
*/}}
{{- define "refcloud.computeConnection" -}}
{{- if eq .Values.ga4gh.referenceCloud.cloudEnvironment "aws" }}
{{- include "refcloud.awsComputeConnection" . }}
{{- else }}
{{- include "refcloud.localComputeConnection" . }}
{{- end }}
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
