import sys
import os
import time
import requests
import json
import logging
import datetime

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

# Set the timestamp to filter records by
TIMESTAMP = "2022-01-01T00:00:00Z"

# Set the title to filter records by
TITLE = None

# Set the URLs for the API
UPDATE_URL = "https://prexit.local/pluto-core/api/pluto/commission"
GET_URL = "https://prexit.local/pluto-core/api/pluto/commission/list"

STATUS_STRINGS = ["New", "Held", "Completed", "Killed", "In Production"]
ALLOWED_INPUT = ["1", "2", "3", "4", "5", "6"]

MAX_RECORDS_PER_PAGE = 100

# get token from environment variable
token = os.environ.get("PLUTO_TOKEN")

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {token}",
}

# Set up logging to write to a file
logging.basicConfig(filename="data.log", level=logging.DEBUG)

def get_filtered_records(timestamp, status, title=None) -> list:
    request_body = {
        "match": "W_EXACT",
        "completionDateBefore": timestamp,
        "status": status,
    }
    if title:
        request_body["title"] = title

    json_body = json.dumps(request_body)
    records = []
    max_retries = 5
    backoff_time = 1

    try:
        for _ in range(max_retries):
            response = requests.put(GET_URL, headers=headers, data=json_body, verify=False)
            if response.status_code in [502, 503]:
                print(f"Received {response.status_code}. Retrying in {backoff_time} seconds...")
                time.sleep(backoff_time)
                backoff_time *= 2
                continue

            response.raise_for_status()  # Raise an HTTPError if status is not 2xx
            json_content = response.json()
            total_records = json_content["count"]
            total_pages = (total_records + MAX_RECORDS_PER_PAGE - 1) // MAX_RECORDS_PER_PAGE
            start_at = 0
            break
        else:
            raise Exception("Maximum retries reached. Exiting script...")

        for page in range(1, total_pages + 1):
            print(f"loading page: {page}")

            for i in range(max_retries):
                response = requests.put(
                    f"{GET_URL}?startAt={start_at}&length={MAX_RECORDS_PER_PAGE}",
                    data=json_body,
                    headers=headers,
                    verify=False,
                )
                if response.status_code in [502, 503]:
                    print(f"Received {response.status_code}. Retrying in {backoff_time} seconds...")
                    time.sleep(backoff_time)
                    backoff_time *= 2
                    continue

                response.raise_for_status()  # Raise an HTTPError if status is not 2xx
                break
            else:
                raise Exception("Maximum retries reached. Exiting script...")

            json_content = response.json()
            logging.debug(f"page: {page}, records: {json_content['result']}")
            records.extend(json_content["result"])
            start_at += MAX_RECORDS_PER_PAGE

    except requests.exceptions.RequestException as e:
        print(e)
        raise Exception("An error occurred. Exiting script...")

    return records

def update_status(records) -> None:
    #display records to be updated
    if records:    
        display_records(records)
    else:
        print("No records to update")
        return
    print("Change status to: ")
    status = STATUS_STRINGS[get_input()]
    
    confirm = input(f"Do you want to update the status of these records to \033[32m{status}\033[0m? (y/n): ")
    
    if confirm != "y":
        print("Exiting script")
        return
    for record in records:
        record_id = record['id']
        request_body = { "status": status }
        json_body = json.dumps(request_body)
        try:
            response = requests.put(f"{UPDATE_URL}/{record_id}/status", headers=headers, data=json_body, verify=False)
            response.raise_for_status()  # Raise an HTTPError if status is not 2xx
            json_content = response.json()
            print(f"Updated record: {record_id} to {status} {json_content['status']}")
            logging.debug(f"record: {record_id}, status: {json_content['status']}, commission status updated to: {status}")
        except requests.exceptions.RequestException as e:
            print(e)

def display_records(records) -> None:
    print("\n")
    for record in records:
        print(f"id: {record['id']:<5} title: {record['title']:<65} status: {record['status']:<10} scheduledCompletion: {record['scheduledCompletion']}")
    print(f"\nTotal records: {len(records)}\n")

def get_input() -> int:
        status_int = input("\n1: New\n2: Held\n3: Completed\n4: Killed\n5: In Production\n6: Exit script\n")
        if status_int not in ALLOWED_INPUT:
            print("Invalid input. Exiting script")
            sys.exit()       
        elif status_int == "6":
            print("Exiting script")
            sys.exit()
        return int(status_int) - 1
            
if __name__ == "__main__":
    logging.info(f"Starting script at {datetime.datetime.now()}")
    print(f"Update status of records with completion date before {TIMESTAMP} that are:")
    filtered_records = get_filtered_records(timestamp=TIMESTAMP, title=TITLE, status=STATUS_STRINGS[get_input()])
    update_status(filtered_records)
