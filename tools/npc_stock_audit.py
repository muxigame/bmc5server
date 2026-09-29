"""Compile local-only stock or follower audit mods against an existing reconstructed build."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile

import npc_source


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace', type=Path, default=Path(r'D:\CPN\customnpcs-source'))
    p.add_argument('--java-home', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--suite', choices=['stock', 'follower'], default='stock')
    args = p.parse_args()
    build = Path(json.loads((args.workspace / 'reports/rebuild-latest.json').read_text(encoding='utf-8'))['build_directory'])
    deps = json.loads((args.workspace / 'provenance/dependencies.json').read_text(encoding='utf-8'))
    cp = [build / 'classes', build / 'access-overlay.jar'] + [Path(d['path']) for d in deps if not any(
        s in d['sourcePath'].replace('\\', '/') for s in ['guava/20.0/', '/asm/9.3/', '/asm-tree/9.3/'])]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    mods = [('TraderStockAudit','trader_stock_audit'),('TraderStockVisual','trader_stock_visual')] if args.suite == 'stock' else [('FollowerAudit','follower_audit')]
    sources = [Path(__file__).parent / 'npc-rebuild' / (name + '.java') for name, _ in mods]
    javac_args = out / 'compile.args'
    npc_source.jvm_args(javac_args, ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-cp', os.pathsep.join(map(str,cp)), '-d', classes, *sources])
    subprocess.run([args.java_home / 'bin/javac.exe', '@' + str(javac_args)], check=True)
    for name, mod_id in mods:
        jar = out / (mod_id + '-local-only.jar')
        with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
            for f in (classes/'audit').glob(name+'*.class'):
                z.write(f,f.relative_to(classes).as_posix())
            z.writestr('META-INF/neoforge.mods.toml', f'''modLoader="javafml"
loaderVersion="[4,)"
license="All Rights Reserved"
[[mods]]
modId="{mod_id}"
version="1.0"
displayName="Local NPC audit (DO NOT DEPLOY)"
[[dependencies.{mod_id}]]
modId="customnpcs"
type="required"
versionRange="[1,)"
ordering="AFTER"
side="BOTH"
''')
        print(jar)


if __name__ == '__main__':
    main()
