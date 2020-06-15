#!/bin/bash -e

yum -y clean all
yum -y install java-11-openjdk-headless postgresql git perl-Digest-SHA
curl -L https://dl.bintray.com/sbt/rpm/sbt-1.3.10.rpm > /tmp/sbt-1.3.10.rpm
rpm -Uvh /tmp/sbt-1.3.10.rpm
rm -f /tmp/sbt-1.3.10.rpm

curl -L https://download.docker.com/linux/static/stable/x86_64/docker-18.06.1-ce.tgz > /tmp/docker-18.06.1-ce.tgz
tar xvzf /tmp/docker-18.06.1-ce.tgz
mv docker/docker /usr/bin
rm -rf docker

yum -y clean all
rm -rf /var/cache/yum/*
echo exit | sbt

if [ ! -d ~/.sbt ]; then mkdir ~/.sbt; fi