FROM openjdk:11-jre-slim-buster

COPY ./containerscripts/sbt-deb-key.txt /tmp
COPY ./containerscripts/sbt-install.sh /tmp
RUN bash /tmp/sbt-install.sh

COPY ./containerscripts/jython.sh /tmp
RUN bash /tmp/jython.sh

COPY ./containerscripts/sbt-repositories /root/.sbt