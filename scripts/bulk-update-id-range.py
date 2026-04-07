#!/usr/bin/env python3
"""
Bulk Update ID Range Script
Designed to manage project status changes within a specified ID range.

Features:
- Toggle individual status changes (e.g., Completed ↔ In Production)
- Process deletion workflow:
  1. Find 'New' or 'Held' projects → set them to 'Completed'
  2. Find 'Killed' projects → cycle them: Killed → Completed → Killed
  3. Find 'Completed' projects → trigger deletion: Completed → In Production → Completed
- Fix stuck 'In Production' projects → change them back to 'Completed'
- Comprehensive error handling and project tracking
- Date safety: Only modifies projects created before configurable cutoff date (default: 2022-01-01, protects newer projects)
"""

import argparse
import sys
import os
import time
import requests
import json
import logging
import jwt
from datetime import datetime, timezone

# Disable SSL warnings
requests.packages.urllib3.disable_warnings()

STATUS_STRINGS = ["New", "Held", "Completed", "Killed", "In Production"]

# Global variable to track changed projects for reverting
CHANGED_PROJECTS = []

# Date cutoff for safety - don't modify projects created on or after this date
# This will be set from command line arguments in main()
CUTOFF_DATE = None

def parse_cutoff_date(date_str):
    """Parse cutoff date string and return datetime object"""
    try:
        return datetime.strptime(date_str, '%Y-%m-%d').replace(tzinfo=timezone.utc)
    except ValueError:
        print(f"❌ Error: Invalid date format '{date_str}'. Please use YYYY-MM-DD format.")
        sys.exit(1)

def parse_project_created_date(created_str):
    """Parse project created date string and return datetime object"""
    if not created_str:
        return None
    try:
        # Try parsing with microseconds first
        return datetime.strptime(created_str, '%Y-%m-%dT%H:%M:%S.%fZ').replace(tzinfo=timezone.utc)
    except ValueError:
        try:
            # Try parsing without microseconds
            return datetime.strptime(created_str, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
        except ValueError:
            try:
                # Try parsing with just date
                return datetime.strptime(created_str[:10], '%Y-%m-%d').replace(tzinfo=timezone.utc)
            except ValueError:
                print(f"⚠️  Warning: Could not parse created date: {created_str}")
                return None

def is_project_too_new(project):
    """Check if project was created on or after the cutoff date"""
    created_str = project.get('created')
    if not created_str:
        print(f"⚠️  Warning: Project {project.get('id')} has no created date, allowing change")
        return False
    
    created_date = parse_project_created_date(created_str)
    if created_date is None:
        # If we can't parse the date, be conservative and allow the change
        return False
    
    return created_date >= CUTOFF_DATE

def filter_projects_by_date(projects, operation_name="operation", ignore_date_safety=False):
    """Filter out projects that are too new to modify (unless date safety is disabled)"""
    if ignore_date_safety:
        print(f"⚠️  DATE SAFETY DISABLED: Processing ALL projects regardless of creation date for {operation_name}")
        return projects
    
    safe_projects = []
    new_projects = []
    
    cutoff_date_str = CUTOFF_DATE.strftime('%Y-%m-%d') if CUTOFF_DATE else "Unknown"
    
    for project in projects:
        if is_project_too_new(project):
            created_str = project.get('created', 'Unknown')[:10]
            print(f"🛡️  SKIPPING project {project['id']} - Created {created_str} (on/after {cutoff_date_str}): {project.get('title', 'Unknown')}")
            new_projects.append(project)
        else:
            safe_projects.append(project)
    
    if new_projects:
        print(f"\n🛡️  SAFETY: Skipped {len(new_projects)} projects created on/after {cutoff_date_str} for {operation_name}")
        print(f"✅ Proceeding with {len(safe_projects)} projects created before {cutoff_date_str}")
    
    return safe_projects

def setup_argparser() -> argparse.ArgumentParser:
    """Set up the argument parser for the script"""
    argparser = argparse.ArgumentParser(description='Bulk update status of projects by ID range')
    argparser.add_argument('-b', '--baseurl', help='Base URL of the environment to run the script against', default="https://local.prexit")
    argparser.add_argument('--start-id', type=int, required=True, help='Start project ID for range filtering')
    argparser.add_argument('--end-id', type=int, required=True, help='End project ID for range filtering')
    argparser.add_argument('--auto-revert', type=int, help='Automatically revert changes after specified seconds')
    argparser.add_argument('--trigger-deletion', type=int, help='Process deletion: set New/Held to Completed, cycle Killed (Killed→Completed→Killed), then trigger deletion for Completed projects (wait time in seconds)', metavar='SECONDS')
    argparser.add_argument('--trigger-deletion-completed-only', type=int, help='Trigger deletion ONLY for projects already in Completed status within the ID range (Completed → In Production → Completed). This bypasses date safety checks. Provide wait time in seconds.', metavar='SECONDS')
    argparser.add_argument('--fix-in-production', action='store_true', help='Find projects stuck "In Production" and change them back to "Completed"')
    argparser.add_argument('--cutoff-date', type=str, default='2022-01-01', help='Date cutoff for safety check - only modify projects created before this date (format: YYYY-MM-DD, default: 2022-01-01)')
    argparser.add_argument('--ignore-date-safety', action='store_true', help='Disable date safety check (allows modifying projects created on/after the cutoff date)')
    argparser.add_argument('--dry-run', action='store_true', help='Show what would be changed without making actual changes')
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
    """Create API URLs"""
    update_url = f"{base_url}/pluto-core/api/project"
    project_list_url = f"{base_url}/pluto-core/api/project/list"
    return update_url, project_list_url

def get_headers(token: str) -> dict:
    """Create headers for API requests"""
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}",
    }

