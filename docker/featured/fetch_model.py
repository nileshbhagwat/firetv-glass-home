"""Downloads a file and checks its SHA-256 before keeping it: fetch_model.py URL SHA256 DEST."""
import hashlib
import os
import sys
import urllib.request

url, expected, dest = sys.argv[1], sys.argv[2].lower(), sys.argv[3]
os.makedirs(os.path.dirname(dest), exist_ok=True)
tmp = dest + ".part"
digest = hashlib.sha256()
with urllib.request.urlopen(url, timeout=120) as response, open(tmp, "wb") as out:
    while True:
        chunk = response.read(1 << 20)
        if not chunk:
            break
        digest.update(chunk)
        out.write(chunk)
if digest.hexdigest() != expected:
    os.remove(tmp)
    sys.exit(f"Checksum mismatch for {url}: got {digest.hexdigest()}")
os.replace(tmp, dest)
print(f"Saved {dest} ({os.path.getsize(dest)} bytes, sha256 verified)")
