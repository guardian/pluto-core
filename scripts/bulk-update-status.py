import argparse
import sys
import os
import time
import requests
import json
import logging
import datetime
import jwt

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

BASE_URL="https://prexit.local"
UPDATE_URL = f"{BASE_URL}/pluto-core/api/project"
COMMISSION_LIST_URL = f"{BASE_URL}/pluto-core/api/pluto/commission/list"
PROJECT_LIST_URL = f"{BASE_URL}/pluto-core/api/project/list"

STATUS_STRINGS = ["New", "Held", "Completed", "Killed", "In Production", None]
ALLOWED_INPUT = ["1", "2", "3", "4", "5", "6", "7"]

MAX_RECORDS_PER_PAGE = 100

def setup_argparser() -> argparse.ArgumentParser:
    """Set up the argument parser for the script"""
    argparser = argparse.ArgumentParser(description='Bulk update status of records')
    argparser.add_argument('-t', '--timestamp', help='Date to filter records before (yyyy-mm-dd)')
    argparser.add_argument('-T', '--title', help='Title to filter records by')
    argparser.add_argument('-S', '--stop', help='Stop script after after n number of commisions are updated')
    return argparser

def get_token() -> str:
    """Set token from environment variable"""
    token = os.environ.get("PLUTO_TOKEN")
    if token == None:
        print("No token found. Exiting script...")
        sys.exit()
    decoded_token = jwt.decode(token, algorithms=[], options={"verify_signature": False})
    expiration_time = datetime.datetime.fromtimestamp(decoded_token["exp"])
    if expiration_time < datetime.datetime.now():
        print("Token has expired. Exiting script...")
        sys.exit()
    print(f"Token expires at: {expiration_time}\n")
    return token

def get_headers(token: str) -> dict:
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }

def setup_logging() -> None:
    logging.basicConfig(filename="data.log", level=logging.DEBUG)


def api_put_request(url, headers, json_body, max_retries=5):
    backoff_factor = 2
    for retry in range(max_retries):
        try:
            with requests.put(url, headers=headers, data=json_body, verify=False) as response:
                response.raise_for_status()
                return response.json()
        except (requests.exceptions.HTTPError, requests.exceptions.RequestException) as e:
            if retry == max_retries - 1:  # If this is the last retry, raise the exception.
                raise
            wait_time = backoff_factor ** retry
            print(f"An error occurred: {e}. Retrying in {wait_time} seconds...")
            time.sleep(wait_time)

def get_filtered_commission_records(timestamp, status, headers, title=None) -> list:
    request_body = {
        "match": "W_EXACT",
        "completionDateBefore": timestamp
    }
    if status:
        request_body["status"] = status
    if title:
        request_body["title"] = title

    json_body = json.dumps(request_body)
    records = []

    try:
        json_content = api_put_request(COMMISSION_LIST_URL, headers, json_body)
        total_records = json_content["count"]
        total_pages = (total_records + MAX_RECORDS_PER_PAGE - 1) // MAX_RECORDS_PER_PAGE
        start_at = 0

        for page in range(1, total_pages + 1):
            print(f"loading page: {page}")

            response = api_put_request(
                f"{COMMISSION_LIST_URL}?startAt={start_at}&length={MAX_RECORDS_PER_PAGE}",
                headers,
                json_body,
            )

            json_content = response
            logging.debug(f"page: {page}, records: {json_content['result']}")
            if status is None:
                records.extend([record for record in json_content["result"] if record["status"] not in ["Completed", "Killed"]])
            else:
                records.extend(json_content["result"])
            start_at += MAX_RECORDS_PER_PAGE

    except requests.exceptions.RequestException as e:
        print(e)
        raise Exception("An error occurred. Exiting script...")
    # write records to file
    with open(f"commissions_before{timestamp}.json", "w") as f:
        json.dump(records, f)
    return records

def get_projects(records, headers, timestamp) -> list:
    projects = []
    number_of_records = len(records)
    for record in records:
        commission_id = record['id']
        print(f"{number_of_records} commissions to go...")
        number_of_records -= 1

        print(f"Getting projects for commission ID: {commission_id}")
        try:
            json_content = api_put_request(
                PROJECT_LIST_URL,
                headers,
                json.dumps({"match": "W_EXACT", "commissionId": commission_id}),
            )

            for project in json_content["result"]:
                if project['status'] == "Completed" or project['status'] == "Killed":
                    print(f"Skipping project {project['id']} with status: {project['status']}")
                    continue
                print(f"Adding project with id: {project['id']} to list of projects to update")
                projects += [project]
                with open(f"projects_{timestamp}.json", "a") as f:
                    f.write(json.dumps(project))
        except requests.exceptions.RequestException as e:
            raise Exception(f"An error occurred. {e} Exiting script...")
    return projects


def update_project_status(headers, timestamp) -> None:
    #open projects file
    input = f"Open projects_{timestamp}.json? (y/n): "
    if input.lower() is "y":
        with open(f"projects_{timestamp}.json", "r") as f:
            projects = json.load(f)
    elif input.lower() is "n":
        input = "Enter path to projects file: "
        with open(input, "r") as f:
            projects = json.load(f)

    if projects:  
        display_projects(projects)
    else:
        print("No records to update")
        return
    print("Change status to: ")
    status = STATUS_STRINGS[get_input()]
    
    confirm = input(f"Do you want to update the status of these projects to \033[32m{status}\033[0m? (y/n): ")
    
    if confirm.lower != "y":
        print("Exiting script")
        return
    
    for project in projects:
        print(project)
        
        request_body = { "status": status }
        json_body = json.dumps(request_body)
        try:
            json_content = api_put_request(
                f"{UPDATE_URL}/{project['id']}/status",
                headers,
                json_body,
            )
            print(f"Updated record: {project['id']} to {status} {json_content['status']}")
            logging.info(f"record: {project['id']}, status: {json_content['status']}, project status updated to: {status}")
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

def main() -> None:
    args = setup_argparser().parse_args()
    token = get_token()
    headers = get_headers(token)
    setup_logging()

    # Set the timestamp to filter records by
    timestamp = args.timestamp or "2022-01-01"
    timestamp = f"{timestamp}T00:00:00Z"

    choice = input("(G)et or (U)pdate projects?\n")
    if choice.lower() == "g":
        print(f"Get projects with a completion date before {timestamp} that are:")
        status = get_input()
        filtered_records = get_filtered_commission_records(timestamp=timestamp, title=args.title, headers=headers, status=STATUS_STRINGS[status])
        projects = get_projects(filtered_records, headers, timestamp)
        display_projects(projects)
    elif choice.lower() == "u":
        update_project_status(headers, timestamp)

            
if __name__ == "__main__":
    logging.info(f"Starting script at {datetime.datetime.now()}")
    main()