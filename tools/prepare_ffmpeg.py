#!/usr/bin/env python3
"""Patch the pinned upstream FFmpeg AAR with validated rebuilt libwebp files."""
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import zipfile
from check_native_alignment import load_alignments

ABIS={'arm64-v8a':183,'x86_64':62}
NAMES={'libsharpyuv.so','libwebp.so','libwebpdecoder.so','libwebpdemux.so','libwebpmux.so'}

def prepare(source,output,overrides):
    checksums=json.loads((overrides/'checksums.json').read_text());blobs={}
    for abi,machine in ABIS.items():
        for name in NAMES:
            key=abi+'/'+name;data=(overrides/key).read_bytes()
            if hashlib.sha256(data).hexdigest()!=checksums[key]: raise ValueError('Checksum mismatch: '+key)
            if data[:6]!=b'\x7fELF\x02\x01' or struct.unpack_from('<H',data,18)[0]!=machine: raise ValueError('Wrong architecture: '+key)
            if not load_alignments(data) or min(load_alignments(data))<16384: raise ValueError('Not 16 KB aligned: '+key)
            blobs[key]=data
    output.parent.mkdir(parents=True,exist_ok=True);replaced=set()
    with zipfile.ZipFile(source) as z,zipfile.ZipFile(output,'w') as target:
        for entry in z.infolist():
            parts=entry.filename.split('/')
            if parts[0]=='jni' and len(parts)>1 and parts[1] not in ABIS: continue
            data=z.read(entry)
            if parts[0]=='jni' and parts[-1]=='libffmpeg.zip.so':
                abi=parts[1];buffer=io.BytesIO()
                with zipfile.ZipFile(io.BytesIO(data)) as payload,zipfile.ZipFile(buffer,'w') as patched:
                    for member in payload.infolist():
                        content=payload.read(member)
                        if member.filename.startswith('usr/lib/') and Path(member.filename).name in NAMES:
                            key=abi+'/'+Path(member.filename).name;content=blobs[key];replaced.add(key)
                        patched.writestr(member,content)
                data=buffer.getvalue()
            target.writestr(entry,data)
    if replaced!=set(blobs):
        output.unlink(missing_ok=True);raise ValueError('Unexpected upstream payload layout: '+repr(sorted(replaced)))
    print('Prepared FFmpeg AAR:',len(replaced),'validated replacements')

if __name__=='__main__': prepare(*(Path(arg) for arg in sys.argv[1:]))
