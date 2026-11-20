#!/usr/bin/env python3
"""Render a reviewable Nginx http-context include. Never install, reload or read keys."""
import argparse
from pathlib import Path
import re


def render(host, certificate, private_key, gateway_port=25567, listen_port=443, loopback_only=False):
    # Restrict interpolation to literal names, ports and paths, never Nginx code.
    if not isinstance(host, str) or len(host)>253 or not all(
        re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?', label)
        for label in host.split('.')
    ):
        raise ValueError('literal lowercase DNS host required')
    paths=[]
    for value in (certificate, private_key):
        value=str(value)
        if not value.startswith('/') or not re.fullmatch(r'/[A-Za-z0-9_./-]+',value) or '..' in Path(value).parts:
            raise ValueError('absolute literal certificate/key path required')
        paths.append(value)
    for port in (gateway_port,listen_port):
        if type(port) is not int or not 1<=port<=65535:
            raise ValueError('port must be 1..65535')
    values={'HOST':host,'CERTIFICATE':paths[0],'PRIVATE_KEY':paths[1],
            'GATEWAY_PORT':str(gateway_port),'LISTEN':('127.0.0.1:' if loopback_only else '')+str(listen_port)}
    template=Path(__file__).with_name('nginx.conf.in').read_text()
    return re.sub(r'@@([A-Z_]+)@@',lambda match:values[match[1]],template)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host',required=True)
    parser.add_argument('--certificate',required=True)
    parser.add_argument('--private-key',required=True)
    parser.add_argument('--gateway-port',type=int,default=25567)
    parser.add_argument('--listen-port',type=int,default=443)
    parser.add_argument('--loopback-only',action='store_true')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    try:
        content=render(args.host,args.certificate,args.private_key,args.gateway_port,args.listen_port,args.loopback_only)
        # Exclusive creation avoids overwriting an existing service configuration.
        with args.output.open('x') as output:output.write(content)
    except (ValueError,OSError):
        parser.exit(2,'Proxy render failed: invalid input or output unavailable.\n')
    print('Proxy include rendered. Installation and reload were not attempted.')


if __name__=='__main__':main()
