#!/bin/bash -e

curl -L https://repo1.maven.org/maven2/org/python/jython-installer/2.7.2/jython-installer-2.7.2.jar > /tmp/jython-installer-2.7.2.jar
java -jar /tmp/jython-installer-2.7.2.jar -s -d ~/jython -t standard -e doc
rm -f /tmp/jython-installer-2.7.2.jar