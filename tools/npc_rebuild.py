"""Rebuild recovered CustomNPCs in isolation. Never installs a runtime mod."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

import npc_source

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def run(java, args, directory, name):
    argfile = directory / (name + '.args')
    npc_source.jvm_args(argfile, args)
    with (directory / (name + '.log')).open('w', encoding='utf-8') as log:
        subprocess.run([str(java), '@' + str(argfile)], stdout=log, stderr=subprocess.STDOUT, check=True)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace', type=Path, default=Path(r'D:\CPN\customnpcs-source'))
    p.add_argument('--java-home', type=Path, required=True)
    args = p.parse_args()
    root = args.workspace.resolve()
    original = root / 'original' / npc_source.JAR_NAME
    if sha(original) != npc_source.JAR_SHA:
        raise ValueError('Original JAR hash mismatch')
    deps = json.loads((root / 'provenance/dependencies.json').read_text(encoding='utf-8'))
    for d in deps:
        if sha(Path(d['path'])) != d['sha256']:
            raise ValueError('Dependency hash mismatch: ' + d['path'])
    builds = root / 'build'
    builds.mkdir(exist_ok=True)
    build = Path(tempfile.mkdtemp(prefix='rebuild-', dir=builds))
    java = args.java_home / 'bin/java.exe'
    javac = args.java_home / 'bin/javac.exe'
    asm = [d['path'] for d in deps if '/org/ow2/asm/' in d['sourcePath'].replace('\\', '/') and '9.10.1' in d['sourcePath']]
    helpers = Path(__file__).parent / 'npc-rebuild'
    run(javac, ['-encoding', 'UTF-8', '-cp', os.pathsep.join(asm), '-d', build,
                helpers / 'AccessOverlay.java', helpers / 'ClassInventory.java'], build, 'helpers')
    cfg = ''
    for d in deps[:3]:
        with zipfile.ZipFile(d['path']) as z:
            if 'META-INF/accesstransformer.cfg' in z.namelist():
                cfg += z.read('META-INF/accesstransformer.cfg').decode('utf-8') + '\n'
    resources = root / 'src/main/resources'
    if any(resources.rglob('*.class')):
        raise ValueError('Resources must not contain class files that could replace compiled output')
    cfg += (resources / 'META-INF/accesstransformer.cfg').read_text(encoding='utf-8')
    for line in (resources / 'customnpcs.accesswidener').read_text(encoding='utf-8').splitlines()[1:]:
        part = line.split('#', 1)[0].split()
        if not part:
            continue
        member = '' if part[1] == 'class' else part[3] + (part[4] if part[1] == 'method' else '')
        cfg += '\n' + ('public-f' if part[0] == 'extendable' else 'public') + ' ' + part[2].replace('/', '.') + ' ' + member
    # Protected nested enum is accessible to its subclass at runtime; recovered import needs a compile-only view.
    cfg += '\npublic net.minecraft.world.entity.ai.control.MoveControl$Operation\n'
    (build / 'access.cfg').write_text(cfg, encoding='utf-8')
    helper_cp = os.pathsep.join([str(build), *asm])
    run(java, ['-cp', helper_cp, 'AccessOverlay', build / 'access.cfg', build / 'access-overlay.jar',
               *[d['path'] for d in deps[:3]]], build, 'overlay')
    cp = [str(build / 'access-overlay.jar')] + [d['path'] for d in deps if not any(
        s in d['sourcePath'].replace('\\', '/') for s in ['guava/20.0/', '/asm/9.3/', '/asm-tree/9.3/'])]
    sources = sorted((root / 'src/main/java').rglob('*.java'))
    classes = build / 'classes'
    classes.mkdir()
    run(javac, ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-XDrawDiagnostics', '-Xmaxerrs', '5000',
                '-sourcepath', '', '-cp', os.pathsep.join(cp), '-d', classes, *sources], build, 'compile')
    artifact = build / 'CustomNPCs-rebuilt-candidate.jar'
    entries = {f.relative_to(classes).as_posix(): f.read_bytes() for f in classes.rglob('*.class')}
    entries.update({f.relative_to(resources).as_posix(): f.read_bytes() for f in resources.rglob('*') if f.is_file()})
    with zipfile.ZipFile(artifact, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, data)
    for label, jar in [('original', original), ('rebuilt', artifact)]:
        run(java, ['-cp', helper_cp, 'ClassInventory', jar, build / label], build, 'inventory-' + label)
    def inventory(label):
        return {tuple(s.split('\t')[:3]): s.split('\t')[3] for s in (build / label / 'inventory.tsv').read_text(encoding='utf-8').splitlines()}
    old, new = inventory('original'), inventory('rebuilt')
    differences = [{'kind': k[0], 'class': k[1], 'member': k[2], 'original': old.get(k), 'rebuilt': new.get(k)}
                   for k in sorted(old.keys() | new.keys()) if old.get(k) != new.get(k)]
    with zipfile.ZipFile(original) as z:
        old_resources = {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith(('/', '.class'))}
        old_classes = {n for n in z.namelist() if n.endswith('.class')}
    new_resources = {n: hashlib.sha256(v).hexdigest() for n, v in entries.items() if not n.endswith('.class')}
    new_classes = {n for n in entries if n.endswith('.class')}
    summary = dict(build_directory=str(build), artifact=str(artifact), sha256=sha(artifact), source_files=len(sources),
                   original_classes=len(old_classes), rebuilt_classes=len(new_classes),
                   missing_classes=sorted(old_classes-new_classes), additional_classes=sorted(new_classes-old_classes),
                   resources_identical=old_resources == new_resources,
                   exact_normalized_methods=sum(k[0] == 'CODE' and old[k] == new.get(k) for k in old),
                   original_methods=sum(k[0] == 'CODE' for k in old),
                   differences={kind: sum(d['kind'] == kind for d in differences) for kind in ['CLASS','FIELD','METHOD','CODE']},
                   equivalence_proven=False, note='Compilation and comparison only. Candidate is not automatically installed.')
    inputs = {'original_sha256': sha(original), 'compiler_sha256': sha(javac),
              'sources': {f.relative_to(root).as_posix(): sha(f) for f in sources},
              'resources': {f.relative_to(root).as_posix(): sha(f) for f in resources.rglob('*') if f.is_file()},
              'tools': {str(f.name): sha(f) for f in [Path(__file__), helpers / 'AccessOverlay.java', helpers / 'ClassInventory.java']},
              'dependency_manifest_sha256': sha(root / 'provenance/dependencies.json')}
    (build / 'inputs.json').write_text(json.dumps(inputs, ensure_ascii=False, indent=2), encoding='utf-8')
    (build / 'differences.json').write_text(json.dumps(differences, ensure_ascii=False, indent=2), encoding='utf-8')
    (build / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    (root / 'reports/rebuild-latest.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False))

if __name__ == '__main__':
    main()
