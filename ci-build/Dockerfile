FROM centos:7

COPY ./containerscripts/javasbtdockergit.sh /tmp
RUN bash /tmp/javasbtdockergit.sh

COPY ./containerscripts/jython.sh /tmp
RUN bash /tmp/jython.sh

COPY ./containerscripts/sbt-repositories /root/.sbt