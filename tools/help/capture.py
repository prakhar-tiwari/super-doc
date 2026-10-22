#!/usr/bin/env python3
"""Takes the help screenshots from the real app on a connected emulator, in English and Hindi.

For every image named in help.json it opens that screen, saves a WebP screenshot to
app/src/main/assets/help/img/<lang>/<image>.webp, and measures where each step's "target"
(an app string name, or "class:<widget class>") is on screen. Boxes go to help/boxes.json.

Before running: a debug build installed, and the AI model file in /sdcard/Download (for the AI pictures).
Usage: python3 tools/help/capture.py [--keep-data] [--only en|hi] [--images home,note,...]
WARNING: without --keep-data this clears the app's data on the emulator.
"""
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/help"
RES = ROOT / "app/src/main/res"
ADB = str(pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb")
PKG = "com.superdoc.app"
MODEL_FILE = "Qwen2.5-0.5B-Instruct_q8.task"
LANGS = {"en": "values", "hi": "values-hi"}


def adb(*args, capture=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout if capture else None


def strings(folder):
    out = {}
    for f in (RES / folder).glob("strings*.xml"):
        for s in ET.parse(f).getroot().iter("string"):
            out[s.get("name")] = "".join(s.itertext()).replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return out


class Screen:
    """Reads the current UI with uiautomator and finds things by app string name."""

    def __init__(self, lang):
        self.s = strings(LANGS[lang])
        self.root = None
        self.parents = {}

    def dump(self):
        for _ in range(3):
            adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
            xml = adb("shell", "cat", "/sdcard/ui.xml", capture=True).decode("utf-8", "replace")
            if "<hierarchy" in xml:
                self.root = ET.fromstring(xml[xml.index("<hierarchy"):])
                self.parents = {c: p for p in self.root.iter() for c in p}
                return
            time.sleep(1)
        raise RuntimeError("uiautomator dump failed")

    def size(self):
        b = bounds(next(self.root.iter("node")))
        return b[2], b[3]

    def node(self, target, fresh=True):
        if fresh:
            self.dump()
        if target.startswith("class:"):
            cls = target[6:]
            return next((n for n in self.root.iter("node") if n.get("class") == cls), None)
        text = self.s.get(target, target)
        nodes = list(self.root.iter("node"))
        for match in (lambda t: t == text, lambda t: t.startswith(text), lambda t: len(text) > 8 and text in t):
            for n in nodes:
                if match(n.get("text", "")) or match(n.get("content-desc", "")):
                    return self.clickable(n)
        return None

    def clickable(self, n):
        """The tappable thing around a label (a button), or the label itself."""
        p = n
        for _ in range(4):
            if p.get("clickable") == "true":
                return p
            p = self.parents.get(p)
            if p is None or p.tag != "node":
                break
        return n

    def has(self, target):
        return self.node(target) is not None

    def tap(self, target, wait=2.0, tries=4):
        for _ in range(tries):
            n = self.node(target)
            if n is not None:
                l, t, r, b = bounds(n)
                adb("shell", "input", "tap", str((l + r) // 2), str((t + b) // 2))
                time.sleep(wait)
                return
            time.sleep(1.5)
        raise RuntimeError(f"not found: {target}")

    def scroll_to(self, target, tries=6):
        """Swipes up until the target is well on screen, or stops moving because the page ends."""
        last = None
        for _ in range(tries):
            n = self.node(target)
            if n is not None:
                _, h = self.size()
                l, t, r, b = bounds(n)
                if t > h * 0.08 and (b < h * 0.8 or (last == (l, t, r, b) and b < h * 0.96)):
                    return
                last = (l, t, r, b)
            adb("shell", "input", "swipe", "540", "1700", "540", "1100", "400")
            time.sleep(1.5)
        raise RuntimeError(f"could not scroll to: {target}")

    def go_home(self):
        for _ in range(6):
            if self.has("home_question"):
                return
            n = self.node("action_home", fresh=False)
            if n is not None:
                l, t, r, b = bounds(n)
                adb("shell", "input", "tap", str((l + r) // 2), str((t + b) // 2))
            else:
                adb("shell", "input", "keyevent", "4")
            time.sleep(2)
        raise RuntimeError("could not get to Home")

    def wait_for(self, target, seconds):
        end = time.time() + seconds
        while time.time() < end:
            if self.has(target):
                return
            time.sleep(4)
        raise RuntimeError(f"timed out waiting for {target}")


def bounds(n):
    return tuple(map(int, re.findall(r"\d+", n.get("bounds"))))


def shoot(path):
    with tempfile.TemporaryDirectory() as tmp:
        png = pathlib.Path(tmp) / "s.png"
        png.write_bytes(adb("exec-out", "screencap", "-p", capture=True))
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["cwebp", "-quiet", "-q", "72", "-resize", "720", "0", str(png), "-o", str(path)], check=True)


# ---- How to reach each picture, starting from Home. -------------------------------------------

def open_practice_note(sc):
    sc.go_home()
    sc.tap("__note__")


def open_practice_letter(sc):
    sc.go_home()
    sc.tap("home_my_documents", wait=3)
    sc.tap("__letter__", wait=3)


def note_lower(sc):
    open_practice_note(sc)
    sc.scroll_to("note_ai_helper")


def ai_menu(sc):
    note_lower(sc)
    sc.tap("note_ai_helper", wait=3)


def ai_result(sc):
    ai_menu(sc)
    sc.tap("ai_fix", wait=5)
    sc.wait_for("ai_keep", 300)
    sc.scroll_to("ai_keep")


def export(sc):
    open_practice_note(sc)
    sc.scroll_to("note_make_document")
    sc.tap("note_make_document", wait=3)


def export_type(sc):
    export(sc)
    sc.scroll_to("export_pdf")


def export_save(sc):
    export(sc)
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1700", "540", "700", "400")
        time.sleep(1.5)


def settings(sc):
    sc.go_home()
    sc.tap("action_settings")


def settings_family(sc):
    settings(sc)
    sc.scroll_to("settings_family_add")


def settings_backup(sc):
    settings(sc)
    sc.scroll_to("backup_restore", tries=14)


RECIPES = {
    "home": lambda sc: sc.go_home(),
    "note": open_practice_note,
    "note_lower": note_lower,
    "scan_intro": lambda sc: (sc.go_home(), sc.tap("home_scan")),
    "viewer": open_practice_letter,
    "library": lambda sc: (sc.go_home(), sc.tap("home_my_documents")),
    "library_bin": lambda sc: (sc.go_home(), sc.tap("home_my_documents"), sc.scroll_to("recycle_bin_open")),
    "export": export,
    "export_type": export_type,
    "export_save": export_save,
    "tools": lambda sc: (sc.go_home(), sc.tap("home_more_tools")),
    "ai_menu": ai_menu,
    "ai_result": ai_result,
    "settings": settings,
    "settings_family": settings_family,
    "settings_backup": settings_backup,
}
# Pictures that leave a dialog or panel open, and how to close it afterwards.
CLOSE = {"scan_intro": "action_cancel", "ai_menu": "ai_close", "ai_result": "ai_undo"}
AI_IMAGES = {"ai_menu", "ai_result"}


def setup_fresh(sc_en):
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    sc_en.wait_for("English", 60)
    time.sleep(3)
    for _ in range(3):
        sc_en.tap("English", wait=4)
        if not sc_en.has("English"):
            break
    sc_en.wait_for("tour_offer_no", 30)
    sc_en.tap("tour_offer_no")
    # The AI model, for the AI pictures.
    settings(sc_en)
    sc_en.scroll_to("settings_ai_open")
    sc_en.tap("settings_ai_open", wait=3)
    sc_en.scroll_to("ai_import")
    sc_en.tap("ai_import", wait=4)
    sc_en.tap(MODEL_FILE, wait=5)
    sc_en.wait_for("ai_import_done", 300)
    sc_en.tap("action_ok")


def add_practice(sc):
    sc.go_home()
    sc.tap("home_help", wait=3)
    sc.tap("help_practice_button", wait=6)
    sc.tap("action_ok")


def switch_language(sc, lang):
    """Sets the app's own language (Android 13+), then restarts it on Home."""
    adb("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "hi-IN" if lang == "hi" else "en-US")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    Screen(lang).wait_for("home_question", 120)


BOXES_FILE = ASSETS / "boxes.json"


def save_boxes(boxes):
    BOXES_FILE.write_text(json.dumps(boxes, indent=1, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")


def capture(lang, images, steps_by_image, boxes, samples_lang=None):
    sc = Screen(lang)
    samples = strings(LANGS[samples_lang or lang])
    sc.s["__note__"] = samples["practice_note_body"].split("\n")[0][:12]
    sc.s["__letter__"] = samples["practice_letter_subject"][:14]
    for image in images:
        print(f"[{lang}] {image}", flush=True)
        RECIPES[image](sc)
        time.sleep(1.5)
        shoot(ASSETS / "img" / lang / f"{image}.webp")
        sc.dump()
        w, h = sc.size()
        for key, target in steps_by_image.get(image, []):
            if target is None:
                continue
            n = sc.node(target, fresh=False)
            if n is None:
                print(f"   ! target {target} not on screen for {key}")
                continue
            l, t, r, b = bounds(n)
            boxes.setdefault(lang, {})[key] = [round(l / w, 4), round(t / h, 4), round(r / w, 4), round(b / h, 4)]
        save_boxes(boxes)
        if image in CLOSE:
            sc.tap(CLOSE[image])


def main():
    keep = "--keep-data" in sys.argv
    only = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    help_json = json.loads((ASSETS / "help.json").read_text(encoding="utf-8"))
    steps_by_image = {}
    for topic in help_json["topics"]:
        for i, step in enumerate(topic["steps"], start=1):
            steps_by_image.setdefault(step["image"], []).append((f"{topic['id']}_{i}", step.get("target")))
    images = list(dict.fromkeys(steps_by_image))
    if "--images" in sys.argv:
        chosen = sys.argv[sys.argv.index("--images") + 1].split(",")
        images = [i for i in images if i in chosen]
    boxes = json.loads(BOXES_FILE.read_text()) if BOXES_FILE.exists() else {}

    en = Screen("en")
    if not keep:
        setup_fresh(en)
    if only in (None, "en"):
        switch_language(en, "en")
        add_practice(en)
        capture("en", images, steps_by_image, boxes)
    if only in (None, "hi"):
        # The AI pictures use the English practice note: the small model only handles English.
        switch_language(en, "hi")
        capture("hi", [i for i in images if i in AI_IMAGES], steps_by_image, boxes, samples_lang="en")
        add_practice(Screen("hi"))
        capture("hi", [i for i in images if i not in AI_IMAGES], steps_by_image, boxes)
    print("done")


if __name__ == "__main__":
    main()
