#!/usr/bin/env python3
"""Records the help narration with macOS voices (Lekha for Hindi, Rishi for Indian English).

Usage: python3 tools/help/make_audio.py [--force]
Writes app/src/main/assets/help/audio/{en,hi}/<topic>_<step>.m4a. Run again after changing help.json.
Clips whose text did not change are kept unless --force is given (tools/help/recorded/ keeps the text of each clip).
"""
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/help"
RES = ROOT / "app/src/main/res"
VOICES = {"en": ("Rishi", 160), "hi": ("Lekha", 150)}  # words per minute: a little slower than normal
# Words the other language's voice can't read well.
SPOKEN = {"en": {"हिन्दी": "Hindi"}, "hi": {}}


def strings(folder: str) -> dict:
    out = {}
    for f in (RES / folder).glob("strings*.xml"):
        for s in ET.parse(f).getroot().iter("string"):
            out[s.get("name")] = "".join(s.itertext()).replace("\\'", "'").replace('\\"', '"')
    return out


def fill(text: str, table: dict) -> str:
    return re.sub(r"\{([a-z0-9_]+)\}", lambda m: table.get(m.group(1), m.group(0)), text)


def main() -> None:
    force = "--force" in sys.argv
    help_json = json.loads((ASSETS / "help.json").read_text(encoding="utf-8"))
    tables = {"en": strings("values"), "hi": strings("values-hi")}
    made = kept = 0
    for lang, (voice, rate) in VOICES.items():
        out_dir = ASSETS / "audio" / lang
        text_dir = ROOT / "tools/help/recorded" / lang
        out_dir.mkdir(parents=True, exist_ok=True)
        text_dir.mkdir(parents=True, exist_ok=True)
        wanted = set()
        for topic in help_json["topics"]:
            for i, step in enumerate(topic["steps"], start=1):
                text = fill(step["text"][lang], tables[lang])
                for word, spoken in SPOKEN[lang].items():
                    text = text.replace(word, spoken)
                name = f"{topic['id']}_{i}"
                wanted.add(name)
                clip, note = out_dir / f"{name}.m4a", text_dir / f"{name}.txt"
                if not force and clip.exists() and note.exists() and note.read_text(encoding="utf-8") == text:
                    kept += 1
                    continue
                with tempfile.TemporaryDirectory() as tmp:
                    aiff = pathlib.Path(tmp) / "clip.aiff"
                    subprocess.run(["say", "-v", voice, "-r", str(rate), "-o", str(aiff), text], check=True)
                    subprocess.run(["afconvert", "-f", "m4af", "-d", "aac", "-b", "32000", "-c", "1", str(aiff), str(clip)], check=True)
                note.write_text(text, encoding="utf-8")
                made += 1
        for folder in (out_dir, text_dir):
            for old in folder.iterdir():
                if old.stem not in wanted:
                    old.unlink()
    print(f"recorded {made}, unchanged {kept}")


if __name__ == "__main__":
    main()
