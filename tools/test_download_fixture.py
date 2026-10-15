#!/usr/bin/env python3
"""Offline adaptive merge and unusable-FFmpeg checks with the app's options."""
import functools
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import threading
ROOT=Path(__file__).resolve().parent.parent

def constant(name):
    source=(ROOT/'app/src/main/java/ca/reelkeep/app/DownloadOptions.java').read_text()
    match=re.search(r'\b'+name+r'\s*=\s*("(?:[^"\\]|\\.)*")\s*;',source)
    if not match:raise ValueError('Cannot read Java option '+name)
    return json.loads(match.group(1))

def run(*args):
    result=subprocess.run(args,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return result

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self,*args):pass

def test(downloader):
    ffmpeg,ffprobe=shutil.which('ffmpeg'),shutil.which('ffprobe')
    if not ffmpeg or not ffprobe:raise RuntimeError('Install host FFmpeg')
    with tempfile.TemporaryDirectory(prefix='reelkeep-fixture-') as temporary:
        root=Path(temporary)
        run(ffmpeg,'-hide_banner','-loglevel','error','-f','lavfi','-i','testsrc=size=256x144:rate=10','-t','1','-pix_fmt','yuv420p','-c:v','libx264',str(root/'video.mp4'))
        run(ffmpeg,'-hide_banner','-loglevel','error','-f','lavfi','-i','sine=frequency=1000:sample_rate=44100','-t','1','-c:a','aac',str(root/'audio.m4a'))
        run(ffmpeg,'-hide_banner','-loglevel','error','-i',str(root/'video.mp4'),'-i',str(root/'audio.m4a'),'-c','copy',str(root/'combined.mp4'))
        handler=functools.partial(QuietHandler,directory=str(root))
        with http.server.ThreadingHTTPServer(('127.0.0.1',0),handler) as server:
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            try:
                base=f'http://127.0.0.1:{server.server_port}/'
                formats=[dict(format_id='137',url=base+'video.mp4',ext='mp4',vcodec='avc1.64000b',acodec='none',height=144,width=256,fps=10),dict(format_id='140',url=base+'audio.m4a',ext='m4a',vcodec='none',acodec='mp4a.40.2',abr=128),dict(format_id='18',url=base+'combined.mp4',ext='mp4',vcodec='avc1.64000b',acodec='mp4a.40.2',height=96,width=170,fps=10)]
                info=root/'info.json';info.write_text(json.dumps(dict(id='fixture',title='fixture',extractor='fixture',extractor_key='Generic',webpage_url=base,formats=formats)))
                bad=root/'bad-bin';bad.mkdir();(bad/'ffmpeg').write_text('#!/bin/sh\nexit 127\n');(bad/'ffmpeg').chmod(0o755)
                for label,location,success in [('working',ffmpeg,True),('unusable',str(bad),False)]:
                    job=root/label;job.mkdir();manifest=job/'finished.jsonl'
                    command=[sys.executable,str(downloader),'--ignore-config',constant('STRICT_ERRORS'),'--no-playlist','--no-mtime','--no-cache-dir','--load-info-json',str(info),'-f',constant('YOUTUBE_FORMAT'),'--ffmpeg-location',location,'--merge-output-format','mp4','-o',str(job/'reel.%(ext)s'),'--print-to-file',constant('RESULT_TEMPLATE'),str(manifest)]
                    env=dict(os.environ)
                    if not success:env['PATH']='/nonexistent'
                    result=subprocess.run(command,capture_output=True,text=True,env=env)
                    if success:
                        assert result.returncode==0 and manifest.exists(),result.stdout+result.stderr
                        records=manifest.read_text().splitlines();assert len(records)==1,records
                        output=Path(json.loads(records[0])['filepath']);assert output==job/'reel.mp4' and output.stat().st_size>0
                        tracks=json.loads(run(ffprobe,'-v','error','-show_entries','stream=codec_type','-of','json',str(output)).stdout)['streams']
                        assert {item['codec_type'] for item in tracks}=={'video','audio'}
                    else:
                        assert result.returncode!=0,result.stdout+result.stderr
                        assert not manifest.exists(),'Failed merge reported a finished file'
                    print(label+' FFmpeg: passed')
            finally:server.shutdown();thread.join()

if __name__=='__main__':test(Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'app/build/generated/downloader/res/raw/ytdlp')
