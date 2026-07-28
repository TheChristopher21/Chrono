ARG ALERTMANAGER_IMAGE_TAG=v0.28.1
FROM prom/alertmanager:${ALERTMANAGER_IMAGE_TAG} AS upstream

FROM alpine:3.22
RUN apk add --no-cache ca-certificates
COPY --from=upstream /bin/alertmanager /bin/alertmanager
COPY entrypoint.sh /opt/chrono/entrypoint.sh
RUN chmod 0555 /opt/chrono/entrypoint.sh

USER 65534:65534
EXPOSE 9093
ENTRYPOINT ["/opt/chrono/entrypoint.sh"]
