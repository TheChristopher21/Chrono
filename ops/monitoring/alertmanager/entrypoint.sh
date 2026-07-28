#!/bin/sh
set -eu

: "${ALERT_WEBHOOK_URL:?ALERT_WEBHOOK_URL is required}"
case "${ALERT_WEBHOOK_URL}" in
  https://*) ;;
  *)
    echo "ALERT_WEBHOOK_URL must use HTTPS." >&2
    exit 1
    ;;
esac
case "${ALERT_WEBHOOK_URL}" in
  *"'"*)
    echo "ALERT_WEBHOOK_URL contains unsupported characters." >&2
    exit 1
    ;;
esac

cat > /tmp/alertmanager.yml <<EOF
route:
  receiver: chrono-operations
  group_by: [alertname]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
receivers:
  - name: chrono-operations
    webhook_configs:
      - url: '${ALERT_WEBHOOK_URL}'
        send_resolved: true
EOF

exec /bin/alertmanager \
  --config.file=/tmp/alertmanager.yml \
  --storage.path=/tmp/alertmanager-data \
  --web.listen-address=:9093
