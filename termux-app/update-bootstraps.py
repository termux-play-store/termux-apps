#!/usr/bin/env python3
import hashlib
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

if len(sys.argv) != 2:
    sys.exit("Usage: ./update-bootstraps.py <tag> # where tag is like 2026.01.22-r2")
bootstrap_version = sys.argv[1]

def get_url_sha256(url):
    sha256_hash = hashlib.sha256()
    #headers = {'User-Agent': 'Mozilla/5.0'}
    req = urllib.request.Request(url) #, headers=headers)
    with urllib.request.urlopen(req) as response:
        while True:
            # Read 8KB at a time
            chunk = response.read(8192)
            if not chunk:
                break
            sha256_hash.update(chunk)
    return sha256_hash.hexdigest()

from pathlib import Path
build_gradle_path = Path('build.gradle.kts')
old_txt = build_gradle_path.read_text()

old_txt = re.sub(r'bootstrapVersion = "(.*)"', f'bootstrapVersion = "{bootstrap_version}"', old_txt)

counter = 1
new_txt = ''
start = 0
for m in re.finditer(r'"(.*)" to "(.*)"', old_txt):
    end, newstart = m.span()
    new_txt += old_txt[start:end]

    arch = m.group(1)
    # old_checksum = m.group(2)
    url = f"https://github.com/termux-play-store/termux-packages/releases/download/bootstrap-{bootstrap_version}/bootstrap-{arch}.zip"
    checksum = get_url_sha256(url)

    rep = '"' + m.group(1) + '" to "' + checksum + '"'
    new_txt += rep
    start = newstart
    counter += 1
new_txt += old_txt[start:]

build_gradle_path.write_text(new_txt)

subprocess.run(["git", "diff", "build.gradle.kts"], check=True)
