#!/usr/bin/env bash

docker network ls | grep projectlocker
if [ "$?" != "0" ]; then
    docker network create projectlocker
fi

docker ps | grep rabbitmq
if [ "$?" == "0" ]; then
    docker rm rabbitmq
fi

docker volume ls | grep rabbitmq-mnesia
if [ "$?" != "0" ]; then
  docker volume create rabbitmq-mnesia
fi

#Port 5672 is AMQP messaging, port 15672 is http management interface
docker run --rm -p 5672:5672 -p 15672:15672 --network projectlocker --name rabbitmq -v rabbitmq-mnesia:/var/lib/rabbitmq/mnesia rabbitmq:3.8-management-alpine