def setup_logging() -> None:
    """Setup logging configuration"""
    logging.basicConfig(
        filename="id_range_updates.log", 
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )

def api_put_request(url, headers, json_body, max_retries=5):
    """Make PUT request with retry logic"""
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

def get_projects_by_id_range(start_id, end_id, target_status, headers, project_list_url) -> list:
    """Get projects within an ID range that have a specific status"""
    projects = []
    unix_timestamp = str(time.time()).split(".")[0]
    
    print(f"Getting projects with IDs from {start_id} to {end_id} with status '{target_status}'...")
    
    # Create a file to store the results
    filename = f"projects_id_range_{start_id}_{end_id}_{target_status}_{unix_timestamp}.json"
    
    found_count = 0
    total_checked = end_id - start_id + 1
    
    for project_id in range(start_id, end_id + 1):
        if (project_id - start_id) % 50 == 0:  # Progress indicator
            print(f"Progress: {project_id - start_id + 1}/{total_checked} projects checked...")
            
        try:
            # Try to get the specific project by ID
            response = requests.get(
                f"{project_list_url.replace('/list', '')}/{project_id}",
                headers=headers,
                verify=False
            )
            
            if response.status_code == 200:
                api_response = response.json()
                
                # Extract the actual project data from the API response
                if api_response.get('status') == 'ok' and 'result' in api_response:
                    project = api_response['result']
                    
                    # Check if project has the target status
                    if project.get('status') == target_status:
                        print(f"✓ Found project {project_id} with status '{target_status}' - Title: {project.get('title', 'Unknown')}")
                        projects.append(project)
                        found_count += 1
                else:
                    print(f"⚠ Unexpected API response format for project {project_id}")
                    print(f"   Response: {api_response}")
                    
            elif response.status_code == 404:
                # Project doesn't exist, skip silently
                pass
            else:
                print(f"⚠ Error getting project {project_id}: HTTP {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            print(f"⚠ Error fetching project {project_id}: {e}")
            continue
    
    # Save results to file
    with open(filename, "w") as f:
        json.dump(projects, f, indent=2)
    
    print(f"\n📊 Summary:")
    print(f"   Total projects checked: {total_checked}")
    print(f"   Projects found with '{target_status}' status: {found_count}")
    print(f"   Results saved to: {filename}")
    
    return projects

def display_projects(projects) -> None:
    """Display project information in a formatted table"""
    if not projects:
        print("No projects to display")
        return
        
    print(f"\n{'='*120}")
    print(f"{'ID':<8} {'Commission':<12} {'Title':<50} {'User':<20} {'Status':<15} {'Created':<20}")
    print(f"{'='*120}")
    
    for project in projects:
        created_date = project.get('created', '')[:10] if project.get('created') else 'Unknown'
        print(f"{project['id']:<8} {project.get('commissionId', 'N/A'):<12} {project.get('title', 'Unknown')[:48]:<50} {project.get('user', 'Unknown')[:18]:<20} {project.get('status', 'Unknown'):<15} {created_date:<20}")
    
    print(f"{'='*120}")
    print(f"Total projects: {len(projects)}\n")

def toggle_projects_status(projects, from_status, to_status, headers, update_url, dry_run=False, ignore_date_safety=False) -> list:
    """Toggle project status and track changes for potential reverting"""
    global CHANGED_PROJECTS
    changed_projects = []
    
    # Filter out projects that are too new to modify
    safe_projects = filter_projects_by_date(projects, f"status change from '{from_status}' to '{to_status}'", ignore_date_safety)
    
    if not safe_projects:
        print(f"❌ No projects remaining after date filtering for status change from '{from_status}' to '{to_status}'")
        return []
    
    if dry_run:
        print(f"\n🔍 DRY RUN: Would change {len(safe_projects)} projects from '{from_status}' to '{to_status}'")
        for project in safe_projects:
            created_date = project.get('created', 'Unknown')[:10]
            print(f"   Would update project {project['id']} (created {created_date}): {project.get('title', 'Unknown')}")
        return []
    
    print(f"\n🔄 Changing {len(safe_projects)} projects from '{from_status}' to '{to_status}'...")
    
    confirm = input(f"Do you want to update the status of these projects to \033[32m{to_status}\033[0m? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ Cancelled status update")
        return []
    
    success_count = 0
    for i, project in enumerate(safe_projects, 1):
        try:
            request_body = {"status": to_status}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            # Track the change for potential reverting
            change_record = {
                "id": project['id'],
                "original_status": from_status,
                "new_status": to_status,
                "timestamp": datetime.now().isoformat(),
                "title": project.get('title', 'Unknown'),
                "user": project.get('user', 'Unknown')
            }
            changed_projects.append(change_record)
            
            print(f"✓ [{i}/{len(safe_projects)}] Updated project {project['id']}: {project.get('title', 'Unknown')[:40]}")
            logging.info(f"Updated project {project['id']} from '{from_status}' to '{to_status}'")
            success_count += 1
            
        except requests.exceptions.RequestException as e:
            print(f"❌ Error updating project {project['id']}: {e}")
            logging.error(f"Error updating project {project['id']}: {e}")
    
    # Save changed projects to file for potential reverting
    unix_timestamp = str(time.time()).split(".")[0]
    changes_filename = f"status_changes_{from_status}_to_{to_status}_{unix_timestamp}.json"
    with open(changes_filename, "w") as f:
        json.dump(changed_projects, f, indent=2)
    
    print(f"\n✅ Successfully changed {success_count}/{len(safe_projects)} projects")
    print(f"📁 Change log saved to: {changes_filename}")
    
    CHANGED_PROJECTS = changed_projects
    return changed_projects

def revert_recent_changes(headers, update_url) -> None:
    """Revert recently changed projects back to their original status"""
    global CHANGED_PROJECTS
    
    if not CHANGED_PROJECTS:
        # Try to find recent change files
        change_files = [f for f in os.listdir('.') if f.startswith('status_changes_') and f.endswith('.json')]
        if change_files:
            print("\n📁 Recent change files found:")
            for i, file in enumerate(sorted(change_files, reverse=True)[:5], 1):
                print(f"   {i}. {file}")
            
            choice = input("Enter file number to load (or press Enter to skip): ").strip()
            if choice.isdigit() and 1 <= int(choice) <= len(change_files):
                selected_file = sorted(change_files, reverse=True)[int(choice) - 1]
                with open(selected_file, "r") as f:
                    CHANGED_PROJECTS = json.load(f)
                print(f"📂 Loaded {len(CHANGED_PROJECTS)} changes from {selected_file}")
        
        if not CHANGED_PROJECTS:
            print("❌ No recent changes found to revert")
            return
    
    print(f"\n🔄 Found {len(CHANGED_PROJECTS)} recent changes to potentially revert:")
    print(f"{'ID':<8} {'Title':<40} {'Change':<25} {'Time':<20}")
    print(f"{'-'*100}")
    
    for change in CHANGED_PROJECTS:
        change_desc = f"{change['new_status']} → {change['original_status']}"
        timestamp = change['timestamp'][:19].replace('T', ' ')
        title = change.get('title', 'Unknown')[:38]
        print(f"{change['id']:<8} {title:<40} {change_desc:<25} {timestamp:<20}")
    
    confirm = input(f"\n🔄 Do you want to revert these {len(CHANGED_PROJECTS)} projects back to their original status? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ Cancelled revert operation")
        return
    
    reverted_count = 0
    for i, change in enumerate(CHANGED_PROJECTS, 1):
        try:
            request_body = {"status": change['original_status']}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{change['id']}/status",
                headers,
                json_body,
            )
            
            print(f"✅ [{i}/{len(CHANGED_PROJECTS)}] Reverted project {change['id']} to '{change['original_status']}'")
            logging.info(f"Reverted project {change['id']} to original status: {change['original_status']}")
            reverted_count += 1
            
        except requests.exceptions.RequestException as e:
            print(f"❌ Error reverting project {change['id']}: {e}")
            logging.error(f"Error reverting project {change['id']}: {e}")
    
    print(f"\n✅ Successfully reverted {reverted_count}/{len(CHANGED_PROJECTS)} projects")
    CHANGED_PROJECTS = []  # Clear the changes after reverting

