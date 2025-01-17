import argparse
import sys
import os
import time
import requests
import json
import logging
from datetime import datetime
from collections import defaultdict

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

MAX_RECORDS_PER_PAGE = 100

def setup_argparser() -> argparse.ArgumentParser:
    """Set up the argument parser for the script"""
    argparser = argparse.ArgumentParser(description='Create list of overdue projects')
    argparser.add_argument('-b', '--baseurl', help='Base URL of the environment to run the script against')
    argparser.add_argument('-t', '--timestamp', help='Date to filter records before (yyyy-mm-dd)')
    return argparser

def get_token() -> str:
    """Set token from environment variable"""
    token = os.environ.get("PLUTO_TOKEN")
    if token == None:
        print("No token found. Exiting script...")
        sys.exit()
    return token

def create_urls(base_url):
    commission_list_url = f"{base_url}/pluto-core/api/pluto/commission/list"
    project_list_url = f"{base_url}/pluto-core/api/project/list"
    return commission_list_url, project_list_url

def get_headers(token: str) -> dict:
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }

def api_put_request(url, headers, json_body, max_retries=5):
    backoff_factor = 2
    for retry in range(max_retries):
        try:
            with requests.put(url, headers=headers, data=json_body, verify=False) as response:
                response.raise_for_status()
                return response.json()
        except (requests.exceptions.HTTPError, requests.exceptions.RequestException) as e:
            if retry == max_retries - 1:
                raise
            wait_time = backoff_factor ** retry
            print(f"An error occurred: {e}. Retrying in {wait_time} seconds...")
            time.sleep(wait_time)

def get_filtered_commission_records(timestamp, headers, commission_list_url) -> list:
    request_body = {
        "match": "W_CONTAINS",
        "completionDateBefore": timestamp
    }
    json_body = json.dumps(request_body)
    records = []

    try:
        json_content = api_put_request(commission_list_url, headers, json_body)
        total_records = json_content["count"]
        total_pages = (total_records + MAX_RECORDS_PER_PAGE - 1) // MAX_RECORDS_PER_PAGE
        start_at = 0

        for page in range(1, total_pages + 1):
            print(f"Loading commission page: {page}")
            response = api_put_request(
                f"{commission_list_url}?startAt={start_at}&length={MAX_RECORDS_PER_PAGE}",
                headers,
                json_body,
            )
            records.extend(response["result"])
            start_at += MAX_RECORDS_PER_PAGE

    except requests.exceptions.RequestException as e:
        print(e)
        raise Exception("An error occurred. Exiting script...")
    
    return records

def get_projects_by_user(records, headers, project_list_url) -> dict:
    user_projects = defaultdict(list)
    user_project_count = defaultdict(int)
    
    for record in records:
        commission_id = record['id']
        print(f"Getting projects for commission ID: {commission_id}")
        
        try:
            json_content = api_put_request(
                project_list_url,
                headers,
                json.dumps({"match": "W_EXACT", "commissionId": commission_id}),
            )

            for project in json_content["result"]:
                if project['status'] == "In Production":
                    user = project['user']
                    user_projects[user].append({
                        'id': project['id'],
                        'title': project['title'],
                        'commission_id': commission_id,
                        'created': project['created']
                    })
                    user_project_count[user] += 1

        except requests.exceptions.RequestException as e:
            print(f"Error getting projects for commission {commission_id}: {e}")
            continue

    return user_projects, user_project_count

def write_naughty_list(user_projects: dict, user_project_count: dict, timestamp: str):
    output_file = f"naughty_list_{timestamp}.txt"
    
    with open(output_file, "w") as f:
        f.write(f"Projects In Production from commissions before {timestamp}\n")
        f.write("=" * 80 + "\n\n")
        
        for user, projects in sorted(user_projects.items(), key=lambda x: len(x[1]), reverse=True):
            f.write(f"\nUser: {user} (Total overdue projects: {user_project_count[user]})\n")
            f.write("-" * 40 + "\n")
            for project in projects:
                f.write(f"Project ID: {project['id']:<6} Commission: {project['commission_id']:<6} Title: {project['title']}\n")
            f.write("\n")

def main():
    args = setup_argparser().parse_args()
    baseurl = args.baseurl or "https://local.prexit"
    commission_list_url, project_list_url = create_urls(baseurl)
    
    token = get_token()
    headers = get_headers(token)
    
    timestamp = args.timestamp or "2022-01-01"
    timestamp = f"{timestamp}T00:00:00.0Z"
    
    print(f"Getting commissions before {timestamp}")
    commissions = get_filtered_commission_records(timestamp, headers, commission_list_url)
    print(f"Found {len(commissions)} commissions")
    
    user_projects, user_project_count = get_projects_by_user(commissions, headers, project_list_url)
    write_naughty_list(user_projects, user_project_count, timestamp)
    
    print(f"\nNaughty list has been written to naughty_list_{timestamp}.txt")

if __name__ == "__main__":
    main()
