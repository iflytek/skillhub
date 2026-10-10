{{- /*
SkillHub Helm Chart 模板辅助函数
*/}}

{{- /* 名称 */}}
{{- define "skillhub.name" -}}
{{- default "skillhub" .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- /* 完整名称 */}}
{{- define "skillhub.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default "skillhub" .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- /* Chart 标签 */}}
{{- define "skillhub.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- /* 通用标签 */}}
{{- define "skillhub.labels" -}}
helm.sh/chart: {{ include "skillhub.chart" . }}
{{ include "skillhub.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: skillhub
{{- end }}

{{- /* 选择器标签 */}}
{{- define "skillhub.selectorLabels" -}}
app.kubernetes.io/name: {{ include "skillhub.name" . }}
{{- end }}

{{- /* 组件标签 */}}
{{- define "skillhub.server.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: server
{{- end }}
{{- define "skillhub.server.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: server
{{- end }}

{{- define "skillhub.web.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: web
{{- end }}
{{- define "skillhub.web.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: web
{{- end }}

{{- define "skillhub.scanner.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: scanner
{{- end }}
{{- define "skillhub.scanner.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: scanner
{{- end }}

{{- /* Bitnami PostgreSQL subchart 完整名称 */}}
{{- define "skillhub.postgresql.fullname" -}}
{{- if .Values.postgresql.fullnameOverride -}}
{{- .Values.postgresql.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default "postgresql" .Values.postgresql.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /* Bitnami Redis subchart 完整名称 */}}
{{- define "skillhub.redis.fullname" -}}
{{- if .Values.redis.fullnameOverride -}}
{{- .Values.redis.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default "redis" .Values.redis.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL Host */}}
{{- define "skillhub.postgresql.host" -}}
{{- if .Values.postgresql.enabled -}}
{{- $prefix := include "skillhub.postgresql.fullname" . -}}
{{- if eq .Values.postgresql.architecture "replication" -}}
{{- printf "%s-primary" $prefix -}}
{{- else -}}
{{- $prefix -}}
{{- end -}}
{{- else -}}
{{- .Values.externalDatabase.host -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL Port */}}
{{- define "skillhub.postgresql.port" -}}
{{- if .Values.postgresql.enabled -}}
{{- print "5432" -}}
{{- else -}}
{{- .Values.externalDatabase.port | default 5432 | int -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL Database */}}
{{- define "skillhub.postgresql.database" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.database -}}
{{- else -}}
{{- .Values.externalDatabase.database -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL Username */}}
{{- define "skillhub.postgresql.username" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.username -}}
{{- else -}}
{{- .Values.externalDatabase.username -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL Secret Name */}}
{{- define "skillhub.postgresql.secretName" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.existingSecret | default (include "skillhub.postgresql.fullname" .) -}}
{{- else -}}
{{- include "skillhub.secretName" . -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL 密码 Secret key；postgres 使用管理员密码，其他用户使用应用密码 */}}
{{- define "skillhub.postgresql.passwordKey" -}}
{{- if eq .Values.postgresql.auth.username "postgres" -}}
{{- .Values.postgresql.auth.secretKeys.adminPasswordKey | default "postgres-password" -}}
{{- else -}}
{{- .Values.postgresql.auth.secretKeys.userPasswordKey | default "password" -}}
{{- end -}}
{{- end }}

{{- /* PostgreSQL JDBC URL */}}
{{- define "skillhub.jdbcUrl" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (include "skillhub.postgresql.host" .) .Values.postgresql.auth.database -}}
{{- else -}}
{{- if .Values.externalDatabase.jdbcUrl -}}
{{- .Values.externalDatabase.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%d/%s" .Values.externalDatabase.host (.Values.externalDatabase.port | default 5432 | int) .Values.externalDatabase.database -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /* Redis Sentinel 节点列表（Redisson 需要具体 pod FQDN，格式: {pod}.{headless-svc}.{ns}.svc.cluster.local） */}}
{{- define "skillhub.redis.sentinel.nodes" -}}
{{- $fullname := include "skillhub.redis.fullname" . -}}
{{- $prefix := printf "%s-node" $fullname -}}
{{- $headless := printf "%s-headless" $fullname -}}
{{- /* Headless Service DNS resolves directly to pod IPs, so use the container port. */ -}}
{{- $port := .Values.redis.sentinel.containerPorts.sentinel | default 26379 -}}
{{- $replicas := .Values.redis.replica.replicaCount | default 3 | int -}}
{{- $nodes := list -}}{{- range $i := until $replicas -}}{{- $nodes = append $nodes (printf "%s-%d.%s.%s.svc.cluster.local:%v" $prefix $i $headless $.Release.Namespace $port) -}}{{- end -}}{{- join "," $nodes -}}
{{- end }}

{{- /* Redis Host */}}
{{- define "skillhub.redis.host" -}}
{{- if .Values.redis.enabled -}}
{{- if .Values.redis.sentinel.enabled -}}
{{- include "skillhub.redis.fullname" . -}}
{{- else -}}
{{- printf "%s-master" (include "skillhub.redis.fullname" .) -}}
{{- end -}}
{{- else -}}
{{- if .Values.externalRedis.cluster.enabled -}}
{{- $node := first .Values.externalRedis.cluster.nodes -}}
{{- first (splitList ":" $node) -}}
{{- else -}}
{{- .Values.externalRedis.host -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /* Redis Port */}}
{{- define "skillhub.redis.port" -}}
{{- if .Values.redis.enabled -}}
{{- if .Values.redis.sentinel.enabled -}}
{{- .Values.redis.sentinel.service.ports.sentinel | default 26379 -}}
{{- else -}}
{{- print "6379" -}}
{{- end -}}
{{- else -}}
{{- if .Values.externalRedis.cluster.enabled -}}
{{- $node := first .Values.externalRedis.cluster.nodes -}}
{{- last (splitList ":" $node) -}}
{{- else if .Values.externalRedis.sentinel.enabled -}}
{{- $node := first .Values.externalRedis.sentinel.nodes -}}
{{- last (splitList ":" $node) -}}
{{- else -}}
{{- .Values.externalRedis.port | default 6379 | int -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /* Redis Password Secret Name */}}
{{- define "skillhub.redis.secretName" -}}
{{- if .Values.redis.enabled -}}
{{- .Values.redis.auth.existingSecret | default (include "skillhub.redis.fullname" .) -}}
{{- else -}}
{{- include "skillhub.secretName" . -}}
{{- end -}}
{{- end }}

{{- /* Redis 密码 Secret key */}}
{{- define "skillhub.redis.passwordKey" -}}
{{- .Values.redis.auth.existingSecretPasswordKey | default "redis-password" -}}
{{- end }}

{{- /* Secret 名称 */}}
{{- define "skillhub.secretName" -}}
{{- .Values.existingSecret | default (printf "%s-secret" (include "skillhub.fullname" .)) }}
{{- end }}

{{- /* PostgreSQL Service 名称（用于 server initContainer 等待） */}}
{{- define "skillhub.postgresql.serviceName" -}}
{{- if .Values.postgresql.enabled -}}
{{- include "skillhub.postgresql.host" . -}}
{{- else -}}
{{- .Values.externalDatabase.host -}}
{{- end -}}
{{- end }}

{{- /* Redis Service 名称（用于 server initContainer 等待） */}}
{{- define "skillhub.redis.serviceName" -}}
{{- if .Values.redis.enabled -}}
{{- include "skillhub.redis.host" . -}}
{{- else -}}
{{- if .Values.externalRedis.cluster.enabled -}}
{{- $node := first .Values.externalRedis.cluster.nodes -}}
{{- first (splitList ":" $node) -}}
{{- else if .Values.externalRedis.sentinel.enabled -}}
{{- $node := first .Values.externalRedis.sentinel.nodes -}}
{{- first (splitList ":" $node) -}}
{{- else -}}
{{- .Values.externalRedis.host -}}
{{- end -}}
{{- end -}}
{{- end }}