def get_projects_for_processing(start_id, end_id, headers, project_list_url) -> tuple:
    """Get projects with status 'Completed', 'New', 'Held', or 'Killed' and separate them for different processing"""
    completed_projects = []
    to_complete_projects = []  # New and Held projects to be set to Completed
    killed_projects = []  # Killed projects need special cycle: Killed → Completed → Killed
    in_production_projects = []  # Track projects stuck in 'In Production'
    
    print(f"Getting projects with IDs from {start_id} to {end_id} with status 'Completed', 'New', 'Held', 'Killed', or 'In Production'...")
    
    unix_timestamp = str(time.time()).split(".")[0]
    filename = f"projects_for_processing_{start_id}_{end_id}_{unix_timestamp}.json"
    
    found_count = 0
    total_checked = end_id - start_id + 1
    
    for project_id in range(start_id, end_id + 1):
        if (project_id - start_id) % 50 == 0:  # Progress indicator
            print(f"Progress: {project_id - start_id + 1}/{total_checked} projects checked...")
            
        try:
            # Try to get the specific project by ID
            response = requests.get(
                f"{project_list_url.replace('/list', '')}/{project_id}",
                headers=headers,
                verify=False
            )
            
            if response.status_code == 200:
                api_response = response.json()
                
                # Extract the actual project data from the API response
                if api_response.get('status') == 'ok' and 'result' in api_response:
                    project = api_response['result']
                    
                    # Categorize projects by their current status
                    project_status = project.get('status')
                    if project_status == 'Completed':
                        print(f"✓ Found COMPLETED project {project_id} - Title: {project.get('title', 'Unknown')} (will trigger deletion)")
                        completed_projects.append(project)
                        found_count += 1
                    elif project_status in ['New', 'Held']:
                        print(f"✓ Found {project_status} project {project_id} - Title: {project.get('title', 'Unknown')} (will set to Completed)")
                        to_complete_projects.append(project)
                        found_count += 1
                    elif project_status == 'Killed':
                        print(f"✓ Found KILLED project {project_id} - Title: {project.get('title', 'Unknown')} (will cycle: Killed → Completed → Killed)")
                        killed_projects.append(project)
                        found_count += 1
                    elif project_status == 'In Production':
                        print(f"⚠️ Found IN PRODUCTION project {project_id} - Title: {project.get('title', 'Unknown')} (may be stuck)")
                        in_production_projects.append(project)
                    else:
                        # Debug: Show what other statuses we're finding
                        print(f"- Skipping project {project_id} with status '{project_status}' - Title: {project.get('title', 'Unknown')}")
                else:
                    print(f"⚠ Unexpected API response format for project {project_id}")
                    
        except requests.exceptions.RequestException as e:
            print(f"⚠ Error fetching project {project_id}: {e}")
            continue
    
    # Save results to file
    results = {
        "completed_projects": completed_projects,
        "to_complete_projects": to_complete_projects,
        "killed_projects": killed_projects,
        "in_production_projects": in_production_projects,
        "summary": {
            "total_checked": total_checked,
            "completed_count": len(completed_projects),
            "to_complete_count": len(to_complete_projects),
            "killed_count": len(killed_projects),
            "in_production_count": len(in_production_projects),
            "total_found": found_count
        }
    }
    
    with open(filename, "w") as f:
        json.dump(results, f, indent=2)
    
    print(f"\n📊 Summary:")
    print(f"   Total projects checked: {total_checked}")
    print(f"   Completed projects (will trigger deletion): {len(completed_projects)}")
    print(f"   New/Held projects (will set to Completed): {len(to_complete_projects)}")
    print(f"   Killed projects (will cycle: Killed → Completed → Killed): {len(killed_projects)}")
    if in_production_projects:
        print(f"   ⚠️  In Production projects (may be stuck): {len(in_production_projects)}")
    print(f"   Results saved to: {filename}")
    
    # Display In Production projects notification if any found
    if in_production_projects:
        print(f"\n⚠️  WARNING: Found {len(in_production_projects)} projects still 'In Production':")
        for project in in_production_projects:
            print(f"   - Project {project['id']}: {project.get('title', 'Unknown')} (User: {project.get('user', 'Unknown')})")
        print(f"   These projects may be stuck from a previous deletion attempt.")
    
    return completed_projects, to_complete_projects, killed_projects

