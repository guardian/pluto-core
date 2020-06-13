#!/bin/bash -e

apt-get -y update
apt-get -y install curl gnupg
echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
apt-key add /tmp/sbt-deb-key.txt
apt-get -y update
apt-get -y install sbt git
apt-get -y clean && rm -rf /var/cache/apt

echo exit | sbt

curl -L https://download.docker.com/linux/static/stable/x86_64/docker-18.06.1-ce.tgz > /tmp/docker-18.06.1-ce.tgz
tar xvzf /tmp/docker-18.06.1-ce.tgz
mv docker/docker /usr/bin
rm -rf docker