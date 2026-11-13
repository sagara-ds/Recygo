"""UI smoke checks on the selected physical phone using synthetic local accounts.

Reads OTPs only from the ignored local demo inbox. Never logs credentials/codes.
Does not clear app data, alter device settings, or operate outside Recygo.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import xml.etree.ElementTree as ET
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("stage", choices=["onboarding", "resident", "collector", "reset", "reset-retry", "profile", "pickup", "font", "visual", "mask", "verdict"])
args = parser.parse_args()
ADB = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe")
OUT = ROOT / ".impeccable/review"
OUT.mkdir(parents=True, exist_ok=True)
ACCOUNT_FILE = ROOT / "backend/data/device-accounts.json"
accounts = json.loads(ACCOUNT_FILE.read_text()) if ACCOUNT_FILE.exists() else {}


def adb(*parts):
    result = subprocess.run([ADB, "-s", args.serial, *parts], capture_output=True)
    if result.returncode:
        # Never include command arguments: input commands can contain passwords.
        raise RuntimeError("Android command failed; no sensitive arguments logged.")
    return result.stdout


def own_app():
    window = adb("shell", "dumpsys", "window").decode(errors="replace")
    assert "id.recygo.app" in next((v for v in window.splitlines() if "mCurrentFocus=" in v), ""), "Open and unlock Recygo first."


def nodes():
    own_app()
    for _ in range(8):
        try:
            result = adb("shell", "uiautomator", "dump", "/sdcard/recygo-account-check.xml")
        except RuntimeError:
            result = b""
        if b"dumped to" in result:
            break
        time.sleep(1)
    else:
        raise RuntimeError("Android did not return a fresh UI hierarchy; no stale file was used.")
    return list(ET.fromstring(adb("exec-out", "cat", "/sdcard/recygo-account-check.xml")).iter("node"))


def bounds(node):
    return list(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def find(label, field=False):
    for attempt in range(7):
        for node in nodes():
            if field and node.get("class") != "android.widget.EditText":
                continue
            if label.casefold() not in ((node.get("text") or "").casefold(), (node.get("content-desc") or "").casefold()):
                continue
            b = bounds(node)
            if len(b) == 4 and b[2] > b[0] and b[3] > b[1] and b[3] <= 2400:
                return node
        # Search from the top, then down; do not scroll the OS or another app.
        adb("shell", "input", "swipe", "500", "700" if attempt == 0 else "1900", "500", "1900" if attempt == 0 else "600", "250")
    raise RuntimeError("Recygo control not found: " + label)


def press(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.25)


def tap(label):
    press(find(label))
    time.sleep(.4)


def back():
    own_app()
    adb("shell", "input", "keyevent", "4")
    time.sleep(.4)


def fill(label, value):
    assert re.fullmatch(r"[A-Za-z0-9@._/+ =,-]+", value), "Unsupported synthetic input."
    node = find(label, True)
    press(node)
    if node.get("text"):
        adb("shell", "input", "keyevent", "123")
        adb("shell", "input", "keyevent", *(["67"] * min(150, len(node.get("text", "")))))
    adb("shell", "input", "text", value.replace(" ", "%s"))
    back()  # Hide the keyboard only.


def wait(label):
    for _ in range(8):
        if any(label in (n.get("text"), n.get("content-desc")) for n in nodes()):
            return
        time.sleep(.8)
    raise RuntimeError("Expected Recygo screen: " + label)


def capture(name):
    own_app()
    for _ in range(3):
        adb("shell", "input", "swipe", "500", "600", "500", "1900", "200")
    time.sleep(.3)
    for node in nodes():
        if node.get("class") == "android.widget.EditText":
            label = node.get("content-desc", "")
            if "password" in label.lower() and node.get("password") != "true":
                raise RuntimeError("Capture blocked: password field is not masked.")
            if "password" in label.lower() and node.get("text") and not all(c in "•●*·" for c in node.get("text")):
                hints = ("Password akunmu", "Password baru", "Ulangi password baru", "Minimal 8 karakter", "Ulangi password")
                if node.get("text") not in hints:
                    raise RuntimeError("Capture blocked: a password field contains readable text.")
            if label.startswith("Digit OTP") and node.get("text"):
                raise RuntimeError("Capture blocked: OTP cells are populated.")
    (OUT / ("device-" + name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))


def save():
    ACCOUNT_FILE.write_text(json.dumps(accounts), encoding="utf-8")


def logout():
    if not any(n.get("text") == "Keluar dari akun?" for n in nodes()):
        tap("Profil"); tap("Keluar")
    tap("Keluar")
    wait("Temukan akunmu")


def latest_code(account, purpose):
    files = sorted((ROOT / "backend/data/outbox").glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    for file in files:
        item = json.loads(file.read_text())
        if item.get("email") == account["email"] and item.get("purpose") == purpose:
            assert item["delivery"] == "LOCAL_DEMO_ONLY"
            return item["code"]
    raise RuntimeError("Local demo OTP not found for synthetic account.")


def verify(account, purpose):
    # Android's dump waits for UI idle; allow the once-per-second OTP countdown
    # to finish rather than treating a stale dump as the current screen.
    time.sleep(46)
    wait("Masukkan kode OTP")
    capture("otp-" + purpose.lower())  # Empty cells; OTPs never enter captures.
    correct = latest_code(account, purpose)
    if args.stage == "reset-retry":
        wrong = str((int(correct) + 1) % 1_000_000).zfill(6)
        fill("Digit OTP 1", wrong); tap("Verifikasi"); wait("Kode OTP tidak sesuai.")
        print("PASS UI: incorrect OTP shows a recoverable error.", flush=True)
    fill("Digit OTP 1", correct)
    tap("Verifikasi")


def new_account(role):
    account = {"name": "Uji " + ("Warga" if role == "RESIDENT" else "Pengepul"),
               "email": "device-" + secrets.token_hex(5) + "@recygo.demo",
               "phone": "08" + str(secrets.randbelow(10**10)).zfill(10),
               "password": secrets.token_hex(12) + "Aa7"}
    accounts[role] = account
    save()
    return account


def login(account):
    wait("Temukan akunmu")
    fill("Nomor WhatsApp", account["phone"]); tap("Lanjutkan")
    fill("Password", account["password"]); tap("Masuk"); wait("Beranda")


def api(path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request("http://127.0.0.1:8080" + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
    with urlopen(request, timeout=20) as response:
        return json.load(response)


if args.stage == "onboarding":
    logout(); back(); back()
    wait("Masuk ke RecyGo"); capture("welcome")
    tap("Masuk ke RecyGo"); wait("Akses Lokasi"); capture("location")
    tap("Nanti Saja"); wait("Notifikasi"); capture("notification")
    tap("Nanti Saja"); wait("Masuk Sebagai apa?"); capture("roles")
    print("PASS UI: welcome, optional location/notification and role selection.", flush=True)
elif args.stage in ("resident", "collector"):
    role = "RESIDENT" if args.stage == "resident" else "COLLECTOR"
    if role == "COLLECTOR":
        logout(); back()
    wait("Masuk Sebagai apa?")
    tap("Daftar sebagai " + ("Warga" if role == "RESIDENT" else "Pengepul")); tap("Lanjutkan")
    account = new_account(role)
    capture(args.stage + "-registration-1")
    fill("Nama Lengkap", account["name"])
    if role == "COLLECTOR":
        fill("NIK contoh", "0000000000000001"); tap("Lanjut")
        capture("collector-registration-2")
    fill("No WhatsApp", account["phone"])
    fill("Alamat email", account["email"])
    fill("Password", account["password"])
    fill("Konfirmasi password", account["password"])
    tap("Lanjut")
    capture(args.stage + "-registration-address")
    fill("Alamat penjemputan" if role == "RESIDENT" else "Alamat", "Jalan Demo Recygo 21 RT 01 RW 04")
    if role == "RESIDENT":
        fill("RT", "01"); fill("RW", "04")
    tap("Setuju penggunaan data demo")
    tap("Buat akun warga" if role == "RESIDENT" else "Buat akun pengepul")
    verify(account, "REGISTER")
    wait("Beranda"); capture(args.stage + "-registered")
    print("PASS UI: " + args.stage + " registration, local OTP and authenticated dashboard.", flush=True)
elif args.stage in ("reset", "reset-retry"):
    account = accounts["RESIDENT"]
    visible = [n.get("text") for n in nodes()]
    if "Masukkan kode OTP" in visible or "Verifikasi password" in visible:
        back()  # Resume a previously interrupted synthetic reset, with a new OTP.
    elif "Kirim kode OTP" in visible:
        pass
    elif "Temukan akunmu" in visible:
        tap("Lupa password?")
    else:
        logout(); tap("Lupa password?")
    fill("Alamat email", account["email"]); tap("Kirim kode OTP")
    verify(account, "RESET")
    wait("Verifikasi password"); capture("new-password")
    account["password"] = secrets.token_hex(12) + "Aa8"
    fill("Password baru", account["password"])
    fill("Konfirmasi password", account["password"])
    tap("Simpan password"); wait("Password berhasil diubah"); save(); capture("reset-done")
    tap("Login ulang"); fill("Nomor WhatsApp", account["phone"]); tap("Lanjutkan")
    fill("Password", account["password"]); tap("Masuk")
    wait("Beranda"); capture("reset-login")
    print("PASS UI: local reset OTP, new password, phone/password login.", flush=True)
elif args.stage == "profile":
    tap("Profil"); capture("profile-before")
    tap("Edit Profil")
    fill("Nama Lengkap", "Uji Warga Recygo")
    tap("Simpan perubahan"); wait("Edit Profil"); capture("profile-updated")
    assert any(n.get("text") == "Uji Warga Recygo" for n in nodes())
    print("PASS UI: profile updated through the backend.", flush=True)
elif args.stage == "pickup":
    resident = accounts["RESIDENT"]
    collector = accounts["COLLECTOR"]
    tap("Beranda"); tap("Setor sampah")
    tap("Botol Plastik, belum dipilih"); tap("Kertas, belum dipilih"); capture("selection")
    tap("Lanjut")
    fill("Perkiraan berat Botol Plastik (kg)", "3.0")
    fill("Perkiraan berat Kertas (kg)", "2.0")
    fill("Alamat penjemputan", "Jalan Demo Recygo 21 RT 01 RW 04")
    fill("Catatan (opsional)", "Uji penjemputan sintetis Recygo")
    tap("Ajukan penjemputan"); wait("Diajukan"); capture("pickup-requested")
    token = api("/api/auth/login", {"identifier": resident["phone"], "password": resident["password"]})["token"]
    order = api("/api/orders", token=token)["orders"][0]
    resident["orderId"] = order["id"]; save()
    short = order["id"][:8].upper()
    print("PASS UI: waste selection and persisted pickup submission.", flush=True)
    logout(); login(collector); capture("collector-home"); tap("Pesanan")
    tap("Pesanan " + short + ", Diajukan")
    tap("Terima pesanan"); wait("Dijadwalkan")
    tap("Mulai perjalanan"); wait("Dijemput"); capture("pickup-journey")
    tap("Konfirmasi tiba"); wait("Kamu sudah sampai?"); capture("arrival")
    tap("Mulai timbang sampah"); wait("Masukkan berat riil per jenis sampah"); capture("weighing")
    tap("Tambah berat Botol Plastik 0,1 kg"); tap("Kurangi berat Botol Plastik 0,1 kg")
    fill("Berat aktual Botol Plastik dalam kg", "2.5")
    fill("Berat aktual Kertas dalam kg", "1.0")
    tap("Konfirmasi berat"); wait("Ringkasan transaksi"); capture("collector-summary")
    print("PASS UI: collector acceptance, journey, arrival and actual weighing.", flush=True)
    logout(); login(resident); tap("Pesanan")
    tap("Pesanan " + short + ", Ditimbang"); tap("Periksa ringkasan transaksi")
    capture("resident-summary"); tap("Selesaikan transaksi"); tap("Konfirmasi")
    wait("Kembali ke riwayat"); capture("completed-summary")
    tap("Kembali ke riwayat"); wait("Riwayat pesanan"); capture("history")
    done = next(o for o in api("/api/orders", token=token)["orders"] if o["id"] == order["id"])
    assert done["status"] == "COMPLETED" and done["points"] == 1550
    own_app(); adb("shell", "am", "force-stop", "id.recygo.app")
    adb("shell", "am", "start", "-n", "id.recygo.app/.MainActivity")
    time.sleep(3); wait("Beranda"); capture("persisted-home")
    print("PASS UI: resident confirmation, history, 1550 demo points and session after relaunch.", flush=True)
elif args.stage == "font":
    original_font = adb("shell", "settings", "get", "system", "font_scale").decode().strip()
    original_night = adb("shell", "cmd", "uimode", "night").decode().strip().split(":")[-1].strip()
    assert original_night in ("auto", "yes", "no", "custom"), "Cannot safely restore the device theme."
    (ROOT / "backend/data/device-display-check.json").write_text(json.dumps({"font": original_font, "night": original_night}))
    try:
        tap("Beranda")
        adb("shell", "cmd", "uimode", "night", "yes")
        adb("shell", "settings", "put", "system", "font_scale", "1.3")
        adb("shell", "am", "start", "-n", "id.recygo.app/.MainActivity")
        time.sleep(3); wait("Beranda"); capture("home-large-font")
        tap("Setor sampah"); capture("selection-large-font"); back()
        tap("Profil"); capture("profile-large-font")
        logout(); capture("find-large-font"); back(); capture("roles-large-font")
        tap("Daftar sebagai Warga"); tap("Lanjutkan"); capture("registration-large-font"); back()
    finally:
        if original_font == "null":
            adb("shell", "settings", "delete", "system", "font_scale")
        else:
            adb("shell", "settings", "put", "system", "font_scale", original_font)
        adb("shell", "cmd", "uimode", "night", original_night)
        print("Restored original device font scale and night setting.", flush=True)
    time.sleep(3); tap("Masuk"); login(accounts["RESIDENT"])
    print("Captured native 1.3 font/night-mode checks; inspect images before claiming a visual pass.", flush=True)
elif args.stage == "visual":
    account = accounts["RESIDENT"]
    tap("Beranda"); capture("resident-registered")
    tap("Profil"); capture("profile-updated"); logout(); back()
    capture("roles"); tap("Daftar sebagai Warga"); capture("roles-selected")
    selected = find("Daftar sebagai Warga")
    assert selected.get("checked") == "true", "Role selection must be exposed to accessibility."
    tap("Lanjutkan"); capture("resident-registration-1")
    tap("Ambil selfie"); tap("Gunakan gambar demo")
    fill("Nama Lengkap", account["name"]); fill("No WhatsApp", account["phone"])
    fill("Alamat email", account["email"]); fill("Password", account["password"])
    fill("Konfirmasi password", account["password"]); tap("Lanjut")
    capture("resident-registration-address"); back(); back()
    tap("Daftar sebagai Pengepul"); tap("Lanjutkan"); capture("collector-registration-1")
    fill("NIK contoh", "0000000000000001"); tap("Lanjut"); capture("collector-registration-2")
    tap("Lanjut"); capture("collector-registration-address")
    assert find("Motor").get("checked") == "true"
    assert find("Plastik").get("checked") == "true"
    print("PASS UI: demo photo, role/vehicle/material accessibility selection states.", flush=True)
    back(); back(); back(); back(); capture("welcome")
    tap("Masuk ke RecyGo"); capture("location"); tap("Nanti Saja")
    capture("notification"); tap("Nanti Saja"); tap("Masuk"); capture("find")
    login(account)
    print("Captured updated account/profile screens at the original font setting.", flush=True)
elif args.stage == "mask":
    logout(); account = accounts["RESIDENT"]
    fill("Nomor WhatsApp", account["phone"]); tap("Lanjutkan")
    fill("Password", account["password"])
    assert find("Password", True).get("password") == "true"
    capture("password-masked")
    tap("Masuk"); wait("Beranda")
    print("PASS UI: populated password remains masked and sign-in succeeds.", flush=True)
elif args.stage == "verdict":
    account = accounts["RESIDENT"]
    tap("Profil"); capture("profile-updated"); logout(); back(); back(); capture("welcome")
    tap("Masuk ke RecyGo"); tap("Nanti Saja"); tap("Nanti Saja")
    tap("Daftar sebagai Warga"); tap("Lanjutkan"); capture("resident-registration-1"); back()
    tap("Daftar sebagai Pengepul"); tap("Lanjutkan")
    fill("Nama Lengkap", "Uji Pengepul"); fill("NIK contoh", "0000000000000001")
    tap("Lanjut"); capture("collector-registration-2"); back(); back(); tap("Masuk")
    fill("Nomor WhatsApp", account["phone"]); tap("Lanjutkan"); fill("Password", account["password"])
    assert find("Password", True).get("password") == "true"
    capture("password-masked"); tap("Masuk"); wait("Beranda")
    print("PASS UI: masked populated password and final default-font fix captures.", flush=True)
    original_font = adb("shell", "settings", "get", "system", "font_scale").decode().strip()
    try:
        adb("shell", "settings", "put", "system", "font_scale", "1.3")
        adb("shell", "am", "start", "-n", "id.recygo.app/.MainActivity")
        time.sleep(3); wait("Beranda"); tap("Profil"); capture("profile-large-font")
    finally:
        adb("shell", "settings", "put", "system", "font_scale", original_font)
        print("Restored original font scale after final profile capture.", flush=True)
    time.sleep(3); tap("Beranda")