def cycle_killed_projects(projects, headers, update_url, wait_time=5, dry_run=False, ignore_date_safety=False) -> bool:
    """
    Cycle Killed projects: Killed → Completed → Killed
    This ensures they go through the Completed status before returning to Killed.
    """
    if not projects:
        return True
    
    # Filter out projects that are too new to modify
    safe_projects = filter_projects_by_date(projects, "Killed project cycling", ignore_date_safety)
    
    if not safe_projects:
        print(f"❌ No Killed projects remaining after date filtering")
        return True
    
    if dry_run:
        print(f"\n🔍 DRY RUN: Would cycle {len(safe_projects)} Killed projects")
        print(f"   Workflow: Killed → Completed (wait {wait_time}s) → Killed")
        for project in safe_projects:
            created_date = project.get('created', 'Unknown')[:10]
            print(f"   Would cycle project {project['id']} (created {created_date}): {project.get('title', 'Unknown')}")
        return True
    
    print(f"\n🔄 KILLED PROJECT CYCLE")
    print(f"   Projects to cycle: {len(safe_projects)}")
    print(f"   Workflow: Killed → Completed → wait {wait_time}s → Killed")
    
    confirm = input(f"\n⚠️  Do you want to cycle these {len(safe_projects)} Killed projects? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ Cancelled Killed project cycling")
        return False
    
    # Step 1: Change from Killed to Completed
    print(f"\n📤 Step 1/3: Changing {len(safe_projects)} projects from 'Killed' to 'Completed'...")
    successfully_changed_to_completed = []
    failed_step1 = []
    
    for i, project in enumerate(safe_projects, 1):
        try:
            request_body = {"status": "Completed"}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            successfully_changed_to_completed.append(project)
            print(f"✓ [{i}/{len(safe_projects)}] Changed project {project['id']} to 'Completed'")
            logging.info(f"Killed cycle step 1: Changed project {project['id']} from 'Killed' to 'Completed'")
            
        except requests.exceptions.RequestException as e:
            failed_step1.append({"project": project, "error": str(e)})
            print(f"❌ [{i}/{len(safe_projects)}] Error updating project {project['id']}: {e}")
            logging.error(f"Error in Killed cycle step 1 for project {project['id']}: {e}")
    
    success_count = len(successfully_changed_to_completed)
    if success_count == 0:
        print("❌ No projects were successfully changed to 'Completed'. Aborting cycle.")
        return False
    
    if failed_step1:
        print(f"⚠️  {len(failed_step1)} projects failed to change to 'Completed'")
        print(f"✅ {success_count} projects successfully changed to 'Completed'")
    else:
        print(f"✅ All {success_count} projects successfully changed to 'Completed'")
    
    # Step 2: Wait
    print(f"\n⏰ Step 2/3: Waiting {wait_time} seconds before reverting to 'Killed'...")
    for remaining in range(wait_time, 0, -1):
        print(f"   Waiting {remaining} seconds...", end='\r')
        time.sleep(1)
    print(f"   Wait complete!{' ' * 20}")  # Clear the line
    
    # Step 3: Change back from Completed to Killed
    print(f"\n📥 Step 3/3: Changing {len(successfully_changed_to_completed)} projects back to 'Killed'...")
    successfully_reverted = []
    failed_step3 = []
    
    for i, project in enumerate(successfully_changed_to_completed, 1):
        try:
            request_body = {"status": "Killed"}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            successfully_reverted.append(project)
            print(f"✓ [{i}/{len(successfully_changed_to_completed)}] Changed project {project['id']} back to 'Killed'")
            logging.info(f"Killed cycle step 3: Changed project {project['id']} from 'Completed' to 'Killed'")
            
        except requests.exceptions.RequestException as e:
            failed_step3.append({"project": project, "error": str(e)})
            print(f"❌ [{i}/{len(successfully_changed_to_completed)}] Error reverting project {project['id']}: {e}")
            logging.error(f"Error in Killed cycle step 3 for project {project['id']}: {e}")
    
    final_success_count = len(successfully_reverted)
    stuck_projects = [item["project"] for item in failed_step3]
    
    # Save operation log
    unix_timestamp = str(time.time()).split(".")[0]
    cycle_log = {
        "timestamp": datetime.now().isoformat(),
        "operation": "killed_project_cycle",
        "wait_time_seconds": wait_time,
        "projects_attempted": len(safe_projects),
        "step1_success": success_count,
        "step1_failed": len(failed_step1),
        "step3_success": final_success_count,
        "step3_failed": len(failed_step3),
        "projects_stuck_completed": [
            {
                "id": p['id'],
                "title": p.get('title', 'Unknown'),
                "user": p.get('user', 'Unknown'),
                "error": next(item["error"] for item in failed_step3 if item["project"]["id"] == p["id"])
            } for p in stuck_projects
        ],
        "successfully_processed": [
            {
                "id": p['id'],
                "title": p.get('title', 'Unknown'),
                "user": p.get('user', 'Unknown')
            } for p in successfully_reverted
        ],
        "failed_step1": [
            {
                "id": item["project"]['id'],
                "title": item["project"].get('title', 'Unknown'),
                "error": item["error"]
            } for item in failed_step1
        ]
    }
    
    log_filename = f"killed_cycle_log_{unix_timestamp}.json"
    with open(log_filename, "w") as f:
        json.dump(cycle_log, f, indent=2)
    
    print(f"\n🎯 KILLED PROJECT CYCLE COMPLETED")
    print(f"   ✅ Step 1: {success_count}/{len(safe_projects)} projects changed to 'Completed'")
    if failed_step1:
        print(f"   ❌ Step 1 failures: {len(failed_step1)} projects")
    print(f"   ⏰ Step 2: Waited {wait_time} seconds")
    print(f"   ✅ Step 3: {final_success_count}/{len(successfully_changed_to_completed)} projects changed back to 'Killed'")
    if failed_step3:
        print(f"   ❌ Step 3 failures: {len(failed_step3)} projects")
    print(f"   📁 Operation log saved to: {log_filename}")
    
    if final_success_count == len(safe_projects):
        print(f"   🚀 All {final_success_count} Killed projects successfully cycled!")
    else:
        print(f"   ⚠️  Only {final_success_count}/{len(safe_projects)} projects completed the full cycle")
        
        if stuck_projects:
            print(f"\n⚠️  WARNING: {len(stuck_projects)} projects are now stuck in 'Completed' status:")
            for project in stuck_projects:
                error_msg = next(item["error"] for item in failed_step3 if item["project"]["id"] == project["id"])
                print(f"   - Project {project['id']}: {project.get('title', 'Unknown')} (Error: {error_msg[:50]}...)")
            print(f"   These projects may need manual intervention to return to 'Killed' status")
    
    return final_success_count > 0

