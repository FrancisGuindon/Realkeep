#!/usr/bin/env python3
"""Scan ELF LOAD alignment, recursively including disguised ZIP payloads."""
import argparse
import io
from pathlib import Path
import struct
import zipfile

def load_alignments(data):
    if not data.startswith(b'\x7fELF'): return None
    if data[5] != 1: raise ValueError('Expected little-endian ELF')
    if data[4] == 2:
        offset=struct.unpack_from('<Q',data,32)[0]
        size,count=struct.unpack_from('<HH',data,54)
        align_offset,align_type=48,'<Q'
    elif data[4] == 1:
        offset=struct.unpack_from('<I',data,28)[0]
        size,count=struct.unpack_from('<HH',data,42)
        align_offset,align_type=28,'<I'
    else: raise ValueError('Unknown ELF class')
    return [struct.unpack_from(align_type,data,offset+i*size+align_offset)[0]
            for i in range(count) if struct.unpack_from('<I',data,offset+i*size)[0]==1]

def scan(data,name,results):
    values=load_alignments(data)
    if values is not None: results.append((name,values))
    elif data.startswith(b'PK\x03\x04'):
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for member in z.infolist():
                if not member.is_dir(): scan(z.read(member),name+'!'+member.filename,results)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('artifact',type=Path);args=parser.parse_args()
    results=[];scan(args.artifact.read_bytes(),str(args.artifact),results)
    failed=[(name,values) for name,values in results if not values or min(values)<16384]
    for name,values in failed: print('FAIL',name,values)
    print(f'{len(results)} ELF files checked; {len(failed)} alignment failures')
    if not results or failed: raise SystemExit(1)
