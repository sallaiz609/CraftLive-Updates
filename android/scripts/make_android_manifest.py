#!/usr/bin/env python3
import hashlib
import json
import pathlib
import sys


def main() -> int:
    if len(sys.argv) != 7:
        print("usage: make_android_manifest.py VERSION_CODE VERSION_NAME APK APK_URL MANDATORY OUTPUT")
        return 2

    version_code = int(sys.argv[1])
    version_name = sys.argv[2]
    apk_path = pathlib.Path(sys.argv[3])
    apk_url = sys.argv[4]
    mandatory = sys.argv[5].lower() in {"1", "true", "yes"}
    output = pathlib.Path(sys.argv[6])

    project_root = pathlib.Path(__file__).resolve().parent.parent
    notes_hu = (project_root / "release-notes" / "android-hu.txt").read_text(encoding="utf-8").strip()
    notes_en = (project_root / "release-notes" / "android-en.txt").read_text(encoding="utf-8").strip()
    digest = hashlib.sha256(apk_path.read_bytes()).hexdigest()

    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": apk_url,
        "sha256": digest,
        "notesHu": notes_hu,
        "notesEn": notes_en,
        "mandatory": mandatory,
    }
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
