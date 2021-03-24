FROM python:3.8-alpine

COPY periodic_trigger.py /usr/local/bin/periodic_trigger.py
COPY requirements.txt /tmp
RUN pip install -r /tmp/requirements.txt
USER nobody
CMD /usr/local/bin/periodic_trigger.py