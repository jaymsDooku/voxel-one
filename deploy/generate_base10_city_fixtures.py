#!/usr/bin/env python3
"""Generate compatibility fixtures with the exact pre-regional base CityFrame writer.

Run after Maven test-compile. Source extraction, compilation and output stay in this
worktree. Uses immutable Git objects; never checks out, stages or publishes files.
"""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = '2b50f4c4be6d47a865906ec0af04585a6db203b0'
TMP = ROOT / 'target/base10-fixture-builder'
OUTPUT = ROOT / 'src/test/resources/city'
WRITERS = ['src/main/java/dev/jayms/net/city/CityFrame.java',
           'src/main/java/dev/jayms/net/city/RoadTypes.java']
DEPENDENCIES = ['GameConfig', 'CityEconomy', 'CityAddresses', 'Agriculture', 'Polygon']

def git_source(path):
    return subprocess.check_output(['git', 'show', BASE + ':' + path], cwd=ROOT)


def main():
    if not (ROOT / 'target/classes/dev/jayms/net/city/CityEconomy.class').is_file():
        raise SystemExit('Run Maven test-compile first.')
    sources = TMP / 'src'
    classes = TMP / 'classes'
    sources.mkdir(parents=True, exist_ok=True)
    classes.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name in DEPENDENCIES:
        path = 'src/main/java/dev/jayms/net/city/' + name + '.java'
        original = git_source(path)
        if original != (ROOT / path).read_bytes():
            raise SystemExit('Fixture dependency differs from base: ' + path)
        hashes[path] = hashlib.sha256(original).hexdigest()
    java_files = []
    for path in WRITERS:
        original = git_source(path)
        dest = sources / Path(path).name
        dest.write_bytes(original)
        java_files.append(str(dest))
        hashes[path] = hashlib.sha256(original).hexdigest()
    builder = sources / 'Base10CityFixtures.java'
    builder.write_text('''import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class Base10CityFixtures {
    static void save(Path path, CityFrame city) throws Exception {
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x4349543A);
            city.write(out, 10);
        }
    }
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        save(out.resolve("base10-empty.city"), CityFrame.empty(GameConfig.cityGame()));
        var roads = List.of(new CityFrame.Road(8,24,10,0),new CityFrame.Road(9,24,10,1),
                new CityFrame.Road(10,24,10,2),new CityFrame.Road(11,24,10,3));
        save(out.resolve("base10-paved.city"), new CityFrame(GameConfig.cityGame(),0,roads,
                List.of(),List.of(),List.of(),List.of()));
    }
}
''')
    java_files.append(str(builder))
    # Isolated base classes come first, so the current regional writer cannot be used.
    subprocess.run(['javac', '-cp', str(ROOT / 'target/classes'), '-d', str(classes)] + java_files,
                   cwd=ROOT, check=True)
    subprocess.run(['java', '-cp', str(classes) + ':' + str(ROOT / 'target/classes'),
                    'Base10CityFixtures', str(OUTPUT)], cwd=ROOT, check=True)
    fixtures = {p.name: {'bytes': p.stat().st_size,
                         'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in sorted(OUTPUT.glob('base10-*.city'))}
    manifest = {'base_commit': BASE, 'save_magic': '0x4349543A', 'save_version': 10,
                'generation_command': 'python3 deploy/generate_base10_city_fixtures.py',
                'writer': 'Exact base CityFrame and RoadTypes; unchanged base DTO dependencies',
                'base_source_sha256': hashes,
                'generator_java_sha256': hashlib.sha256(builder.read_bytes()).hexdigest(),
                'fixtures': fixtures}
    (OUTPUT / 'base10-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Generated exact-base empty and paved-road format-10 fixtures.')

if __name__ == '__main__':
    main()
