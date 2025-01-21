# Bulk Status Update of Records Script

This Python script allows for bulk updating the status of records within a system. It retrieves records from an API based on certain filters, and then updates their status according to user input. The script also has robust error handling and logging mechanisms in place.

## Prerequisites

To use this script, you will need:
- Python 3 installed on your system
- run `source venv/bin/activate` (`source venv/bin/activate.fish` is using fish shell)
- The `PLUTO_TOKEN` environment variable set with a valid token. The token can be obtained from logging into pluto with developer tools open and grabbing the `authorization: Bearer` token from the headers tab. Do this by running `export PLUTO_TOKEN=<your-token>`

## Usage

To run the script, use the following command:
`python3 bulk-update-status.py -b <baseurl>

Here are the details about the script parameters:

- `-b, --baseurl` : Base URL of the environment to run the script against. Default is `https://local.prexit`
- `-t, --timestamp` : Date to filter records before (format: yyyy-mm-dd). Default is `2022-01-01`
- `-T, --title` : Title to filter records by (used for testing)

You can choose between two modes by entering either `G` (to Get projects) or `U` (to Update projects) when prompted.

## Features

- Get a list of records that match a certain status and completion date
- Bulk update the status of a list of projects based on user input
- Handles failed API requests with a backoff and retry mechanism
- Detailed logging of actions and errors into a `data.log` file
- Writes a `projects_<timestamp>.json` file with the (G)et command of projects before the specified date
- Writes a `check_<timestamp>.json` file of projects to check as _deletable_ and _deep_archive_ have been selected by the user.

## How It Works

The script starts by parsing command-line arguments and getting an access token from an environment variable. It then sets up the necessary headers for API requests and initiates logging.

Based on user input, the script either retrieves a list of projects based on certain filters or updates the status of projects.

When retrieving projects, it sends a PUT request to the `commission_list_url` endpoint with certain filters. The results are then parsed and written to a file.

When updating project status, it first reads a list of projects from a file. It then prompts the user to confirm the new status and sends a PUT request to the `update_url` endpoint for each project.
