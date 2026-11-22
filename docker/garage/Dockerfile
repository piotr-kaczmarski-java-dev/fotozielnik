# Garage S3-compatible object storage (ADR-0007), single node, self-configuring.
# The official image is a bare static binary without a shell, so it cannot bootstrap itself; this image adds
# a shell entrypoint that writes the config from env vars and, on first start, creates the single-node
# layout, imports the app's access key and creates the bucket. Used by compose.yml, deploy/ and Testcontainers.
FROM dxflrs/garage:v2.2.0 AS garage

FROM alpine:3.22
COPY --from=garage /garage /usr/local/bin/garage
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
VOLUME /var/lib/garage
EXPOSE 3900
HEALTHCHECK --interval=2s --timeout=3s --retries=30 CMD test -f /tmp/garage-ready
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
