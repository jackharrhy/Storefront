#!/usr/bin/env python3
"""Exercise built Fabric JARs in a new loopback-only world, then restart it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[1]
VERSION = ET.parse(REPO / "pom.xml").findtext("{http://maven.apache.org/POM/4.0.0}version")
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--workspace", type=Path, default=Path(os.environ.get("TMPDIR", ".")))
parser.add_argument("--launcher", type=Path, required=True)
parser.add_argument("--fabric-api", type=Path, required=True)
parser.add_argument("--minecraft", type=Path, required=True)
parser.add_argument("--jar", type=Path, default=REPO / f"fabric/build/libs/storefront-fabric-{VERSION}.jar")
parser.add_argument("--smoke-jar", type=Path, default=REPO / f"fabric/build/libs/storefront-fabric-{VERSION}-smoke.jar")
parser.add_argument("--accept-eula", action="store_true", required=True)
args = parser.parse_args()
args.workspace.mkdir(parents=True, exist_ok=True)
root = Path(tempfile.mkdtemp(prefix="storefront-smoke-", dir=args.workspace)).resolve()
(root / "mods").mkdir()
(root / "config/storefront").mkdir(parents=True)
for source, target in [(args.launcher, "fabric-server-launch.jar"), (args.minecraft, "server.jar"),
                       (args.fabric_api, "mods/fabric-api.jar"), (args.jar, "mods/storefront.jar"),
                       (args.smoke_jar, "mods/smoke.jar")]:
    shutil.copyfile(source, root / target)
(root / "eula.txt").write_text("eula=true\n")
(root / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=25675\nonline-mode=true\nlevel-name=world\nview-distance=2\nsimulation-distance=2\nmax-players=2\n")
(root / "config/storefront/config.yml").write_text("web:\n  host: 127.0.0.1\n  port: 8765\nrefresh:\n  chests-per-tick: 2\n  budget-ms: 2.0\n")
with sqlite3.connect(root / "config/storefront/storefront.db") as database:
    database.execute("CREATE TABLE chest (id INTEGER PRIMARY KEY, owner TEXT NOT NULL, location TEXT NOT NULL, contents TEXT NOT NULL, modified INTEGER NOT NULL, description TEXT NOT NULL)")
    database.execute("INSERT INTO chest VALUES (?, ?, ?, ?, ?, ?)",
                     (42, json.dumps({"uuid": "550e8400-e29b-41d4-a716-446655440000", "name": "Alice"}),
                      "world:0.0:80.0:0.0", "[]", 123456789, '["[storefront]","Legacy listing"]'))
print(f"Test directory: {root}", flush=True)


def run(phase):
    log = root / f"{phase}.log"
    options = ["-Dstorefront.smoke.legacyId=42"] if phase == "lifecycle" else ["-Dstorefront.smoke.restart=true"]
    with log.open("w") as output:
        process = subprocess.Popen(["java", "--enable-native-access=ALL-UNNAMED", "-Xmx1G", *options,
                                    "-jar", "fabric-server-launch.jar", "nogui"], cwd=root,
                                   stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
        assert process.stdin is not None
        try:
            deadline = time.monotonic() + 180
            while process.poll() is None and time.monotonic() < deadline:
                text = log.read_text()
                if "STOREFRONT SMOKE PASS" in text or "STOREFRONT SMOKE FAIL" in text:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    break
                time.sleep(0.5)
            process.wait(timeout=40)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
    text = log.read_text()
    passed = process.returncode == 0 and "STOREFRONT SMOKE PASS" in text and "STOREFRONT SMOKE FAIL" not in text
    print(f"{phase}: {'PASS' if passed else 'FAIL'} ({log})", flush=True)
    if not passed:
        print(text[-6000:])
        raise SystemExit(1)
    return str(log)


logs = [run("lifecycle"), run("restart")]
summary = {"logs": logs, "artifacts": {str(path.resolve()): hashlib.sha256(path.read_bytes()).hexdigest()
                                      for path in [args.jar, args.smoke_jar]}, "result": "PASS"}
(root / "result.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
