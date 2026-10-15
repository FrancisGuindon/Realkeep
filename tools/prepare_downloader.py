#!/usr/bin/env python3
"""Fetch the pinned official yt-dlp bundle and validate it before packaging."""
import hashlib
from pathlib import Path
import sys
import urllib.request

VERSION = '2026.08.19'
SHA256 = '1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6'

def prepare(output):
    if output.is_file() and hashlib.sha256(output.read_bytes()).hexdigest() == SHA256:
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.download')
    try:
        urllib.request.urlretrieve(f'https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp', temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
            raise ValueError('yt-dlp checksum mismatch')
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    print('Verified yt-dlp', VERSION)

if __name__ == '__main__':
    prepare(Path(sys.argv[1]))
