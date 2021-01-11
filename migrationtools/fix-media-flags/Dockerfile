FROM alpine:3.12

COPY --chown=daemon:root fix-media-flags /usr/local/bin/fix-media-flags
USER daemon
CMD /usr/local/bin/fix-media-flags