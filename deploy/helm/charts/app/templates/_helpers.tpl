{{/*
Full image reference for a refcloud app image, using the lockstep tag.
Usage: {{ include "refcloud.image" (dict "root" . "repository" "refcloud-core-api") }}
Omits the registry prefix when .Values.image.registry is empty (local minikube builds).
*/}}
{{- define "refcloud.image" -}}
{{- $image := .root.Values.image -}}
{{- $tag := required "image.tag is required: set the lockstep refcloud image tag, e.g. --set image.tag=$(git describe --tags --match 'v[0-9]*' --always)" $image.tag -}}
{{- if $image.registry -}}
{{- printf "%s/%s:%s" (trimSuffix "/" $image.registry) .repository $tag -}}
{{- else -}}
{{- printf "%s:%s" .repository $tag -}}
{{- end -}}
{{- end -}}

{{/*
Third-party Ory images. Pinned in the chart (not values) so each refcloud
release deploys a fixed Kratos/Hydra version.
Keep in sync with deploy/local/docker-compose.yml.
*/}}
{{- define "refcloud.kratosImage" -}}
oryd/kratos:v26.2.0
{{- end -}}

{{- define "refcloud.hydraImage" -}}
oryd/hydra:v26.2.0
{{- end -}}

{{/*
imagePullSecrets block for refcloud app pods. Renders nothing when
.Values.image.pullSecrets is empty.
*/}}
{{- define "refcloud.imagePullSecrets" -}}
{{- with .Values.image.pullSecrets -}}
imagePullSecrets:
{{- range . }}
  - name: {{ . }}
{{- end }}
{{- end }}
{{- end -}}

{{/*
Pod-level security context, shared by every pod. runAsUser is set explicitly because
runAsNonRoot can't verify images whose USER is a name (hydra, liquibase, researcher-ui);
it must match the image's user, which owns the image's files.
Usage: {{- include "refcloud.podSecurityContext" 10001 | nindent 6 }}
*/}}
{{- define "refcloud.podSecurityContext" -}}
securityContext:
  runAsNonRoot: true
  runAsUser: {{ . }}
  seccompProfile:
    type: RuntimeDefault
{{- end -}}

{{/*
Container-level security context, shared by every container. The root filesystem is
read-only, so each container mounts a writable emptyDir volume at /tmp,
plus any other path it writes to.
Usage: {{- include "refcloud.containerSecurityContext" . | nindent 10 }}
*/}}
{{- define "refcloud.containerSecurityContext" -}}
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
{{- end -}}

{{/*
The compute namespace: one per release, created by the chart (templates/common/compute-namespace.yaml), so several
installations can share a cluster. Funnel, its workers and the task pods run there. Its name is
ga4gh.referenceCloud.compute.namespace, or <release name>-compute: releases with the same name in one cluster (e.g.
staging and production in their own namespaces) set their own (spec 0013).
*/}}
{{- define "refcloud.computeNamespace" -}}
{{- .Values.ga4gh.referenceCloud.compute.namespace | default (printf "%s-compute" .Release.Name) -}}
{{- end -}}

{{/*
The SELinux level every compute pod shares: the Nextflow Jobs, Funnel's workers and executors. On nodes that enforce
SELinux (EKS Auto Mode's), each pod otherwise gets random categories, and a volume can be used only by the pod that
mounted it last: a task's worker and executors could not share its workspace claim (spec 0013, Task 5). Nodes without
SELinux (minikube's) ignore it.

ga4gh.referenceCloud.compute.seLinuxLevel, or derived from the compute namespace's name: two categories from its
checksums, so each environment gets its own level with nothing to configure. Tasks stay apart from each other because
each task's claim is mounted only into its own pods.
*/}}
{{- define "refcloud.computeSelinuxLevel" -}}
{{- $level := .Values.ga4gh.referenceCloud.compute.seLinuxLevel }}
{{- if $level }}
{{- if not (regexMatch "^s0(:c[0-9]{1,4}(,c[0-9]{1,4})*)?$" $level) }}
{{- fail (printf "ga4gh.referenceCloud.compute.seLinuxLevel must be an SELinux level such as s0:c123,c456, not %q" $level) }}
{{- end }}
{{- $level -}}
{{- else }}
{{- $namespace := include "refcloud.computeNamespace" . }}
{{- $first := mod (adler32sum $namespace | atoi) 1024 }}
{{- $second := mod (adler32sum (printf "%s/selinux" $namespace) | atoi) 1024 }}
{{- if eq $first $second }}
{{- $second = mod (add $second 1) 1024 }}
{{- end }}
{{- printf "s0:c%d,c%d" (min $first $second) (max $first $second) -}}
{{- end }}
{{- end -}}