def set_projects_to_completed(projects, headers, update_url, dry_run=False, ignore_date_safety=False) -> bool:
    """Set New or Held projects to Completed status"""
    if not projects:
        return True
    
    # Filter out projects that are too new to modify
    safe_projects = filter_projects_by_date(projects, "setting to 'Completed' status", ignore_date_safety)
    
    if not safe_projects:
        print(f"❌ No projects remaining after date filtering for setting to 'Completed'")
        return True  # Return True since we're not failing, just skipping new projects
        
    if dry_run:
        print(f"\n🔍 DRY RUN: Would set {len(safe_projects)} projects to 'Completed'")
        for project in safe_projects:
            created_date = project.get('created', 'Unknown')[:10]
            print(f"   Would change project {project['id']} ({project.get('status')}) to 'Completed' (created {created_date}): {project.get('title', 'Unknown')}")
        return True
    
    print(f"\n📝 Setting {len(safe_projects)} projects to 'Completed' status...")
    
    confirm = input(f"Do you want to set these {len(safe_projects)} projects to 'Completed'? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ Cancelled setting projects to Completed")
        return False
    
    success_count = 0
    for i, project in enumerate(safe_projects, 1):
        try:
            request_body = {"status": "Completed"}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            print(f"✓ [{i}/{len(safe_projects)}] Changed project {project['id']} from '{project.get('status')}' to 'Completed'")
            logging.info(f"Changed project {project['id']} from '{project.get('status')}' to 'Completed'")
            success_count += 1
            
        except requests.exceptions.RequestException as e:
            print(f"❌ Error updating project {project['id']}: {e}")
            logging.error(f"Error updating project {project['id']}: {e}")
    
    print(f"\n✅ Successfully changed {success_count}/{len(safe_projects)} projects to 'Completed'")
    return success_count > 0

def fix_stuck_in_production_projects(start_id, end_id, headers, project_list_url, update_url, dry_run=False, ignore_date_safety=False) -> bool:
    """Find and fix projects stuck in 'In Production' status"""
    print(f"🔍 Searching for projects stuck 'In Production' in range {start_id}-{end_id}...")
    in_production_projects = get_projects_by_id_range(
        start_id, end_id, "In Production", headers, project_list_url
    )
    
    if not in_production_projects:
        print("✅ No projects found stuck 'In Production' - all clear!")
        return True
    
    print(f"\n⚠️  Found {len(in_production_projects)} projects stuck 'In Production':")
    print("These projects may be stuck from failed deletion attempts or system errors.")
    display_projects(in_production_projects)
    
    if dry_run:
        print("🔍 DRY RUN: Would change these projects from 'In Production' to 'Completed'")
        return True
    
    confirm = input(f"\n🔧 Do you want to change these {len(in_production_projects)} projects back to 'Completed'? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ No changes made")
        return False
    
    changed_projects = toggle_projects_status(
        in_production_projects, "In Production", "Completed", headers, update_url, dry_run, ignore_date_safety
    )
    
    if changed_projects:
        print(f"✅ Successfully changed {len(changed_projects)} projects from 'In Production' to 'Completed'")
        print("These projects are now eligible for deletion processing again.")
        return True
    else:
        print("❌ No projects were changed")
        return False

