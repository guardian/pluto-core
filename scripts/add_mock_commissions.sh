#!/usr/bin/bash

# Create 250 commissions

export LC_TIME=C
DATE_FORMAT='%Y-%m-%dT%H:%M:%S.%3N%:z'
future_date=$(date -d "+30 days" +"${DATE_FORMAT}")
N_WORKING_GROUPS=12
N_COMMISSIONS=${1:-250}

for id in $(seq -w 1 $N_COMMISSIONS)
do
  now="$(date +${DATE_FORMAT})"
  curl localhost:9000/api/prexit/commission \
    -v \
    -H 'Content-Type: application/json;charset=utf-8' \
    --data "$(cat <<EOF
{
  "title": "Commission #${id}",
  "status": "New",
  "workingGroupId": $((RANDOM % N_WORKING_GROUPS)),
  "created": "${now}",
  "updated": "${now}",
  "productionOffice": "UK",
  "scheduledCompletion": "${future_date}",
  "owner": "noldap"
}
EOF
)"
done
