#!/usr/bin/bash

# Adds 12 user groups to the system.

for i in {01..12};
do
  curl localhost:9000/api/pluto/workinggroup \
    -v \
    -H content-type:application/json \
    --data "{\"name\": \"Working Group ${i}\", \"uuid\":\"$(uuidgen)\"}"
done