def trigger_deletion_process(projects, headers, update_url, wait_time=5, dry_run=False, ignore_date_safety=False) -> bool:
    """
    Automatically cycle projects: Completed -> In Production -> Completed
    This triggers the deletion process for the projects.
    """
    # Filter out projects that are too new to modify
    safe_projects = filter_projects_by_date(projects, "deletion trigger process", ignore_date_safety)
    
    if not safe_projects:
        print(f"❌ No projects remaining after date filtering for deletion trigger process")
        return False
    
    if dry_run:
        print(f"\n🔍 DRY RUN: Would trigger deletion process for {len(safe_projects)} projects")
        print(f"   Workflow: Completed → In Production (wait {wait_time}s) → Completed")
        for project in safe_projects:
            created_date = project.get('created', 'Unknown')[:10]
            print(f"   Would process project {project['id']} (created {created_date}): {project.get('title', 'Unknown')}")
        return True
    
    print(f"\n🔄 DELETION TRIGGER PROCESS")
    print(f"   Projects to process: {len(safe_projects)}")
    print(f"   Workflow: Completed → In Production → wait {wait_time}s → Completed")
    print(f"   This will trigger the deletion process for these projects.")
    
    confirm = input(f"\n⚠️  Do you want to trigger deletion for these {len(safe_projects)} projects? (y/n): ")
    
    if confirm.lower() != "y":
        print("❌ Cancelled deletion trigger process")
        return False
    
    # Step 1: Change from Completed to In Production
    print(f"\n📤 Step 1/3: Changing {len(safe_projects)} projects to 'In Production'...")
    successfully_changed_to_production = []
    failed_step1 = []
    
    for i, project in enumerate(safe_projects, 1):
        try:
            request_body = {"status": "In Production"}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            # Only track projects that were actually successfully changed
            successfully_changed_to_production.append(project)
            print(f"✓ [{i}/{len(safe_projects)}] Changed project {project['id']} to 'In Production'")
            logging.info(f"Deletion trigger step 1: Changed project {project['id']} to 'In Production'")
            
        except requests.exceptions.RequestException as e:
            failed_step1.append({"project": project, "error": str(e)})
            print(f"❌ [{i}/{len(safe_projects)}] Error updating project {project['id']}: {e}")
            logging.error(f"Error in deletion trigger step 1 for project {project['id']}: {e}")
    
    success_count = len(successfully_changed_to_production)
    if success_count == 0:
        print("❌ No projects were successfully changed to 'In Production'. Aborting process.")
        return False
    
    if failed_step1:
        print(f"⚠️  {len(failed_step1)} projects failed to change to 'In Production'")
        print(f"✅ {success_count} projects successfully changed to 'In Production'")
    else:
        print(f"✅ All {success_count} projects successfully changed to 'In Production'")
    
    # Step 2: Wait
    print(f"\n⏰ Step 2/3: Waiting {wait_time} seconds before reverting...")
    for remaining in range(wait_time, 0, -1):
        print(f"   Waiting {remaining} seconds...", end='\r')
        time.sleep(1)
    print(f"   Wait complete!{' ' * 20}")  # Clear the line
    
    # Step 3: Change back from In Production to Completed (only for projects that were successfully changed)
    print(f"\n📥 Step 3/3: Reverting {len(successfully_changed_to_production)} projects back to 'Completed'...")
    successfully_reverted = []
    failed_step3 = []
    
    for i, project in enumerate(successfully_changed_to_production, 1):
        try:
            request_body = {"status": "Completed"}
            json_body = json.dumps(request_body)
            
            json_content = api_put_request(
                f"{update_url}/{project['id']}/status",
                headers,
                json_body,
            )
            
            successfully_reverted.append(project)
            print(f"✓ [{i}/{len(successfully_changed_to_production)}] Reverted project {project['id']} to 'Completed'")
            logging.info(f"Deletion trigger step 3: Reverted project {project['id']} to 'Completed'")
            
        except requests.exceptions.RequestException as e:
            failed_step3.append({"project": project, "error": str(e)})
            print(f"❌ [{i}/{len(successfully_changed_to_production)}] Error reverting project {project['id']}: {e}")
            logging.error(f"Error in deletion trigger step 3 for project {project['id']}: {e}")
    
    final_success_count = len(successfully_reverted)
    
    # Projects that are now stuck in 'In Production' (failed Step 3)
    stuck_in_production = [item["project"] for item in failed_step3]
    
    # Save operation log
    unix_timestamp = str(time.time()).split(".")[0]
    deletion_log = {
        "timestamp": datetime.now().isoformat(),
        "operation": "deletion_trigger",
        "wait_time_seconds": wait_time,
        "projects_attempted": len(safe_projects),
        "step1_success": success_count,
        "step1_failed": len(failed_step1),
        "step3_success": final_success_count,
        "step3_failed": len(failed_step3),
        "projects_stuck_in_production": [
            {
                "id": p['id'],
                "title": p.get('title', 'Unknown'),
                "user": p.get('user', 'Unknown'),
                "error": next(item["error"] for item in failed_step3 if item["project"]["id"] == p["id"])
            } for p in stuck_in_production
        ],
        "successfully_processed": [
            {
                "id": p['id'],
                "title": p.get('title', 'Unknown'),
                "user": p.get('user', 'Unknown')
            } for p in successfully_reverted
        ],
        "failed_step1": [
            {
                "id": item["project"]['id'],
                "title": item["project"].get('title', 'Unknown'),
                "error": item["error"]
            } for item in failed_step1
        ]
    }
    
    log_filename = f"deletion_trigger_log_{unix_timestamp}.json"
    with open(log_filename, "w") as f:
        json.dump(deletion_log, f, indent=2)
    
    print(f"\n🎯 DELETION TRIGGER COMPLETED")
    print(f"   ✅ Step 1: {success_count}/{len(safe_projects)} projects changed to 'In Production'")
    if failed_step1:
        print(f"   ❌ Step 1 failures: {len(failed_step1)} projects")
    print(f"   ⏰ Step 2: Waited {wait_time} seconds")
    print(f"   ✅ Step 3: {final_success_count}/{len(successfully_changed_to_production)} projects reverted to 'Completed'")
    if failed_step3:
        print(f"   ❌ Step 3 failures: {len(failed_step3)} projects")
    print(f"   📁 Operation log saved to: {log_filename}")
    
    if final_success_count == len(safe_projects):
        print(f"   🚀 Deletion process should now be triggered for all {final_success_count} projects!")
    else:
        print(f"   ⚠️  Only {final_success_count}/{len(safe_projects)} projects completed the full cycle")
        
        if stuck_in_production:
            print(f"\n⚠️  WARNING: {len(stuck_in_production)} projects are now stuck 'In Production':")
            for project in stuck_in_production:
                error_msg = next(item["error"] for item in failed_step3 if item["project"]["id"] == project["id"])
                print(f"   - Project {project['id']}: {project.get('title', 'Unknown')} (Error: {error_msg[:50]}...)")
            print(f"\n🔧 To fix these stuck projects:")
            print(f"   • Use option 3 in the interactive menu, OR")
            print(f"   • Run this script with --fix-in-production and your desired --start-id/--end-id range")
    
    return final_success_count > 0

