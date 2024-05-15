import requests

def get_auth_token(base_url, user, password):
    body = {
        "grant_type": "password",
        "username": user,
        "password": password
    }
    r = requests.post(base_url + "/auth/token", json=body, verify=False)
    if r.status_code != 200:
        raise Exception(f"failed to get token: {r.json()}")
    else:
        return r.json()["access_token"]
