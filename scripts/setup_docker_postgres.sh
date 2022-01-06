#!/usr/bin/env bash

docker network ls | grep projectlocker
if [ "$?" != "0" ]; then
    docker network create projectlocker
fi

docker ps | grep database
if [ "$?" == "0" ]; then
    docker rm database
fi

docker run --rm -p 5432:5432 --network projectlocker --name database -e POSTGRES_USER=postgres -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_PASSWORD=projectlocker -e POSTGRES_DB=projectlocker -v ${PWD}/docker-init:/docker-entrypoint-initdb.d postgres:14.1-alpine