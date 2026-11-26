{{/*
The local compute connection (nextflow-funnel-kubernetes-local), as YAML: Nextflow Jobs and Funnel in this
release's compute namespace, with run storage in the S3-compatible RustFS (templates/apps/rustfs/, spec 0012):
core-api's own RustFS user, from refcloud-compute-s3-core-api in its environment, and STS credentials limited to each
run's prefix for its Nextflow Job. RustFS issues those at its own address (sts-endpoint), for core-api's user itself,
and ignores the role ARN (any well-formed one does), for up to 12 hours. Read with fromYaml by templates/common/compute-connections.yaml (the
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
        "sts-endpoint" (printf "http://rustfs.%s.svc.cluster.local:9000" .Release.Namespace)
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
EKS Pod Identity (no keys), and gives each run's Nextflow Job credentials limited to its prefix from the run role,
through AWS's regional STS (no sts-endpoint: the S3 endpoint serves only S3): on AWS, roles assumed from core-api's own
session last at most an hour. The engine's size and the limits are the values'
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
list items.

The Kubernetes API and DNS rules name each destination in both of the forms network plugins match, so the same rules
work everywhere: Calico (minikube) matches traffic to a Service after its translation, at the pods or addresses behind
it; EKS's policy agent matches the address the pod connects to (spec 0013, Task 5). Their addresses are looked up when
the chart is deployed ("addresses": refcloud.computeClusterAddresses); `helm template` can't look anything up, so it
renders the rules without them.
*/}}

{{/*
The cluster addresses the rules below need, looked up when the chart is deployed, as YAML:
  * apiService, apiServicePorts: the kubernetes Service's cluster IP (as NetworkPolicy peers) and ports;
  * apiServer, apiServerPorts: the API server's own addresses and ports, from that Service's EndpointSlices;
  * coreDns: CoreDNS's address on EKS Auto Mode, the .10 of the service range (the kubernetes Service is its .1), which
    is also kube-dns's address in minikube. IPv4 clusters only.
Fails on a deploy that reaches a cluster but finds no API server: the compute pods that need it couldn't reach it.
*/}}
{{- define "refcloud.computeClusterAddresses" -}}
{{- $addresses := dict "apiService" (list) "apiServicePorts" (list) "apiServer" (list) "apiServerPorts" (list) "coreDns" (list) }}
{{- with lookup "v1" "Service" "default" "kubernetes" }}
{{- $clusterIP := .spec.clusterIP }}
{{- $ipv6 := contains ":" $clusterIP }}
{{- $_ := set $addresses "apiService" (list (dict "ipBlock" (dict "cidr" (printf "%s/%s" $clusterIP (ternary "128" "32" $ipv6))))) }}
{{- $ports := list }}
{{- range .spec.ports }}
{{- $ports = append $ports (dict "protocol" (.protocol | default "TCP") "port" .port) }}
{{- end }}
{{- $_ := set $addresses "apiServicePorts" $ports }}
{{- if not $ipv6 }}
{{- $_ := set $addresses "coreDns" (list (dict "ipBlock" (dict "cidr" (printf "%s.10/32" (regexReplaceAll "\\.[0-9]+$" $clusterIP ""))))) }}
{{- end }}
{{- end }}
{{- $servers := list }}
{{- $serverPorts := list }}
{{- range (lookup "discovery.k8s.io/v1" "EndpointSlice" "default" "").items }}
{{- if eq (dig "metadata" "labels" "kubernetes.io/service-name" "" .) "kubernetes" }}
{{- range .endpoints }}
{{- range .addresses }}
{{- $servers = append $servers (dict "ipBlock" (dict "cidr" (printf "%s/%s" . (ternary "128" "32" (contains ":" .))))) }}
{{- end }}
{{- end }}
{{- range .ports }}
{{- $serverPorts = append $serverPorts (dict "protocol" (.protocol | default "TCP") "port" .port) }}
{{- end }}
{{- end }}
{{- end }}
{{- $_ := set $addresses "apiServer" $servers }}
{{- $_ := set $addresses "apiServerPorts" $serverPorts }}
{{- if and (not (and $addresses.apiService $servers)) (lookup "v1" "Namespace" "" "default") }}
{{- fail "the Kubernetes API's addresses weren't found (the kubernetes Service in the default namespace, and its EndpointSlices): the compute namespace's NetworkPolicies need them" }}
{{- end }}
{{- toYaml $addresses }}
{{- end -}}

{{/*
The Kubernetes API, at the kubernetes Service's address (EKS) and at the API server's own (Calico). Nothing without
addresses (helm template).
Usage: {{- include "refcloud.computeEgressToKubernetesApi" $addresses | nindent 4 }}
*/}}
{{- define "refcloud.computeEgressToKubernetesApi" -}}
{{- if .apiService }}
# the Kubernetes API, at the kubernetes Service's address
- to:
    {{- toYaml .apiService | nindent 4 }}
  ports:
    {{- toYaml .apiServicePorts | nindent 4 }}
{{- end }}
{{- if .apiServer }}
# the Kubernetes API, at the API server's own addresses
- to:
    {{- toYaml .apiServer | nindent 4 }}
  ports:
    {{- toYaml .apiServerPorts | nindent 4 }}
{{- end }}
{{- end -}}

{{/*
DNS: kube-dns's pods (minikube), and CoreDNS's address (EKS Auto Mode runs CoreDNS on every node, with no kube-dns pods
or Service; the address is left out without a cluster to look it up in).
Usage: {{- include "refcloud.computeEgressToDns" $addresses | nindent 4 }}
*/}}
{{- define "refcloud.computeEgressToDns" -}}
# DNS
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    {{- with .coreDns }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
  ports:
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 53
{{- end -}}

{{/*
The run storage: RustFS in the release namespace locally (spec 0012); on AWS, compute's private S3 endpoint at its
fixed addresses (ga4gh.referenceCloud.compute.aws.s3EndpointIps; spec 0013).
Usage: {{- include "refcloud.computeEgressToRunStorage" . | nindent 4 }}
*/}}
{{- define "refcloud.computeEgressToRunStorage" -}}
{{- if eq .Values.ga4gh.referenceCloud.cloudEnvironment "aws" }}
# the run storage: compute's private S3 endpoint
- to:
    {{- range .Values.ga4gh.referenceCloud.compute.aws.s3EndpointIps }}
    - ipBlock:
        cidr: {{ . }}/32
    {{- end }}
  ports:
    - protocol: TCP
      port: 443
{{- else }}
# the run storage: RustFS, in the release namespace
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
{{- end }}
{{- end -}}
