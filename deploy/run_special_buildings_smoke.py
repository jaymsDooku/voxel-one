#!/usr/bin/env python3
"""Render real Java/OpenGL special-building placement on Mesa EGL without an X server or external files.

Run after mvn test-compile, with the Maven cache as the optional second argument.
All native extraction, screenshots and report output remain inside the worktree.
"""
import ctypes as C
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
TMP = ROOT / 'target/special-buildings-smoke-tmp'
TMP.mkdir(parents=True, exist_ok=True)
os.environ['TMPDIR'] = str(TMP)
os.environ['XDG_CACHE_HOME'] = str(TMP / 'cache')
os.environ['LIBGL_ALWAYS_SOFTWARE'] = '1'
os.environ['MESA_SHADER_CACHE_DISABLE'] = 'true'

def function(lib, name, result, *args):
    f = getattr(lib, name)
    f.restype, f.argtypes = result, args
    return f

p, i = C.c_void_p, C.c_int
# EGL surfaceless pbuffers use the same Mesa OpenGL renderer as the X desktop.
egl = C.CDLL('libEGL.so.1', mode=C.RTLD_GLOBAL)
display = function(egl, 'eglGetPlatformDisplay', p, C.c_uint, p, p)(0x31DD, None, None)
major, minor = i(), i()
assert function(egl, 'eglInitialize', C.c_uint, p, C.POINTER(i), C.POINTER(i))(display, C.byref(major), C.byref(minor)), 'EGL initialize'
assert function(egl, 'eglBindAPI', C.c_uint, C.c_uint)(0x30A2), 'OpenGL API'
attrs = (i * 13)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038)
config, count = p(), i()
assert function(egl, 'eglChooseConfig', C.c_uint, p, C.POINTER(i), C.POINTER(p), i, C.POINTER(i))(display, attrs, C.byref(config), 1, C.byref(count)) and count.value, 'EGL config'
surface_attrs = (i * 5)(0x3057, 1280, 0x3056, 720, 0x3038)
surface = function(egl, 'eglCreatePbufferSurface', p, p, p, C.POINTER(i))(display, config, surface_attrs)
context_attrs = (i * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038)
context = function(egl, 'eglCreateContext', p, p, p, p, C.POINTER(i))(display, config, None, context_attrs)
assert surface and context, 'OpenGL 3.3 pbuffer context'
assert function(egl, 'eglMakeCurrent', C.c_uint, p, p, p, p)(display, surface, surface, context), 'Current context'

cache = Path(sys.argv[2] if len(sys.argv) > 2 else ROOT / 'target/m2').resolve()
classpath = os.pathsep.join([str(ROOT/'target/classes'), str(ROOT/'target/test-classes')] + [str(j) for j in sorted(cache.rglob('*.jar'))])
subprocess.run(['javac', '-cp', classpath, '-d', str(ROOT/'target/test-classes'), str(ROOT/'deploy/SpecialBuildingsSmoke.java')], check=True)
java_home = Path(shutil.which('java')).resolve().parents[1]
jvm = C.CDLL(str(java_home/'lib/server/libjvm.so'), mode=C.RTLD_GLOBAL)
class Option(C.Structure):
    _fields_ = [('optionString', C.c_char_p), ('extraInfo', p)]
class Args(C.Structure):
    _fields_ = [('version', i), ('nOptions', i), ('options', C.POINTER(Option)), ('ignoreUnrecognized', C.c_ubyte)]
options = (Option * 4)(*[Option(s.encode(), None) for s in [
    '-Djava.class.path='+classpath, '-Djava.io.tmpdir='+str(TMP),
    '-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(TMP/'natives'), '-Djava.awt.headless=true']])
vm, env = p(), p()
assert function(jvm, 'JNI_CreateJavaVM', i, C.POINTER(p), C.POINTER(p), C.POINTER(Args))(C.byref(vm), C.byref(env), C.byref(Args(0x00010008, 4, options, 0))) == 0, 'JVM creation'
table = C.cast(env, C.POINTER(C.POINTER(p))).contents
# JNI table positions are specified by the Java Native Interface ABI.
def jni(index, result, *args):
    return C.CFUNCTYPE(result, p, *args)(table[index])
find = jni(6, p, C.c_char_p)
klass = find(env, b'dev/jayms/SpecialBuildingsSmoke')
assert klass, 'Smoke class'
method = jni(113, p, p, C.c_char_p, C.c_char_p)(env, klass, b'main', b'([Ljava/lang/String;)V')
assert method, 'Smoke main'
array = jni(172, p, i, p, p)(env, 2, find(env, b'java/lang/String'), None)
new_string = jni(167, p, C.c_char_p)
set_element = jni(174, None, p, i, p)
for index, text in enumerate([sys.argv[1] if len(sys.argv)>1 else 'dashboard/evidence', 'offscreen']):
    set_element(env, array, index, new_string(env, text.encode()))
class Value(C.Union):
    _fields_ = [('l', p), ('j', C.c_longlong), ('d', C.c_double)]
arguments = (Value * 1)(); arguments[0].l = array
jni(143, None, p, p, C.POINTER(Value))(env, klass, method, arguments)
exception = jni(15, p)(env)
if exception:
    jni(16, None)(env)
    raise SystemExit('Smoke failed with Java exception')
report_path = ROOT / (sys.argv[1] if len(sys.argv)>1 else 'dashboard/evidence') / 'special-buildings-rendering.json'
report = json.loads(report_path.read_text())
report['egl'] = f'{major.value}.{minor.value}'
report['command'] = 'python3 deploy/run_special_buildings_smoke.py ' + ' '.join(sys.argv[1:])
report['checks'] = ['Actual menu click permits a City hall', 'Rendered placement changes at least 1000 world pixels', 'No OpenGL errors in both captures']
report_path.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'egl':f'{major.value}.{minor.value}', 'mode':'Mesa surfaceless EGL OpenGL pbuffer', 'result':'passed'}))
