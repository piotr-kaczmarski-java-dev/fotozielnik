"""Minimal fotozielnik API client for perf scripts: session cookie + CSRF header, like the browser."""
import requests


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.s = requests.Session()

    def _csrf(self):
        if "XSRF-TOKEN" not in self.s.cookies:
            self.s.get(f"{self.base}/api/auth/csrf")
        return {"X-XSRF-TOKEN": self.s.cookies.get("XSRF-TOKEN", "")}

    def get(self, path, **kw):
        return self.s.get(f"{self.base}{path}", **kw)

    def send(self, method, path, json=None, data=None, headers=None):
        h = {**self._csrf(), **(headers or {})}
        return self.s.request(method, f"{self.base}{path}", json=json, data=data, headers=h)

    def login(self, email, password):
        r = self.send("POST", "/api/auth/login", json={"email": email, "password": password})
        r.raise_for_status()
        return self


def ensure_user(base, admin, email, password="perf-user-password"):
    """Creates the user (if missing) with a permanent password and returns (Api signed in, user id)."""
    r = admin.send("POST", "/api/admin/users",
                   json={"email": email, "displayName": email.split("@")[0], "role": "USER", "temporaryPassword": "temporary-1"})
    if r.status_code == 201:
        u = Api(base).login(email, "temporary-1")
        u.send("PUT", "/api/me/password", json={"currentPassword": "temporary-1", "newPassword": password}).raise_for_status()
    elif r.status_code != 409:
        r.raise_for_status()
    u = Api(base).login(email, password)
    return u, u.get("/api/me").json()["id"]
