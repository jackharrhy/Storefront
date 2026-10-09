#!/usr/bin/env python3
"""Run against built runtime jars, in both classpath orders. No server/world needed."""
import os
from pathlib import Path
import subprocess
import sys
import zipfile

if len(sys.argv) not in (3, 4):
    raise SystemExit('Usage: python dev/combined-web-classpath.py STOREFRONT.jar ITEMSORTER.jar [AUDIOPLAYER.jar]')
jars = [Path(arg).resolve(strict=True) for arg in sys.argv[1:3]]
# Put the real colliding resource owner first in both application classpath orders.
extra = [Path(sys.argv[3]).resolve(strict=True)] if len(sys.argv) == 4 else []
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
# These are Minecraft-owned APIs, intentionally absent from the runtime mod jars.
support = []
for group, artifact in [('com.google.code.gson', 'gson'), ('org.slf4j', 'slf4j-api')]:
    candidates = sorted((cache / group / artifact).glob('*/*/*.jar'))
    if not candidates:
        raise SystemExit(f'Missing cached {artifact}; build the Fabric projects first')
    support.append(max(candidates, key=lambda p: tuple(int(part) for part in p.parents[1].name.split('.') if part.isdigit())))
source = Path(__file__).with_name('CombinedWebClasspath.java')
for order in (jars, jars[::-1]):
    for context_path in ('', '/sorter'):
        print('Classpath order:', ', '.join(p.name for p in extra + order),
              'ItemSorter context:', context_path or '/', flush=True)
        subprocess.run(['java', '--enable-native-access=ALL-UNNAMED', '-cp',
                        os.pathsep.join(map(str, extra + order + support)), str(source), context_path], check=True)

classes = []
for jar in jars:
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        classes.append({n for n in names if n.endswith('.class')})
        exposed = ('io/javalin/', 'kotlin/', 'org/eclipse/jetty/', 'javax/servlet/',
                   'jakarta/servlet/', 'org/objectweb/asm/', 'org/slf4j/', 'com/google/gson/')
        assert not any(n.startswith(exposed) for n in classes[-1]), f'Unisolated library in {jar}'
        for name in names:
            if name.startswith('META-INF/services/') and not name.endswith('/'):
                for line in archive.read(name).decode().splitlines():
                    provider = line.split('#', 1)[0].strip()
                    if provider:
                        assert provider.replace('.', '/') + '.class' in names, (jar, name, provider)
shared = classes[0] & classes[1]
assert all(n.startswith(('org/jetbrains/annotations/', 'org/intellij/lang/annotations/')) for n in shared), shared
print('PASS: isolated libraries, Minecraft-owned APIs excluded, service providers packaged')
