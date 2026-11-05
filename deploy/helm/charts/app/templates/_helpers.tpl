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
oryd/kratos:v0.9.0-alpha.2
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
