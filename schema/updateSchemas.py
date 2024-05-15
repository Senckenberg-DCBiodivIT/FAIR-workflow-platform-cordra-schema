import json

import requests
import os
from pathlib import Path

# ignore https warning
import urllib3

from cordra_auth import get_auth_token

urllib3.disable_warnings()

USER = "admin"
PASSWORD = "password"
BASE_URL = "https://localhost:8443"
SCHEMA_FOLDER = Path("schemas")
LIBRARY = "../lib/build/libs/lib.jar"
LIBRARY_SCHEMA = "Dataset"

def get_schemas(token):
    headers = {"Authorization": f"Bearer {token}"}
    r = requests.get(BASE_URL + "/search?query=type:Schema", verify=False, headers=headers)
    name_to_id = {}
    for schema in r.json()["results"]:
        name_to_id[schema["content"]["name"]] = schema["id"]
    return name_to_id


def update_schema(type, schema, token, schema_id=None):
    print(f"processing {type}: ", end="")
    headers = {"Authorization": f"Bearer {token}"}

    # parse body. Can't use requests json parameter because it does not work with multipart
    files = {"content": (None, json.dumps(schema), "application/json")}

    # attach java lib to body if type matches the library schema
    if type == LIBRARY_SCHEMA:
        print(f"attach library to schema {type}")
        files["java"] = ("java", open(LIBRARY, "rb"), "application/octet-stream")

    # upsert schema
    if schema_id is None:  # insert
        print(f"insert new schema")
        url = BASE_URL + "/objects/?type=Schema"
        r = requests.post(url, json=schema, files=files, verify=False, headers=headers)
    else:  # update
        print(f"update schema ({schema_id})")
        url = BASE_URL + "/objects/" + schema_id
        r = requests.put(url, json=schema, files=files, verify=False, headers=headers)

    if r.status_code != 200:
        raise Exception(f"failed to update schema for {type}: {r.json()}")


token = get_auth_token(BASE_URL, USER, PASSWORD)
schemas = get_schemas(token)

for filename in os.listdir(SCHEMA_FOLDER):
    type = filename.split(".")[0]
    if type in schemas:
        schema_id = schemas[type]
    else:
        schema_id = None

    with open(SCHEMA_FOLDER / filename, "r") as f:
        print(filename)
        schema = json.load(f)
    update_schema(type, schema, token, schema_id=schema_id)
