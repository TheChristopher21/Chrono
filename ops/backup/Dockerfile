ARG MYSQL_IMAGE_TAG=8.0.42
FROM mysql:${MYSQL_IMAGE_TAG}

COPY backup.sh check-backup.sh restore-drill.sh /opt/chrono/
RUN chmod 0555 /opt/chrono/backup.sh /opt/chrono/check-backup.sh /opt/chrono/restore-drill.sh

ENTRYPOINT ["/opt/chrono/backup.sh"]
