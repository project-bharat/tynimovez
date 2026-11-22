"""Capture the installed debug app on a disposable CI emulator."""
import datetime
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

package = "com.projectbharat.counterapp"
output = pathlib.Path("screenshots")
output.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args])


def preferences(name, values):
    root = ET.Element("map")
    for key, value in values.items():
        if isinstance(value, bool):
            ET.SubElement(root, "boolean", name=key, value=str(value).lower())
        else:
            ET.SubElement(root, "string", name=key).text = value
    subprocess.run(["adb", "shell", f"run-as {package} sh -c 'cat > shared_prefs/{name}.xml'"],
                   input=ET.tostring(root), check=True)


def find(label):
    labels = []
    for _ in range(12):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
            tree = ET.fromstring(adb("shell", "cat", "/sdcard/window.xml"))
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(1)
            continue
        labels = [n.get("text") for n in tree.iter("node") if n.get("text")]
        for node in tree.iter("node"):
            if node.get("text") == label:
                return list(map(int, re.findall(r"\d+", node.get("bounds"))))
        time.sleep(1)
    print(adb("logcat", "-b", "crash", "-d").decode(), flush=True)
    raise RuntimeError(f"Screen missing {label}. Visible labels: {labels}")


def tap(label):
    x1, y1, x2, y2 = find(label)
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def capture(name):
    (output / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "run-as", package, "mkdir", "-p", "shared_prefs")
preferences("app_preferences", {"english_language": True})
preferences("app_setup", {"permission_intro_seen": True})
preferences("speech_model_setup", {"prefetch_connected_network_v2": True})
preferences("pregnancy_dates", {"method": "LMP", "date_LMP": str(datetime.date.today() - datetime.timedelta(days=192))})
adb("shell", "am", "start", "-W", "-n", f"{package}/.MainActivity")
tap("Start session")
tap("Add movement")
find("1")
capture("Movements")
tap("Timeline")
find("Pregnancy timeline")
capture("Timeline")
tap("Count")
find("Add movement")
adb("shell", "cmd", "uimode", "night", "yes")
adb("shell", "settings", "put", "system", "font_scale", "1.3")
adb("shell", "wm", "size", "720x1600")
adb("shell", "wm", "density", "320")
find("Add movement")
capture("Movements-dark-large-text")
tap("Timeline")
find("Pregnancy timeline")
capture("Timeline-dark-large-text")
