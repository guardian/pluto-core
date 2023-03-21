import argparse
import sys
import os
import time
import requests
import json
import logging
import datetime


argparser = argparse.ArgumentParser(description='Bulk update status of records')
argparser.add_argument('-t', '--timestamp', help='Date to filter records before (yyyy-mm-dd)')
argparser.add_argument('-T', '--title', help='Title to filter records by')
args = argparser.parse_args()
print(args.timestamp)

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

# Set the timestamp to filter records by
if not args.timestamp:
    TIMESTAMP = "2022-01-01T00:00:00Z"
else:
    TIMESTAMP = f"{args.timestamp}T00:00:00Z"

# Set the URLs for the API
BASE_URL="https://prexit.local"
UPDATE_URL = f"{BASE_URL}/pluto-core/api/project"
COMMISSION_LIST_URL = f"{BASE_URL}/pluto-core/api/pluto/commission/list"

STATUS_STRINGS = ["New", "Held", "Completed", "Killed", "In Production", None]
ALLOWED_INPUT = ["1", "2", "3", "4", "5", "6", "7"]

MAX_RECORDS_PER_PAGE = 100

# get token from environment variable
token = os.environ.get("PLUTO_TOKEN")
if token == None:
    print("No token found. Exiting script...")
    sys.exit()


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
            response = requests.put(COMMISSION_LIST_URL, headers=headers, data=json_body, verify=False)
            if response.status_code in [408, 502, 503, 504]:
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

            for _ in range(max_retries):
                response = requests.put(
                    f"{COMMISSION_LIST_URL}?startAt={start_at}&length={MAX_RECORDS_PER_PAGE}",
                    data=json_body,
                    headers=headers,
                    verify=False,
                )
                if response.status_code in [408, 502, 503, 504]:
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
            if status == None:
                records.extend([record for record in json_content["result"] if record["status"] not in ["Completed", "Killed"]])
            else:
                records.extend(json_content["result"])
            start_at += MAX_RECORDS_PER_PAGE

    except requests.exceptions.RequestException as e:
        print(e)
        raise Exception("An error occurred. Exiting script...")
    # write records to file
    with open(f"commissions_before{TIMESTAMP}.json", "w") as f:
        json.dump(records, f)
    return records

def get_projects(records) -> list:
    projects = []
    number_of_records = len(records)
    for record in records:
        commission_id = record['id']
        print(f"{number_of_records} commissions to go...")
        number_of_records -= 1
        try:
            print(f"Getting projects for commission ID: {commission_id}")
            response = requests.put("https://prexit.local/pluto-core/api/project/list", headers=headers, data=json.dumps({"match": "W_EXACT", "commissionId": commission_id}), verify=False)
            response.raise_for_status()  # Raise an HTTPError if status is not 2xx
            json_content = response.json()
            for project in json_content["result"]:
                if project['status'] == "Completed" or project['status'] == "Killed":
                    print(f"Skipping project {project['id']}with status: {project['status']}")
                    continue
                print(f"Adding project with id: {project['id']} to list of projects to update")
                projects += [project]
        except requests.exceptions.RequestException as e:
            raise Exception(f"An error occurred. {e} Exiting script...")
    return projects


def update_project_status() -> None:
    #open projects file
    with open(f"projects_before_{TIMESTAMP}.json", "r") as f:
        projects = json.load(f)

    if projects:  
        display_projects(projects)
    else:
        print("No records to update")
        return
    print("Change status to: ")
    status = STATUS_STRINGS[get_input()]
    
    confirm = input(f"Do you want to update the status of these projects to \033[32m{status}\033[0m? (y/n): ")
    
    if confirm != "y":
        print("Exiting script")
        return
    
    for project in projects:
        print(project)
        
        request_body = { "status": status }
        json_body = json.dumps(request_body)
        try:
            response = requests.put(f"{UPDATE_URL}/{project['id']}/status", headers=headers, data=json_body, verify=False)
            response.raise_for_status()  # Raise an HTTPError if status is not 2xx
            json_content = response.json()
            print(f"Updated record: {project['id']} to {status} {json_content['status']}")
            logging.debug(f"record: {project['id']}, status: {json_content['status']}, project status updated to: {status}")
        except requests.exceptions.RequestException as e:
            raise Exception(f"An error occurred. {e} Exiting script...")

def display_projects(projects) -> None:
    print("\n")
    project_count = 0
    for project in projects:
        project_count += 1
        print(f"projectId: {project['id']:<5} commissionId: {project['commissionId']:<7} title: {project['title']:<65} user: {project['user']:<20} status: {project['status']:<10}")
        
    print(f"\nTotal projects:    {project_count}\n")

def get_input() -> int:
        status_int = input("\n1: New\n2: Held\n3: Completed\n4: Killed\n5: In Production\n6: All\n7: Exit script\n")
        if status_int not in ALLOWED_INPUT:
            print("Invalid input. Exiting script")
            sys.exit()       
        elif status_int == "7":
            print("Exiting script")
            sys.exit()
        return int(status_int) - 1
            
if __name__ == "__main__":
    logging.info(f"Starting script at {datetime.datetime.now()}")
    print(f"Update status of records with completion date before {TIMESTAMP} that are:")
    filtered_records = get_filtered_records(timestamp=TIMESTAMP, title=args.title, status=STATUS_STRINGS[get_input()])
    update_status(filtered_records)
