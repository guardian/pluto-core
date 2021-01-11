FROM alpine:3.12

COPY --chown=daemon:root migrate-old-assetfolders /usr/local/bin
USER daemon
CMD /usr/local/bin/migrate-old-assetfolders