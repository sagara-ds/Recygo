"""Exercise only id.recygo.app on an explicitly selected Android device.

Credentials are read from the ignored local demo file and never printed.
Screenshots/dumps are local review artifacts, not claims of production readiness.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("action", choices=["capture", "inspect", "login-resident", "login-collector", "tap", "fill-request", "fill-weights"])
parser.add_argument("--text", default="")
parser.add_argument("--name", default="phone")
args = parser.parse_args()
ADB = Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb.exe"


def adb(*parts):
    result = subprocess.run([str(ADB), "-s", args.serial, *parts], capture_output=True)
    if result.returncode:
        raise RuntimeError("Android command failed; no sensitive arguments logged.")
    return result.stdout


def nodes():
    result = adb("shell", "uiautomator", "dump", "/sdcard/recygo-window.xml")
    if b"dumped to" not in result:
        raise RuntimeError("Android did not return a fresh UI hierarchy; retry when the screen is idle.")
    raw = adb("exec-out", "cat", "/sdcard/recygo-window.xml")
    root = ET.fromstring(raw)
    return list(root.iter("node"))


def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def tap(text):
    for _ in range(4):
        found = [n for n in nodes() if n.get("text") == text or n.get("content-desc") == text]
        if found:
            tap_node(found[0]); time.sleep(1); return
        adb("shell", "input", "swipe", "500", "1800", "500", "700", "400")
    raise RuntimeError("Control not found: " + text)


def input_text(value):
    # Test input and generated passwords contain no shell metacharacters.
    if not re.fullmatch(r"[A-Za-z0-9@._/+ =,-]+", value):
        raise RuntimeError("Unsupported test input")
    adb("shell", "input", "text", value.replace(" ", "%s"))


def capture():
    focus = adb("shell", "dumpsys", "window").decode("utf-8", errors="replace")
    current = next((line for line in focus.splitlines() if "mCurrentFocus=" in line), "")
    if "id.recygo.app" not in current:
        raise RuntimeError("Recygo is not in the foreground; unlock/open the app before capture.")
    out = ROOT / ".impeccable" / "review" / (args.name + ".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(adb("exec-out", "screencap", "-p"))
    print("Screenshot: " + str(out))


if args.action.startswith("login-"):
    role = "Warga" if args.action.endswith("resident") else "Pengepul"
    accounts = json.loads((ROOT / "backend/data/demo-accounts.json").read_text(encoding="utf-8-sig"))
    account = next(a for a in accounts if a["role"] == role)
    fields = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if len(fields) == 1 and fields[0].get("content-desc") in ("Nomor WhatsApp", "Alamat email"):
        # The account flow first asks for the identifier, then the password.
        identifier = ("080000000001" if role == "Warga" else "080000000002") if fields[0].get("content-desc") == "Nomor WhatsApp" else account["email"]
        tap_node(fields[0]); input_text(identifier)
        adb("shell", "input", "keyevent", "4")
        tap("Lanjutkan")
        fields = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    if len(fields) == 1 and fields[0].get("content-desc") == "Password":
        tap_node(fields[0]); input_text(account["password"])
    elif len(fields) == 2:
        tap_node(fields[0]); input_text(account["email"])
        tap_node(fields[1]); input_text(account["password"])
    else:
        raise RuntimeError("Expected Recygo identifier/password sign-in")
    adb("shell", "input", "keyevent", "4")
    tap("Masuk"); time.sleep(2)
    print("Submitted " + role + " demo sign-in; credentials not printed.")
    capture()
elif args.action == "fill-request":
    # Fill the draft through UI; users must still explicitly submit it below.
    fields = [n for n in nodes() if n.get("class") == "android.widget.EditText"]
    for field in fields:
        label = field.get("content-desc", "")
        value = "2.5" if "berat" in label.lower() else "Jalan Demo Recygo 8 RT 01 RW 04" if "Alamat" in label else "Data sintetis untuk uji Recygo"
        tap_node(field); input_text(value); adb("shell", "input", "keyevent", "4")
    print("Filled visible synthetic pickup fields.")
    capture()
elif args.action == "fill-weights":
    print("Default estimated weights retained for actual-weight demo; inspect before confirming.")
    capture()
elif args.action == "tap":
    tap(args.text); capture()
elif args.action == "inspect":
    # Do not print editable text, which can include credentials.
    for n in nodes():
        if n.get("package") == "id.recygo.app" and n.get("class") != "android.widget.EditText":
            value = n.get("text") or n.get("content-desc")
            if value: print(value)
else:
    capture()
