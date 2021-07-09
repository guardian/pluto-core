#!/usr/bin/env python

import pika
import os
from time import sleep
import logging

pikaLogger = logging.getLogger("pika")
pikaLogger.setLevel(logging.WARNING)
logging.basicConfig(level=logging.INFO, format="%(asctime)-15s %(name)s|%(funcName)s [%(levelname)s] %(message)s")
# Tick times in seconds
FAST_TICK   = 30
MEDIUM_TICK = 300       # 5min
SLOW_TICK   = 900       # 15min
VERY_SLOW_TICK = 3600   # 60min

# Messages to send when each tick is ready
TRIGGER_MAP = {
    "fast": [
        "pluto.core.service.storagescan"
    ],
    "slow": [
        "pluto.core.service.backuptrigger"
    ],
    "medium": [
        "pluto.core.service.commissionstatuspropagator",
    ],
    "veryslow": [
        "pluto.core.service.postrunaction"
    ]
}


def send(routing_key: str):
    """
    sends a "perform action" message with the given routing key
    :param routing_key: routing key to send. Consult PeriodicScanReceiver.scala in app/services to find valid values for this.
    :return: result of the basic_publish operation. Throws an exception on error
    """
    message_string = """{"action":"PerformAction"}"""
    logging.info("Requesting {} action...".format(routing_key))
    return channel.basic_publish(target_exchange, routing_key, message_string.encode("utf-8"))


# START MAIN
target_exchange = os.getenv("EXCHANGE", "pluto-core")
vhost = os.getenv("VHOST", "/")
user = os.getenv("USERNAME", "guest")
passwd = os.getenv("PASSWORD", "guest")
rmq_host = os.getenv("RABBITMQ", "localhost")

logging.info("Starting up, contacting {} with virtual host {} as {}".format(rmq_host, vhost, user))
connection = pika.BlockingConnection(pika.ConnectionParameters(host=rmq_host,
                                                               virtual_host=vhost,
                                                               credentials=pika.PlainCredentials(user, passwd))
                                     )
channel = connection.channel()
logging.info("Connection established. Ticks are at {} seconds".format([FAST_TICK, SLOW_TICK, VERY_SLOW_TICK]))

# we deliberately don't declare the exchange, wait for the main pluto-core to do this.
# this does mean that at first startup we will crashloop until pluto-core has initialised the exchange

slow_tick_counter = 0
med_tick_counter = 0
very_slow_tick_counter = 0

while True:
    sleep(FAST_TICK)
    logging.debug("Triggering {} actions for FAST tick after {} seconds".format(len(TRIGGER_MAP["fast"]), FAST_TICK))
    for action in TRIGGER_MAP["fast"]:
        send(action)

    slow_tick_counter += FAST_TICK
    if slow_tick_counter >= SLOW_TICK:
        logging.debug("Triggering {} actions for SLOW tick after {} seconds".format(len(TRIGGER_MAP["slow"]), slow_tick_counter))
        for action in TRIGGER_MAP["slow"]:
            send(action)
        slow_tick_counter = 0

    med_tick_counter += FAST_TICK
    if med_tick_counter >= MEDIUM_TICK:
        logging.debug("Triggering {} actions for MEDIUM tick after {} seconds".format(len(TRIGGER_MAP["medium"]), med_tick_counter))
        for action in TRIGGER_MAP["medium"]:
            send(action)
        med_tick_counter = 0

    very_slow_tick_counter += FAST_TICK
    if very_slow_tick_counter >= VERY_SLOW_TICK:
        logging.debug("Triggering {} actions for VERYSLOW tick after {} seconds".format(len(TRIGGER_MAP["veryslow"]), very_slow_tick_counter))
        for action in TRIGGER_MAP["veryslow"]:
            send(action)
        very_slow_tick_counter = 0