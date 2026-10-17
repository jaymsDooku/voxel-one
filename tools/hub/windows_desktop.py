#!/usr/bin/env python3
"""Select an advertised desktop mode on this assignment's ephemeral CI runner."""
import ctypes
import json
import os
from pathlib import Path


def configure():
    branch='refs/heads/feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161'
    if os.name!='nt' or os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('GITHUB_REPOSITORY')!='jaymsDooku/voxel-one' or os.environ.get('GITHUB_REF')!=branch:
        raise ValueError('Desktop changes require the fenced ephemeral Windows CI job')
    class Mode(ctypes.Structure):
        _fields_=[('device',ctypes.c_wchar*32),('spec',ctypes.c_ushort),('driver',ctypes.c_ushort),
          ('size',ctypes.c_ushort),('extra',ctypes.c_ushort),('fields',ctypes.c_uint32),
          ('x',ctypes.c_int32),('y',ctypes.c_int32),('orientation',ctypes.c_uint32),('fixed',ctypes.c_uint32),
          ('color',ctypes.c_short),('duplex',ctypes.c_short),('yres',ctypes.c_short),('tt',ctypes.c_short),('collate',ctypes.c_short),
          ('form',ctypes.c_wchar*32),('logpixels',ctypes.c_ushort),('bits',ctypes.c_uint32),
          ('width',ctypes.c_uint32),('height',ctypes.c_uint32),('flags',ctypes.c_uint32),('frequency',ctypes.c_uint32),
          ('icm',ctypes.c_uint32),('intent',ctypes.c_uint32),('media',ctypes.c_uint32),('dither',ctypes.c_uint32),
          ('reserved1',ctypes.c_uint32),('reserved2',ctypes.c_uint32),('panwidth',ctypes.c_uint32),('panheight',ctypes.c_uint32)]
    assert ctypes.sizeof(Mode)==220
    api=ctypes.WinDLL('user32',use_last_error=True)
    api.EnumDisplaySettingsW.argtypes=[ctypes.c_wchar_p,ctypes.c_uint32,ctypes.POINTER(Mode)]
    api.ChangeDisplaySettingsW.argtypes=[ctypes.POINTER(Mode),ctypes.c_uint32]
    api.ChangeDisplaySettingsW.restype=ctypes.c_long
    current=Mode();current.size=ctypes.sizeof(Mode)
    if not api.EnumDisplaySettingsW(None,0xffffffff,ctypes.byref(current)):raise ValueError('Current desktop mode unavailable')
    report={'before':[current.width,current.height],'required':[2560,1600],
            'limitation':'Display resolution setup only; actual Windows monitor DPI scenarios remain separate.'}
    candidates=[];index=0
    while True:
        mode=Mode();mode.size=ctypes.sizeof(Mode)
        if not api.EnumDisplaySettingsW(None,index,ctypes.byref(mode)):break
        if mode.width>=2560 and mode.height>=1600 and mode.bits==current.bits:candidates.append(mode)
        index+=1
    if current.width>=2560 and current.height>=1600:report['status']='already sufficient'
    elif candidates:
        mode=min(candidates,key=lambda m:m.width*m.height)
        code=api.ChangeDisplaySettingsW(ctypes.byref(mode),0)
        report['changeResult']=code;report['status']='changed' if code==0 else 'unsupported'
    else:report['status']='unsupported'
    actual=Mode();actual.size=ctypes.sizeof(Mode)
    if not api.EnumDisplaySettingsW(None,0xffffffff,ctypes.byref(actual)):raise ValueError('Updated desktop mode unavailable')
    report['after']=[actual.width,actual.height]
    report['sufficient']=actual.width>=2560 and actual.height>=1600
    return report


if __name__=='__main__':
    output=Path('dashboard/evidence/hub-scoped-windows/desktop.json');output.parent.mkdir(parents=True,exist_ok=True)
    try:report=configure()
    except Exception:report={'status':'failed','sufficient':False,'observed':'Fenced desktop setup unavailable; no controls bypassed.'}
    output.write_text(json.dumps(report,indent=2)+'\n')
