#!/usr/bin/env python3
import argparse
import json
import json_merge_patch
from pathlib import Path
from getpass import getpass

import requests
import os

# ignore https warnings
import urllib3

from cordra_auth import get_auth_token

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

BASEDIR = os.path.dirname(__file__)

# path to schema.json files
SCHEMA_FOLDER = Path(BASEDIR, "schemas")

# patches to apply to all schemas
PATCHES = [Path(BASEDIR, "AuthConfig.mergepatch.json")]

# path to a library jar file
LIBRARY = Path(BASEDIR, "../lib/build/libs/lib.jar")

# Name of the type where the library should be attached to as java payload
LIBRARY_SCHEMA = "Dataset"


def get_schemas(token=get_auth_token(BASE_URL, USER, PASSWORD)):
    """ get a map of schemas present in cordra. maps schema name to schema id """
    headers = {"Authorization": f"Bearer {token}"}
    r = requests.get(BASE_URL + "/search?query=type:Schema", verify=False, headers=headers)
    name_to_id = {}
    for schema in r.json()["results"]:
        name_to_id[schema["content"]["name"]] = schema["id"]
    return name_to_id


def update_schema(type, schema, token=get_auth_token(BASE_URL, USER, PASSWORD), schema_id=None):
    headers = {"Authorization": f"Bearer {token}"}

    # parse body. Can't use requests json parameter because it does not work with multipart
    files = {"content": (None, json.dumps(schema), "application/json")}

    # attach java lib to body if type matches the library schema
    if type == LIBRARY_SCHEMA:
        print(f"attach library {LIBRARY} to schema {type}")
        files["java"] = ("java", open(LIBRARY, "rb"), "application/octet-stream")

    # upsert schema
    if schema_id is None:  # insert
        print(f"Schema not found. Inserting...")
        url = BASE_URL + "/objects/?type=Schema"
        r = requests.post(url, json=schema, files=files, verify=False, headers=headers)
    else:  # update
        print(f"Schema known under id {schema_id}. Updating...")
        url = BASE_URL + "/objects/" + schema_id
        r = requests.put(url, json=schema, files=files, verify=False, headers=headers)

    if r.status_code != 200:
        raise Exception(f"Failed to update Schema for {type} ({r.status_code}): {r.text}")


token = get_auth_token(BASE_URL, USER, PASSWORD)
schemas = get_schemas(token)

print("Found schemas:", os.listdir(SCHEMA_FOLDER))
print("Patches for all schemas:", PATCHES)

for filename in os.listdir(SCHEMA_FOLDER):
    type = filename.split(".")[0]
    if type in schemas:
        schema_id = schemas[type]
    else:
        schema_id = None

    with open(SCHEMA_FOLDER / filename, "r") as f:
        print("Processing ", filename, "...")
        schema = json.load(f)
        
        for patch_file in PATCHES:
            patch = json.load(open(patch_file, "r"))
            schema = json_merge_patch.merge(schema, patch)

        update_schema(type, schema, token, schema_id=schema_id)
