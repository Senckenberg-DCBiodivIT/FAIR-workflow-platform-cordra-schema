#!/usr/bin/env python3

from pathlib import Path
from cordra_auth import get_auth_token
import requests

# ignore https warning
import urllib3
urllib3.disable_warnings()

USER = "admin"
PASSWORD = "password"
BASE_URL = "https://localhost:8443"
SCHEMA_FOLDER = Path("schemas")
LIBRARY = "../lib/build/libs/lib.jar"
LIBRARY_SCHEMA = "Dataset"

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
