#!/usr/bin/env python3
"""Authenticated dashboard questions; credential arrives only on hidden stdin, never in files."""
import json,sys,termios,urllib.request,urllib.error
URL='https://voxel-one.jamesleaver1.chatgpt.site/api/agent/questions'
if sys.stdin.isatty():
 settings=termios.tcgetattr(sys.stdin);hidden=settings.copy();hidden[3]&=~termios.ECHO;termios.tcsetattr(sys.stdin,termios.TCSANOW,hidden)
 print('Ready for dashboard request on stdin (input is hidden).',flush=True)
 try:task=json.loads(sys.stdin.readline())
 finally:termios.tcsetattr(sys.stdin,termios.TCSANOW,settings)
else:task=json.load(sys.stdin)
token=task.pop('token',None)
if not token:raise SystemExit('A Sites service credential is required.')
action=task.pop('action','list');payload=None if action=='list' else json.dumps({'action':action,**task}).encode()
request=urllib.request.Request(URL,data=payload,headers={'OAI-Sites-Authorization':'Bearer '+token,'X-Voxel-Agent':'1','Content-Type':'application/json'},method='GET' if payload is None else 'POST')
try:
 with urllib.request.urlopen(request,timeout=20) as response:print(response.read().decode())
except urllib.error.HTTPError as error:print(error.read().decode());raise SystemExit(error.code)
