import argparse
import sys
import os
import time
import requests
import json
import logging
import datetime
import jwt
from datetime import datetime, timezone

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

STATUS_STRINGS = ["New", "Held", "Completed", "Killed", "In Production", None]
ALLOWED_INPUT = ["1", "2", "3", "4", "5", "6", "7"]

MAX_RECORDS_PER_PAGE = 100

def setup_argparser() -> argparse.ArgumentParser:
    """Set up the argument parser for the script"""
    argparser = argparse.ArgumentParser(description='Bulk update status of records')
    argparser.add_argument('-b', '--baseurl', help='Base URL of the environment to run the script against')
    argparser.add_argument('-t', '--timestamp', help='Date to filter records before (yyyy-mm-dd)')
    argparser.add_argument('-T', '--title', help='Title to filter records by')
    return argparser

def get_token() -> str:
    """Set token from environment variable"""
    token = os.environ.get("PLUTO_TOKEN")
    if token == None:
        print("No token found. Exiting script...")
        sys.exit()
    decoded_token = jwt.decode(token, algorithms=[], options={"verify_signature": False})
    expiration_time = datetime.fromtimestamp(decoded_token["exp"])
    if expiration_time < datetime.now():
        print("Token has expired. Exiting script...")
        sys.exit()
    print(f"Token expires at: {expiration_time}\n")
    return token

def create_urls(base_url):
    update_url = f"{base_url}/pluto-core/api/project"
    commission_list_url = f"{base_url}/pluto-core/api/pluto/commission/list"
    project_list_url = f"{base_url}/pluto-core/api/project/list"

    return update_url, commission_list_url, project_list_url

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

def get_filtered_commission_records(timestamp, status, headers, commission_list_url, title=None) -> list:
    print("TIMESTAMP: ", timestamp)
    request_body = {
        "match": "W_CONTAINS",
        "completionDateBefore": timestamp
    }
    if status:
        request_body["status"] = status
    if title:
        request_body["title"] = title

    json_body = json.dumps(request_body)
    records = []

    try:
        json_content = api_put_request(commission_list_url, headers, json_body)
        total_records = json_content["count"]
        total_pages = (total_records + MAX_RECORDS_PER_PAGE - 1) // MAX_RECORDS_PER_PAGE
        start_at = 0

        for page in range(1, total_pages + 1):
            print(f"loading page: {page}")

            response = api_put_request(
                f"{commission_list_url}?startAt={start_at}&length={MAX_RECORDS_PER_PAGE}",
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
    unix_timestamp = str(time.time()).split(".")[0]
    with open(f"commissions_before{timestamp}-{unix_timestamp}.json", "a") as f:
        json.dump(records, f)
    return records

def get_projects(records, headers, timestamp, project_list_url, unix_timestamp) -> list:
    projects = []
    number_of_records = len(records)
    with open(f"projects_{timestamp}-{unix_timestamp}.json", "w") as f:
        f.write("[")  # start the array
    for i, record in enumerate(records):
        commission_id = record['id']
        print(f"{number_of_records - i} commissions to go...")

        print(f"Getting projects for commission ID: {commission_id}")
        try:
            json_content = api_put_request(
                project_list_url,
                headers,
                json.dumps({"match": "W_EXACT", "commissionId": commission_id}),
            )

            for project in json_content["result"]:
                
                if project['status'] == "Completed" or project['status'] == "Killed":
                    print(f"Skipping project {project['id']} with status: {project['status']}")
                    continue
                try:
                    if project['deletable'] == True and project['deep_archive'] == True:
                        print(f"Skipping project {project['id']} with deletable: {project['deletable']} and deep_archive: {project['deep_archive']}")
                        with open(f"check_{timestamp}-{unix_timestamp}.json", "a") as f:
                            f.write(json.dumps(project))
                            f.write(",")
                        continue
                except KeyError:
                    with open(f"check_{timestamp}-{unix_timestamp}.json", "a") as f:
                            f.write(json.dumps(project))
                            f.write(",")
                    continue
                created_timestamp = parse_timestamp(project['created'])
                parsed_timestamp = parse_timestamp(timestamp)
                if created_timestamp > parsed_timestamp:
                    print(f"Skipping project {project['id']} with created date: {project['created']}")
                    continue
                print(f"Adding project with id: {project['id']} to list of projects to update")
                projects += [project]
                with open(f"projects_{timestamp}-{unix_timestamp}.json", "a") as f:
                    f.write(json.dumps(project))
                    f.write(",")  # add a comma between records
        except requests.exceptions.RequestException as e:
            raise Exception(f"An error occurred. {e} Exiting script...")

    # remove the trailing comma and end the array
    with open(f"projects_{timestamp}-{unix_timestamp}.json", "rb+") as f:
        f.seek(-1, os.SEEK_END)
        f.truncate()
        f.write(b"]")

    return projects

def parse_timestamp(timestamp_str):
    try:
        dt = datetime.strptime(timestamp_str, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
    except ValueError:
        dt = datetime.strptime(timestamp_str, '%Y-%m-%dT%H:%M:%S.%fZ').replace(tzinfo=timezone.utc)
    return dt

def update_project_status(headers, timestamp, update_url) -> None:
    #open projects file
    user_input = input(f"Open projects_{timestamp}.json? (y/n): ")
    if user_input.lower() == "y":
        with open(f"projects_{timestamp}.json", "r") as f:
            projects = json.load(f)
    elif user_input.lower() == "n":
        path = input("Enter path to projects file: ")
        with open(path, "r") as f:
            projects = json.load(f)

    if projects:  
        display_projects(projects)
    else:
        print("No records to update")
        return
    print("Change status to: ")
    status = STATUS_STRINGS[get_input()]
    
    confirm = input(f"Do you want to update the status of these projects to \033[32m{status}\033[0m? (y/n): ")
    
    print(confirm.lower())
    if confirm.lower() != "y":
        print("Exiting script")
        return
    
    for project in projects:
        print(project)
        
        request_body = { "status": status }
        json_body = json.dumps(request_body)
        try:
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
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
    baseurl = args.baseurl or "https://local.prexit"
    update_url, commission_list_url, project_list_url = create_urls(baseurl)
    print(f"update_url: {update_url}")
    print(f"commission_list_url: {commission_list_url}")
    print(f"project_list_url: {project_list_url}")
    token = get_token()
    headers = get_headers(token)
    setup_logging()

    # Set the timestamp to filter records by
    timestamp = args.timestamp or "2022-01-01"
    timestamp = f"{timestamp}T00:00:00.0Z"

    unix_timestamp = str(time.time()).split(".")[0]
    

    choice = input("(G)et or (U)pdate projects?\n")
    if choice.lower() == "g":
        print(f"Get projects with a completion date before {timestamp} that are:")
        status = get_input()
        filtered_records = get_filtered_commission_records(timestamp=timestamp, title=args.title, headers=headers, status=STATUS_STRINGS[status], commission_list_url=commission_list_url)
        projects = get_projects(filtered_records, headers, timestamp, project_list_url, unix_timestamp)
        display_projects(projects)
    elif choice.lower() == "u":
        update_project_status(headers, timestamp, update_url)
      
if __name__ == "__main__":
    logging.info(f"Starting script at {datetime.now()}")
    main()
