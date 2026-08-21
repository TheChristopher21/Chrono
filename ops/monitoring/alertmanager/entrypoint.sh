#!/bin/sh
set -eu

: "${ALERT_EMAIL_TO:?ALERT_EMAIL_TO is required}"
: "${SPRING_MAIL_HOST:?SPRING_MAIL_HOST is required}"
: "${SPRING_MAIL_PORT:=587}"
: "${SPRING_MAIL_USERNAME:?SPRING_MAIL_USERNAME is required}"
: "${SPRING_MAIL_PASSWORD:?SPRING_MAIL_PASSWORD is required}"
: "${SPRING_MAIL_SMTP_STARTTLS_ENABLE:=true}"

case "${SPRING_MAIL_PORT}" in
  *[!0-9]*|"")
    echo "SPRING_MAIL_PORT must be numeric." >&2
    exit 1
    ;;
esac

for value in \
  "${ALERT_EMAIL_TO}" \
  "${SPRING_MAIL_HOST}" \
  "${SPRING_MAIL_USERNAME}" \
  "${SPRING_MAIL_PASSWORD}"
do
  case "${value}" in
    *"'"*)
      echo "Alert e-mail configuration contains unsupported characters." >&2
      exit 1
      ;;
  esac
done

cat > /tmp/alertmanager.yml <<EOF
global:
  smtp_smarthost: '${SPRING_MAIL_HOST}:${SPRING_MAIL_PORT}'
  smtp_from: '${SPRING_MAIL_USERNAME}'
  smtp_auth_username: '${SPRING_MAIL_USERNAME}'
  smtp_auth_password: '${SPRING_MAIL_PASSWORD}'
  smtp_require_tls: ${SPRING_MAIL_SMTP_STARTTLS_ENABLE}

route:
  receiver: chrono-operations
  group_by: [alertname]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
receivers:
  - name: chrono-operations
    email_configs:
      - to: '${ALERT_EMAIL_TO}'
        send_resolved: true
EOF

exec /bin/alertmanager \
  --config.file=/tmp/alertmanager.yml \
  --storage.path=/tmp/alertmanager-data \
  --web.listen-address=:9093
