#!/usr/bin/env python3
"""Small milestone updates; GitHub stores the JSON without rebuilding game releases."""
import argparse, base64, datetime, json, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / 'dashboard/progress.json'
REPO = 'jaymsDooku/voxel-one'
BRANCH = 'development-progress'
RAW = 'https://raw.githubusercontent.com/' + REPO + '/' + BRANCH + '/'
STATUSES = ('queued', 'in_progress', 'blocked', 'complete')

def api(path, method='GET', payload=None, missing=False):
    command=['gh','api','repos/'+REPO+'/'+path]
    if method!='GET':command+=['--method',method]
    if payload is not None:command+=['--input','-']
    result=subprocess.run(command,input=json.dumps(payload) if payload is not None else None,capture_output=True,text=True)
    if result.returncode:
        if missing and 'HTTP 404' in result.stderr:return None
        raise RuntimeError('GitHub progress request failed: '+result.stderr.strip())
    return json.loads(result.stdout)

def content(path):
    return api('contents/'+path+'?ref='+BRANCH,missing=True)

def put(path, data, message):
    old=content(path)
    encoded=base64.b64encode(data).decode()
    if old and old.get('content','').replace('\n','')==encoded:return False
    body={'message':message,'content':encoded,'branch':BRANCH}
    if old:body['sha']=old['sha']
    api('contents/'+path, 'PUT',body)
    return True

def write(data):
    temporary=FILE.with_suffix('.tmp');temporary.write_text(json.dumps(data,indent=2)+'\n');temporary.replace(FILE)

def publish(data, artifacts):
    if api('git/ref/heads/'+BRANCH,missing=True) is None:
        head=api('git/ref/heads/master')['object']['sha']
        api('git/refs','POST',{'ref':'refs/heads/'+BRANCH,'sha':head})
    existing=content('progress.json')
    if existing:
        remote=json.loads(base64.b64decode(existing['content']))
        by_id={item['id']:item for item in remote['items']}
        for item in data['items']:
            previous=by_id.get(item['id'])
            if previous is None or item.get('updatedAt','')>=previous.get('updatedAt',''):by_id[item['id']]=item
        data['items']=list(by_id.values())
        data['updatedAt']=max(data['updatedAt'],remote['updatedAt'])
    for artifact in artifacts:
        target=ROOT/'dashboard/evidence'/artifact
        if target.resolve().parent!=(ROOT/'dashboard/evidence').resolve() or not target.is_file():raise ValueError('Artifacts must be files directly inside dashboard/evidence.')
        if target.stat().st_size>6_000_000:raise ValueError('Evidence artifact exceeds 6 MB.')
        put('evidence/'+target.name,target.read_bytes(),'Record verification evidence: '+target.name)
    changed=put('progress.json',(json.dumps(data,indent=2)+'\n').encode(),'Update development milestones')
    write(data)
    print('Published progress.' if changed else 'Progress unchanged; no publication needed.')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['set','publish'])
    parser.add_argument('item',nargs='?')
    parser.add_argument('--status',choices=STATUSES)
    parser.add_argument('--title')
    parser.add_argument('--description')
    parser.add_argument('--note')
    parser.add_argument('--evidence',action='append',default=[],help='Label|HTTPS URL')
    parser.add_argument('--artifact',action='append',default=[],help='Filename inside dashboard/evidence to publish')
    parser.add_argument('--publish',action='store_true')
    args=parser.parse_args();data=json.loads(FILE.read_text())
    if args.action=='set':
        if not args.item or not args.status:parser.error('set needs an item ID and --status')
        item=next((i for i in data['items'] if i['id']==args.item),None)
        if item is None:
            if not args.title:parser.error('A new work item needs --title')
            item={'id':args.item,'title':args.title,'description':args.description or args.note or '', 'evidence':[]};data['items'].insert(0,item)
        before=json.loads(json.dumps(item))
        item['status']=args.status
        for name in ['title','description','note']:
            value=getattr(args,name)
            if value is not None:item[name]=value
        for entry in args.evidence:
            label,separator,url=entry.partition('|')
            if not separator or not url.startswith('https://'):parser.error('--evidence needs Label|HTTPS URL')
            evidence={'kind':'link','label':label,'url':url}
            if evidence not in item['evidence']:item['evidence'].append(evidence)
        for name in args.artifact:
            evidence={'kind':'image' if name.endswith('.png') else 'report','label':name.replace('-',' '),'url':RAW+'evidence/'+name}
            if evidence not in item['evidence']:item['evidence'].append(evidence)
        if item!=before:
            now=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds');item['updatedAt']=now;data['updatedAt']=now
            write(data)
        else:print('Milestone unchanged.')
    if args.publish or args.action=='publish':publish(data,args.artifact)
    else:print('Recorded milestone locally.')

if __name__=='__main__':main()
