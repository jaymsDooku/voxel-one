#!/usr/bin/env python3
"""Read-only private game route diagnostic. Hidden stdin; no body/header/token artifacts."""
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

if sys.stdin.isatty():
    import termios
    attrs=termios.tcgetattr(sys.stdin);attrs[3]&=~termios.ECHO;termios.tcsetattr(sys.stdin,termios.TCSANOW,attrs)
print('Ready for hidden Site access JSON',flush=True)
request=json.loads(sys.stdin.readline());token=request.get('token')
origin='https://voxel-one.jamesleaver1.chatgpt.site'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
opener=urllib.request.build_opener(NoRedirect)
report={'playtest':'Playtest: GET game routes with and without supported Site service access; compare status/start-control/module presence. No identity, cookies, response bodies or credentials retained.','routes':[]}
for path in ('/games/space-invaders/','/games/space-invaders/index.html'):
    for authenticated in (False,True):
        if authenticated and not token:continue
        headers={'OAI-Sites-Authorization':'Bearer '+token} if authenticated else {}
        entry={'path':path,'serviceAccess':authenticated}
        try:
            try:response=opener.open(urllib.request.Request(origin+path,headers=headers),timeout=20)
            except urllib.error.HTTPError as error:response=error
            with response:
                body=response.read(1024*1024);entry['status']=response.code
                location=response.headers.get('Location','');entry['authRedirect']='signin' in location or 'auth' in location
                entry['gameStartControl']=bool(re.search(rb'id=["\']start["\']',body))
                entry['moduleScript']=bool(re.search(rb'type=["\']module["\']',body))
        except Exception as error:entry['error']=type(error).__name__
        report['routes'].append(entry)
destination=Path('dashboard/evidence/hub-site-access.json');destination.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