{{/*
Where the compute pods run (the Nextflow Jobs, Funnel's workers and executors; not Funnel's server): the node selector
and tolerations from ga4gh.referenceCloud.compute, as YAML for a pod spec. Empty when they're empty: any node.
Usage: {{ include "refcloud.computePodPlacement" . | indent 6 }}
*/}}
{{- define "refcloud.computePodPlacement" -}}
{{- $compute := .Values.ga4gh.referenceCloud.compute }}
{{- with $compute.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with $compute.tolerations }}
tolerations:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end -}}

{{/*
The Funnel (TES executor) image, run by Funnel's server and its per-task workers. Pinned by version and by the digest
of its multi-architecture index. Keep the version in sync with deploy/local/funnel/Dockerfile (native mode). Funnel
has moved to github.com/calypr/funnel; its images are still published here.
*/}}
{{- define "refcloud.funnelImage" -}}
quay.io/ohsu-comp-bio/funnel:0.12.3@sha256:bfd615926d2bbc5aac76cd9640235f3e7fcd3badc95f58fbdff29368f86b6db4
{{- end -}}

{{/*
The disk (GB) a task that requests none is given: the size of its workspace claim (files/funnel/storage.yaml) and
Funnel's own default (templates/apps/funnel/config-map.yaml).
*/}}
{{- define "refcloud.defaultTaskDiskGb" -}}
1
{{- end -}}

{{/*
RustFS, the S3-compatible server that stands in for S3 locally (spec 0012): run storage for the local compute
connection. Pinned by version and by the digest of its multi-architecture index. Local only; production uses S3.
*/}}
{{- define "refcloud.rustfsImage" -}}
rustfs/rustfs:1.0.6@sha256:ee717072290bec9b11abfc0365f8310b00df1cd9b3639943edae47bdef05044a
{{- end -}}

{{/*
The local S3 bucket compute runs are kept in (runs/<run id>/ under it), on RustFS.
*/}}
{{- define "refcloud.localComputeBucket" -}}
refcloud-compute
{{- end -}}

{{/*
Service-info for core-api's GA4GH services, from ga4gh.referenceCloud.serviceInfo, which they all share (values.yaml):
DRS, refget, Data Connect, the service registry, and each compute platform's WES and TES. A service's id is
org.ga4gh.refcloud.<id>.<idSuffix>, its name "<name> (<environmentName>)", and its version the lockstep image tag.
Descriptions, types and timestamps stay each service's own (core-api's application.yml).
Usage: {{- include "refcloud.serviceInfoEnv" (dict "root" . "prefix" "GA4GH_REFCLOUD_DRS_SERVICEINFO" "id" "drs" "name" "GA4GH Reference Cloud DRS Service") | nindent 12 }}
*/}}
{{- define "refcloud.serviceInfoEnv" -}}
{{- $info := .root.Values.ga4gh.referenceCloud.serviceInfo -}}
- name: {{ .prefix }}_ID
  value: {{ printf "org.ga4gh.refcloud.%s.%s" .id (required "ga4gh.referenceCloud.serviceInfo.idSuffix is required" $info.idSuffix) | quote }}
- name: {{ .prefix }}_NAME
  value: {{ printf "%s (%s)" .name (required "ga4gh.referenceCloud.serviceInfo.environmentName is required" $info.environmentName) | quote }}
{{ include "refcloud.serviceInfoSharedEnv" . }}
{{- end -}}

{{/*
The service-info settings every service shares, for services whose ids and names are built elsewhere (compute's WES and
TES, from each platform). Usage: as refcloud.serviceInfoEnv, without "id" and "name"
*/}}
{{- define "refcloud.serviceInfoSharedEnv" -}}
{{- $info := .root.Values.ga4gh.referenceCloud.serviceInfo -}}
- name: {{ .prefix }}_ORGANIZATION_NAME
  value: {{ $info.organization.name | quote }}
- name: {{ .prefix }}_ORGANIZATION_URL
  value: {{ $info.organization.url | quote }}
- name: {{ .prefix }}_CONTACTURL
  value: {{ $info.contactUrl | quote }}
- name: {{ .prefix }}_DOCUMENTATIONURL
  value: {{ $info.documentationUrl | quote }}
- name: {{ .prefix }}_ENVIRONMENT
  value: {{ required "ga4gh.referenceCloud.serviceInfo.environment is required" $info.environment | quote }}
- name: {{ .prefix }}_VERSION
  value: {{ .root.Values.image.tag | quote }}
{{- end -}}