def main():
    """Main execution function"""
    global CUTOFF_DATE
    
    args = setup_argparser().parse_args()
    
    # Parse and set the cutoff date
    CUTOFF_DATE = parse_cutoff_date(args.cutoff_date)
    
    # Validate arguments
    if args.start_id > args.end_id:
        print("❌ Error: start-id must be less than or equal to end-id")
        sys.exit(1)
    
    if args.end_id - args.start_id > 10000:
        confirm = input(f"⚠ Large range detected ({args.end_id - args.start_id + 1} projects). Continue? (y/n): ")
        if confirm.lower() != 'y':
            print("❌ Operation cancelled")
            sys.exit(1)
    
    # Setup
    baseurl = args.baseurl
    update_url, project_list_url = create_urls(baseurl)
    token = get_token()
    headers = get_headers(token)
    setup_logging()
    
    print(f"🚀 Starting ID Range Status Toggle")
    print(f"   Range: {args.start_id} - {args.end_id}")
    print(f"   Environment: {baseurl}")
    print(f"   Date cutoff: {CUTOFF_DATE.strftime('%Y-%m-%d')} (only modify projects created before this date)")
    print(f"   Date safety: {'Disabled' if args.ignore_date_safety else 'Enabled'}")
    print(f"   Dry run: {'Yes' if args.dry_run else 'No'}")
    
    # Handle command line trigger-deletion-completed-only option (no date guardrail)
    if args.trigger_deletion_completed_only:
        wait_time = args.trigger_deletion_completed_only
        print(f"\n🗑️ Deletion trigger for COMPLETED projects only (no date guardrail)")
        print(f"   Wait between status changes: {wait_time} seconds")
        print(f"   Date safety bypass: Enabled for this operation")

        completed_projects = get_projects_by_id_range(
            args.start_id, args.end_id, "Completed", headers, project_list_url
        )

        if not completed_projects:
            print(f"❌ No 'Completed' projects found in ID range {args.start_id}-{args.end_id}")
            return

        display_projects(completed_projects)

        success = trigger_deletion_process(
            completed_projects, headers, update_url, wait_time, args.dry_run, ignore_date_safety=True
        )

        if success:
            print("\n🎯 Completed-only deletion trigger finished")
        else:
            print("\n❌ Completed-only deletion trigger failed or was cancelled")
        return

    # Handle command line trigger-deletion option
    if args.trigger_deletion:
        print(f"   Auto deletion trigger: {args.trigger_deletion} seconds")
        completed_projects, to_complete_projects, killed_projects = get_projects_for_processing(
            args.start_id, args.end_id, headers, project_list_url
        )
        
        # Handle New/Held projects first (set them to Completed)
        if to_complete_projects:
            print(f"\n🔄 First, setting {len(to_complete_projects)} New/Held projects to 'Completed'...")
            display_projects(to_complete_projects)
            set_success = set_projects_to_completed(to_complete_projects, headers, update_url, args.dry_run, args.ignore_date_safety)
            if not set_success and not args.dry_run:
                print("❌ Failed to set projects to Completed. Aborting deletion trigger.")
                return
        
        # Handle Killed projects (cycle: Killed → Completed → Killed)
        if killed_projects:
            print(f"\n🔄 Second, cycling {len(killed_projects)} Killed projects: Killed → Completed → Killed...")
            display_projects(killed_projects)
            cycle_success = cycle_killed_projects(killed_projects, headers, update_url, args.trigger_deletion, args.dry_run, args.ignore_date_safety)
            if not cycle_success and not args.dry_run:
                print("❌ Failed to cycle Killed projects. Continuing with deletion trigger...")
        
        # Handle Completed projects (trigger deletion)
        if completed_projects:
            print(f"\n🗑️ Finally, triggering deletion for {len(completed_projects)} Completed projects...")
            display_projects(completed_projects)
            success = trigger_deletion_process(
                completed_projects, headers, update_url, args.trigger_deletion, args.dry_run, args.ignore_date_safety
            )
            if success:
                print(f"\n🎯 Deletion trigger process completed!")
            else:
                print(f"\n❌ Deletion trigger process failed or was cancelled")
        
        if not completed_projects and not to_complete_projects and not killed_projects:
            print(f"❌ No projects with 'Completed', 'New', 'Held', or 'Killed' status found in ID range {args.start_id}-{args.end_id}")
        return

    
    # Handle command line fix-in-production option
    if args.fix_in_production:
        print(f"   Fix In Production: Finding and fixing stuck projects")
        fix_stuck_in_production_projects(
            args.start_id, args.end_id, headers, project_list_url, update_url, args.dry_run, args.ignore_date_safety
        )
        return
    
    # Main workflow
    while True:
        print(f"\n{'='*60}")
        print(f"🎯 ID Range Workflow (Projects {args.start_id} - {args.end_id})")
        print(f"{'='*60}")
        print("1. 🔍 Find 'Completed' projects and change to 'In Production'")
        print("2. 🗑️  Process deletion: New/Held → Completed, Killed → Completed → Killed, then trigger deletion for Completed")
        print("3. 🔧 Fix projects stuck 'In Production' (change to 'Completed')")
        print("4. 🔄 Revert recent changes back to 'Completed'")
        print("5. 📊 Get projects by status in ID range")
        print("6. 🚪 Exit")
        
        choice = input("\nSelect option (1-6): ").strip()
        
        if choice == "1":
            # Get Completed projects and change to In Production
            completed_projects = get_projects_by_id_range(
                args.start_id, args.end_id, "Completed", headers, project_list_url
            )
            
            if completed_projects:
                display_projects(completed_projects)
                changed_projects = toggle_projects_status(
                    completed_projects, "Completed", "In Production", headers, update_url, args.dry_run, args.ignore_date_safety
                )
                
                if changed_projects and not args.dry_run:
                    # Handle auto-revert or manual prompt
                    if args.auto_revert:
                        wait_time = args.auto_revert
                        print(f"⏰ Auto-revert enabled. Waiting {wait_time} seconds...")
                        time.sleep(wait_time)
                        print("🔄 Auto-reverting changes...")
                        revert_recent_changes(headers, update_url)
                    else:
                        wait_time = input("\n⏰ Enter seconds to wait before offering revert (or press Enter for 5): ").strip()
                        wait_time = int(wait_time) if wait_time.isdigit() else 5
                        
                        print(f"⏰ Waiting {wait_time} seconds...")
                        time.sleep(wait_time)
                        
                        revert_choice = input("🔄 Do you want to revert these changes back to 'Completed'? (y/n): ")
                        if revert_choice.lower() == "y":
                            revert_recent_changes(headers, update_url)
            else:
                print("❌ No 'Completed' projects found in the specified ID range")
        
        elif choice == "2":
            # Process deletion: New/Held → Completed, Killed → Completed → Killed, then trigger deletion for Completed
            completed_projects, to_complete_projects, killed_projects = get_projects_for_processing(
                args.start_id, args.end_id, headers, project_list_url
            )
            
            wait_time = input("\n⏰ Enter seconds to wait between status changes (or press Enter for 5): ").strip()
            wait_time = int(wait_time) if wait_time.isdigit() else 5
            
            # Handle New/Held projects first (set them to Completed)
            if to_complete_projects:
                print(f"\n🔄 Step 1: Setting {len(to_complete_projects)} New/Held projects to 'Completed'...")
                display_projects(to_complete_projects)
                set_success = set_projects_to_completed(to_complete_projects, headers, update_url, args.dry_run, args.ignore_date_safety)
                if not set_success and not args.dry_run:
                    print("❌ Failed to set projects to Completed. Aborting deletion trigger.")
                    continue
            
            # Handle Killed projects (cycle: Killed → Completed → Killed)
            if killed_projects:
                print(f"\n🔄 Step 2: Cycling {len(killed_projects)} Killed projects: Killed → Completed → Killed...")
                display_projects(killed_projects)
                cycle_success = cycle_killed_projects(killed_projects, headers, update_url, wait_time, args.dry_run, args.ignore_date_safety)
                if not cycle_success and not args.dry_run:
                    print("⚠️ Failed to cycle Killed projects. Continuing with deletion trigger...")
            
            # Handle Completed projects (trigger deletion)
            if completed_projects:
                step_num = 3 if (to_complete_projects or killed_projects) else 1
                print(f"\n🗑️ Step {step_num}: Triggering deletion for {len(completed_projects)} Completed projects...")
                display_projects(completed_projects)
                
                success = trigger_deletion_process(
                    completed_projects, headers, update_url, wait_time, args.dry_run, args.ignore_date_safety
                )
                if success:
                    print(f"\n🎯 Deletion trigger process completed!")
                else:
                    print(f"\n❌ Deletion trigger process failed or was cancelled")
            
            if not completed_projects and not to_complete_projects and not killed_projects:
                print("❌ No projects with 'Completed', 'New', 'Held', or 'Killed' status found in the specified ID range")
        
        elif choice == "3":
            # Fix projects stuck 'In Production'
            fix_stuck_in_production_projects(
                args.start_id, args.end_id, headers, project_list_url, update_url, args.dry_run, args.ignore_date_safety
            )
        
        elif choice == "4":
            # Revert recent changes
            revert_recent_changes(headers, update_url)
        
        elif choice == "5":
            # Get projects by status in range
            print("\n📊 Available statuses:")
            for i, status in enumerate(STATUS_STRINGS, 1):
                print(f"   {i}. {status}")
            
            status_choice = input("Select status (1-5): ").strip()
            if status_choice in ["1", "2", "3", "4", "5"]:
                target_status = STATUS_STRINGS[int(status_choice) - 1]
                projects = get_projects_by_id_range(
                    args.start_id, args.end_id, target_status, headers, project_list_url
                )
                if projects:
                    display_projects(projects)
                else:
                    print(f"❌ No projects found with status '{target_status}' in ID range {args.start_id}-{args.end_id}")
            else:
                print("❌ Invalid choice")
        
        elif choice == "6":
            print("👋 Exiting ID range workflow")
            break
        
        else:
            print("❌ Invalid choice, please try again")

if __name__ == "__main__":
    logging.info(f"Starting ID range script at {datetime.now()}")
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n👋 Script interrupted by user")
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        logging.error(f"Unexpected error: {e}")
        sys.exit(1) 