#!/usr/bin/env python3

from pathlib import Path
from cordra_auth import get_auth_token
import requests
import argparse
from getpass import getpass

# ignore https warning
import urllib3
urllib3.disable_warnings()

# Parse arguments
parser = argparse.ArgumentParser()
parser.add_argument("url", nargs='?', default="https://localhost:8443")
parser.add_argument("-u", "--user", required=False, default="admin")
parser.add_argument("-p", "--password", required=False, default="")
args = parser.parse_args()

USER = args.user
BASE_URL = args.url
PASSWORD = args.password if args.password else getpass(prompt="admin password: ")


token = get_auth_token(BASE_URL, USER, PASSWORD)

search_results = requests.get(BASE_URL + "/search?query=*:*&pageSize=0", verify=False, headers={"Authorization": f"Bearer {token}"})
if search_results.status_code != 200:
    raise Exception(f"failed to get search results: {search_results.json()}")
print(search_results.json())

num_results = search_results.json()["size"]
deleted = 0

while True:
    search_results = requests.get(BASE_URL + f"/search?query=*:*&pageSize=100", verify=False, headers={"Authorization": f"Bearer {token}"})
    ids_to_delete = [obj["id"] for obj in search_results.json()["results"] if obj["type"] != "Schema"]
    if len(ids_to_delete) == 0 or len(search_results.json()["results"]) == 0:
        break
    for id in ids_to_delete:
        delete_req = requests.delete(BASE_URL + f"/objects/{id}", verify=False, headers={"Authorization": f"Bearer {token}"})
        if delete_req.status_code != 200:
            raise Exception(f"failed to delete object {id}: {delete_req.json()}")
        else:
            print(f"deleted object {id}")
        deleted += 1

print(f"deleted {deleted} objects")
