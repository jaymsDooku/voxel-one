#!/usr/bin/env python3
"""Check only sanitized native PCM artifacts; never reads profiles or runtime logs."""
import argparse, hashlib, json, math, struct, wave
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,default=Path('dashboard/evidence'));a=p.parse_args()
rows=[]
for name in ['vehicle-audio-car-idle.wav','vehicle-audio-car-driving.wav','vehicle-audio-train-moving.wav','vehicle-audio-plane-takeoff.wav']:
    with wave.open(str(a.evidence/name)) as w:
        channels=w.getnchannels();rate=w.getframerate();width=w.getsampwidth();count=w.getnframes();raw=w.readframes(count)
    assert channels==2 and rate==22050 and width==2 and count==22050, name+': unexpected PCM format'
    samples=struct.unpack('<'+'h'*(len(raw)//2),raw)
    rms=math.sqrt(sum(s*s for s in samples)/len(samples));peak=max(abs(s) for s in samples)
    assert rms>100 and peak<32767,name+': silent or clipped PCM'
    rows.append({'file':name,'channels':channels,'sampleRateHz':rate,'sampleBits':16,'durationSeconds':count/rate,'rms':round(rms,2),'peak':peak,'clippedSamples':sum(abs(s)>=32767 for s in samples),'sha256':hashlib.sha256(raw).hexdigest()})
assert len({r['sha256'] for r in rows})==len(rows),'Captured sounds must differ'
report={'status':'passed','origin':'actual native OpenAL Soft loopback during Main application playtest','physicalSpeakerOutputTested':False,'captures':rows}
(a.evidence/'vehicle-audio-pcm-check.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS: four distinct, audible, unclipped stereo PCM captures, 22050 Hz, 16 bit, 1 second each')
