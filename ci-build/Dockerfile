FROM openjdk:8u292-jdk-slim-buster

COPY ./containerscripts/sbt-deb-key.txt /tmp
COPY ./containerscripts/sbt-install.sh /tmp
RUN bash /tmp/sbt-install.sh

COPY ./containerscripts/sbt-repositories /root/.sbt