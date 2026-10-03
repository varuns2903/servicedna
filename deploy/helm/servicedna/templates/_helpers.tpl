{{- define "servicedna.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/* A component's name: servicedna-backend, servicedna-postgresql, … */}}
{{- define "servicedna.name" -}}
{{- printf "%s-%s" (include "servicedna.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "servicedna.labels" -}}
app.kubernetes.io/name: {{ .root.Chart.Name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end -}}

{{- define "servicedna.selectorLabels" -}}
app.kubernetes.io/name: {{ .root.Chart.Name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "servicedna.image" -}}
{{- $repo := .image.repository -}}
{{- with .root.Values.image.registry }}{{ $repo = printf "%s/%s" (trimSuffix "/" .) $repo }}{{ end -}}
{{- printf "%s:%s" $repo (.image.tag | default .root.Chart.AppVersion) -}}
{{- end -}}

{{- define "servicedna.secretName" -}}
{{- .Values.secrets.existingSecret | default (printf "%s-secrets" (include "servicedna.fullname" .)) -}}
{{- end -}}

{{- define "servicedna.url" -}}
{{- required "url is required: ServiceDNA's address as browsers reach it, e.g. https://servicedna.example.com" .Values.url | trimSuffix "/" -}}
{{- end -}}

{{/* Pod-level settings shared by every component. */}}
{{- define "servicedna.podSpec" -}}
automountServiceAccountToken: false
# Kubernetes' service-link variables (KAFKA_PORT=tcp://…) would override real settings.
enableServiceLinks: false
{{- with .root.Values.imagePullSecrets }}
imagePullSecrets:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with .root.Values.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with .root.Values.tolerations }}
tolerations:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with .root.Values.affinity }}
affinity:
  {{- toYaml . | nindent 2 }}
{{- end }}
securityContext:
  runAsNonRoot: true
  runAsUser: {{ .uid }}
  runAsGroup: {{ .uid }}
  fsGroup: {{ .uid }}
  seccompProfile: {type: RuntimeDefault}
{{- end -}}

{{- define "servicedna.containerSecurity" -}}
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: {{ .readOnly | default false }}
  capabilities: {drop: [ALL]}
{{- end -}}

{{/* Where the backend finds each datastore: the bundled one, or the external one given. */}}
{{- define "servicedna.postgresHost" -}}
{{- if .Values.postgresql.enabled }}{{ include "servicedna.name" (dict "root" . "component" "postgresql") }}{{ else }}{{ required "postgresql.host is required when postgresql.enabled is false" .Values.postgresql.host }}{{ end -}}
{{- end -}}
