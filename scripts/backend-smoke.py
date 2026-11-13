"""Run a synthetic end-to-end account + pickup scenario against the local demo API.

Never prints passwords, OTPs or session tokens. Test records remain in the local
demo database so the team can inspect the result without rewriting history.
"""
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import secrets
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8080"


def call(path, data=None, token=None, key=None, expected=200):
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = Request(BASE + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
    try:
        with urlopen(req, timeout=20) as response:
            status, body = response.status, response.read()
    except HTTPError as error:
        status, body = error.code, error.read()
    assert status == expected, "Unexpected HTTP status on " + path + ": " + str(status)
    return json.loads(body)


def code(challenge):
    item = json.loads((ROOT / "backend/data/outbox" / (challenge + ".json")).read_text())
    assert item["delivery"] == "LOCAL_DEMO_ONLY"
    return item["code"]


assert call("/health")["demo"] is True, "Only run against the local demo backend."
email = "smoke-" + uuid4().hex[:12] + "@recygo.demo"
password = secrets.token_urlsafe(24) + "7a"
phone = "08" + str(secrets.randbelow(10**9)).zfill(9)
registration = {
    "purpose": "REGISTER", "name": "Uji Recygo", "email": email,
    "password": password, "phone": phone, "role": "RESIDENT",
    "address": "Jalan Demo Recygo 12 RT 01 RW 04", "rt": "01", "rw": "04", "consent": True,
}
started = call("/api/auth/start", registration)
assert "code" not in started
session = call("/api/auth/verify", {"challengeId": started["challengeId"], "code": code(started["challengeId"])})
resident = session["token"]
call("/api/auth/verify", {"challengeId": started["challengeId"], "code": code(started["challengeId"])}, expected=400)
assert call("/api/profile", token=resident)["profile"]["verification"] == "DEMO_ONLY"
call("/api/auth/login", {"identifier": phone, "password": password})
print("PASS: registration, local OTP, replay prevention, encrypted profile and phone sign-in.")

date = (datetime.now(timezone(timedelta(hours=7))).date() + timedelta(days=1)).isoformat()
payload = {"address": registration["address"], "date": date, "slot": "08.00–10.00", "notes": "Data sintetis untuk pengujian", "items": [{"materialId": "plastic", "grams": 3000}, {"materialId": "paper", "grams": 2000}]}
key = str(uuid4())
order = call("/api/orders", payload, resident, key)["order"]
assert call("/api/orders", payload, resident, key)["order"]["id"] == order["id"]
assert call("/api/orders", token=resident)["orders"][0]["id"] == order["id"]
print("PASS: persisted pickup request and idempotent retry.")

accounts = json.loads((ROOT / "backend/data/demo-accounts.json").read_text(encoding="utf-8-sig"))
collector_account = next(a for a in accounts if a["role"] == "Pengepul")
collector = call("/api/auth/login", {"email": collector_account["email"], "password": collector_account["password"]})["token"]
for action in ["accept", "start", "arrive"]:
    call("/api/orders/" + order["id"] + "/" + action, {}, collector)
weighed = call("/api/orders/" + order["id"] + "/weigh", {"items": [{"materialId": "plastic", "grams": 2500}, {"materialId": "paper", "grams": 1000}]}, collector)["order"]
assert weighed["status"] == "AWAITING_CONFIRMATION" and weighed["points"] == 1550
done = call("/api/orders/" + order["id"] + "/confirm", {}, resident)["order"]
assert done["status"] == "COMPLETED"
print("PASS: collector acceptance, journey, arrival, actual weighing and resident confirmation.")

# Keep this disposable account available for later UI/reset checks. Reset expiry,
# replay and session revocation are covered by AccountsTest; this script does not
# change seeded demo-account passwords or bypass cooldown timestamps.
saved = {"email": email, "phone": phone, "password": password, "orderId": order["id"]}
(ROOT / "backend/data/smoke-account.json").write_text(json.dumps(saved, indent=2), encoding="utf-8")
call("/api/auth/logout", {}, resident)
call("/api/orders", token=resident, expected=401)
call("/api/auth/logout", {}, collector)
print("PASS: logout invalidation. Synthetic account details saved only in ignored backend/data.")
