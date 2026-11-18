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
Compute run storage on the minikube node (local only): a hostPath with one directory per release, mounted at the
same path in every pod that uses it, since run directories are passed between them by path.
scripts/refcloud-run-helm.sh creates it, owned by the compute user (10001): a directory Kubernetes created would be
root's. Keep in sync with MINIKUBE_COMPUTE_STORAGE_ROOT in scripts/lib/common.sh.
*/}}
{{- define "refcloud.computeStorageRoot" -}}
/data/refcloud-compute/{{ .Release.Name }}
{{- end -}}

{{/*
The compute namespace: one per release, created by the chart (templates/common/compute-namespace.yaml), so several
installations can share a cluster. Funnel, its workers and the task pods run there.
*/}}
{{- define "refcloud.computeNamespace" -}}
{{ .Release.Name }}-compute
{{- end -}}

{{/*
The Funnel (TES executor) image, run by Funnel's server and its per-task workers. Pinned by version and by the digest
of its multi-architecture index. Keep the version in sync with deploy/local/funnel/Dockerfile (native mode).
*/}}
{{- define "refcloud.funnelImage" -}}
quay.io/ohsu-comp-bio/funnel:0.11.14@sha256:8e10ba4de436b058bed95427110d9fe947142162078274a6a91cfe3b3eb8d15a
{{- end -}}
