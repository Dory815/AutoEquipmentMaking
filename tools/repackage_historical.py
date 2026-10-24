"""Relabel verified historical binaries; do not alter their code or other entries."""
import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "client/build/libs"
DEST = ROOT / "release-assets"
RELEASES = [
    ("Alpha 1.0", "1.0.0-alpha.1.0", "auto-equipment-making-client-1.0.0-rc.7.jar", "11cc98a906f4f0ece9f1ce7d8283da9a07b176a1fd6478ffb4b1b6c38c9a720a"),
    ("Alpha 1.2", "1.0.0-alpha.1.2", "auto-equipment-making-client-1.1.0-rc.5.jar", "671266e0caf3411b199a7a62cc926f322682d22547d63f2a95dbeb1e950e9e16"),
    ("Alpha 1.3", "1.0.0-alpha.1.3", "auto-equipment-making-client-1.1.0-rc.6.jar", "7ab97452aec57a1f40ec697f3d78e97d5cfa6998a6bb91636e34d8cba26b1765"),
]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def repackage(label, version, filename, expected):
    source = SOURCE / filename
    source_bytes = source.read_bytes()
    if digest(source_bytes) != expected:
        raise ValueError(f"Unexpected historical artifact: {filename}")
    output = DEST / f"auto-equipment-making-client-{version}.jar"
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(output, "w") as new:
        for entry in old.infolist():
            data = old.read(entry.filename)
            if entry.filename == "fabric.mod.json":
                metadata = json.loads(data)
                metadata["version"] = version
                data = (json.dumps(metadata, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
            new.writestr(entry, data)
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(output) as new:
        if old.namelist() != new.namelist():
            raise ValueError("Archive entry list changed")
        for name in old.namelist():
            if name != "fabric.mod.json" and old.read(name) != new.read(name):
                raise ValueError(f"Unexpected changed entry: {name}")
        if json.loads(new.read("fabric.mod.json"))["version"] != version:
            raise ValueError("Incorrect release metadata")
    return {"name": label, "tag": "v" + version, "historicalFile": filename, "historicalSHA256": expected, "releaseFile": output.name, "releaseSHA256": digest(output.read_bytes())}


if __name__ == "__main__":
    DEST.mkdir(exist_ok=True)
    result = [repackage(*release) for release in RELEASES]
    (DEST / "manifest.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))
