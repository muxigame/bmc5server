"""Prepare a local, traceable CustomNPCs recovery workspace; never install a mod.

Python 3.11+ and JDK 21. Decompiled sources are a migration starting point,
not upstream source and not a validated replacement JAR.
"""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import urllib.request
import zipfile

SERVER = Path(__file__).resolve().parents[1]
JAR_NAME = 'CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar'
JAR_SHA = '6c28d87b215fc1191488194188ec8a39dd908ae7d2d887b7c9d7d463be0a162c'
VF_URL = 'https://github.com/Vineflower/vineflower/releases/download/1.12.0/vineflower-1.12.0.jar'
VF_SHA = '1dfcfe974395734fa467ce620661c7623d05ba83670de0529b1fbd63ff548b9d'
CFR_URL = 'https://www.benf.org/other/cfr/cfr-0.152.jar'
CFR_SHA = 'f686e8f3ded377d7bc87d216a90e9e9512df4156e75b06c655a16648ae8765b2'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def download(url, path, expected=None):
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={'User-Agent': 'BMC5-NPC-source-audit/1.0'})
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read()
        if expected and hashlib.sha256(payload).hexdigest() != expected:
            raise ValueError('Downloaded SHA256 mismatch: ' + url)
        path.write_bytes(payload)
    if expected and digest(path) != expected:
        raise ValueError('Existing file SHA256 mismatch: ' + str(path))


def safe_member(name):
    parts = PurePosixPath(name).parts
    if not parts or name.startswith('/') or '\\' in name or ':' in name or '..' in parts:
        raise ValueError('Unsafe archive entry: ' + name)
    return Path(*parts)


def jvm_args(path, args):
    # Java argument-file quoting, not shell interpolation.
    path.write_text('\n'.join('"' + str(x).replace('\\', '/').replace('"', '\\"') + '"' for x in args), encoding='utf-8')


def research(root):
    urls = {
        'modrinth-project': 'https://api.modrinth.com/v2/project/customnpcs-unofficial',
        'author-public-repositories': 'https://api.github.com/users/Goodbird-git/repos?per_page=100',
        'github-search': 'https://api.github.com/search/repositories?q=CustomNPCs+1.21.1&per_page=30',
    }
    for repo in ('CustomNPCPlus1.16.5', 'CustomNPC-Plus-Contribution', 'CustomNPC-Plus-API-Contribution'):
        urls[repo + '-branches'] = f'https://api.github.com/repos/Goodbird-git/{repo}/branches?per_page=100'
    evidence = root / 'provenance/public-source-check'
    for name, url in urls.items():
        download(url, evidence / (name + '.json'))
    write_json(evidence / 'check.json', {
        'checkedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'urls': urls,
        'result': 'Public evidence snapshot; exact-version source matching requires manual review. See the dated source-recovery documentation.',
        'evidenceFileTimes': {name: dt.datetime.fromtimestamp((evidence / (name + '.json')).stat().st_mtime, dt.timezone.utc).isoformat() for name in urls},
        'releasePage': 'https://www.curseforge.com/minecraft/mc-mods/customnpcs-unofficial/files/7411561',
        'metadataLicense': 'LicenseRef-CC-BY-NC-3.0',
        'scope': 'Public API metadata and candidate branch names. No private repositories accessed.',
    })


def libraries(args):
    neo = args.server / 'libraries/net/neoforged/neoforge/21.1.250'
    client_neo = args.client / 'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar'
    required = [client_neo, neo / 'neoforge-21.1.250-universal.jar']
    required += sorted((args.client / 'libraries/net/minecraft/client').rglob('*-srg.jar'))
    if not required[-1].name.endswith('-srg.jar') or any(not p.is_file() for p in required):
        raise ValueError('Installed mapped client + NeoForge libraries required; use --client and --server.')
    jars = required[:]
    # Use mapped/patched game classes first; avoid raw/obfuscated duplicate game jars.
    for base in (args.client / 'libraries', args.server / 'libraries'):
        jars.extend(p for p in sorted(base.rglob('*.jar'))
                    if '/net/minecraft/' not in p.as_posix() and '/net/neoforged/neoforge/' not in p.as_posix())
    jars.extend(p for p in sorted((args.client / 'mods').glob('*.jar')) if p.name != JAR_NAME)
    jars.extend(p for p in sorted((args.server / 'mods').glob('*.jar')) if p.name != JAR_NAME)
    unique = {}
    for p in jars:
        unique.setdefault(p.name, p.resolve())
    jars = list(unique.values())
    nested = args.workspace / 'dependencies/nested'
    for jar in jars[:]:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    target = nested / (jar.stem + '--' + safe_member(name).name)
                    if not target.exists():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(archive.read(name))
                    jars.append(target)
    # Java launcher argument files can lose non-ASCII file names on Windows.
    # Content-addressed local copies also prevent later mod updates changing this baseline.
    snapshots, manifest = {}, []
    for p in jars:
        sha = digest(p)
        target = args.workspace / 'dependencies/jars' / (sha + '.jar')
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copy2(p, target)
        if digest(target) != sha:
            raise ValueError('Dependency snapshot changed: ' + str(target))
        snapshots.setdefault(sha, target)
        manifest.append({'path': str(target), 'sourcePath': str(p), 'size': target.stat().st_size, 'sha256': sha})
    write_json(args.workspace / 'provenance/dependencies.json', manifest)
    return list(snapshots.values())


def prepare(args):
    root = args.workspace
    for folder in ('original', 'reference', 'src/main/java', 'src/main/resources', 'reports', 'provenance', 'tools-cache'):
        (root / folder).mkdir(parents=True, exist_ok=True)
    templates = Path(__file__).resolve().parent / 'npc-source-docs'
    for template in sorted(templates.rglob('*')):
        if template.is_file():
            destination = root / template.relative_to(templates)
            if not destination.exists():
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(template, destination)
    (root / 'patches').mkdir(exist_ok=True)
    source = args.server / 'mods' / JAR_NAME
    if digest(source) != JAR_SHA:
        raise ValueError('Wrong CustomNPCs binary; expected pinned 20251230 SHA256.')
    target = root / 'original' / JAR_NAME
    if target.exists() and digest(target) != JAR_SHA:
        raise ValueError('Existing original copy changed; refusing to overwrite.')
    if not target.exists():
        shutil.copy2(source, target)
    with zipfile.ZipFile(target) as archive:
        inventory = [{'entry': item.filename, 'bytes': item.file_size,
                      'sha256': hashlib.sha256(archive.read(item)).hexdigest()}
                     for item in archive.infolist() if not item.is_dir()]
        for name in ('META-INF/MANIFEST.MF', 'META-INF/neoforge.mods.toml', 'customnpcs.mixins.json'):
            if name in archive.namelist():
                dest = root / 'provenance/jar-metadata' / safe_member(name)
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(archive.read(name))
    write_json(root / 'provenance/jar-inventory.json', inventory)
    download(VF_URL, root / 'tools-cache/vineflower-1.12.0.jar', VF_SHA)
    if args.research:
        research(root)
    dependency_manifest = root / 'provenance/dependencies.json'
    if dependency_manifest.exists():
        pinned = json.loads(dependency_manifest.read_text(encoding='utf-8'))
        for item in pinned:
            if digest(Path(item['path'])) != item['sha256']:
                raise ValueError('Pinned dependency changed: ' + item['path'])
        cp = list(dict.fromkeys(Path(item['path']) for item in pinned))
    else:
        cp = libraries(args)
    output = root / 'reference/vineflower'
    if not (root / 'provenance/reference-files.json').exists():
        if output.exists() and any(output.iterdir()):
            raise ValueError('Unfinished reference output exists; inspect it or choose a new workspace; no automatic deletion.')
        argfile = root / 'reports/decompile.args'
        jvm_args(argfile, ['-Xmx6G', '-jar', root / 'tools-cache/vineflower-1.12.0.jar',
                          '--folder', '--thread-count=4', '--log-level=warn', '--indent-string=    ',
                          '--decompiler-comments=true', '--bytecode-source-mapping=true',
                          *('-e=' + str(p) for p in cp), target, output])
        with (root / 'reports/decompile.log').open('w', encoding='utf-8') as log:
            subprocess.run([str(args.java_home / 'bin/java.exe'), '@' + str(argfile)], stdout=log, stderr=subprocess.STDOUT, check=True)
        reference = {p.relative_to(output).as_posix(): digest(p) for p in sorted(output.rglob('*')) if p.is_file()}
        if not any(n.endswith('.java') for n in reference):
            raise ValueError('Decompiler produced no sources.')
        write_json(root / 'provenance/reference-files.json', reference)
    # Never replace hand-edited working sources on subsequent preparation.
    if not (root / 'provenance/working-baseline.json').exists():
        if any((root / 'src').rglob('*.java')):
            raise ValueError('Untracked working sources already exist; refusing to merge blindly.')
        baseline = {}
        for p in sorted(output.rglob('*')):
            if not p.is_file():
                continue
            rel = p.relative_to(output)
            destination = root / ('src/main/java' if p.suffix == '.java' else 'src/main/resources') / rel
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, destination)
            baseline[destination.relative_to(root).as_posix()] = digest(destination)
        write_json(root / 'provenance/working-baseline.json', baseline)
    index(args)
    verify(args)


def index(args):
    root = args.workspace
    sources = sorted((root / 'src/main/java').rglob('*.java'))
    rows, warnings = [], []
    patterns = ('couldn\'t be decompiled', 'Could not decompile', '$VF: Could not', 'Decompilation failed')
    for source in sources:
        content = source.read_text(encoding='utf-8')
        path = source.relative_to(root).as_posix()
        package = re.search(r'^package\s+([\w.]+);', content, re.M)
        rows.append({'path': path, 'package': package[1] if package else '',
                     'lines': len(content.splitlines()), 'sha256': digest(source)})
        for i, line in enumerate(content.splitlines(), 1):
            if any(p.lower() in line.lower() for p in patterns):
                warnings.append({'path': path, 'line': i, 'text': line.strip()})
    write_json(root / 'reports/source-index.json', rows)
    write_json(root / 'reports/decompiler-blockers.json', warnings)
    with zipfile.ZipFile(root / 'original' / JAR_NAME) as archive:
        top = [n[:-6] + '.java' for n in archive.namelist() if n.endswith('.class') and '$' not in n]
    actual = {p.relative_to(root / 'src/main/java').as_posix() for p in sources}
    coverage = {'javaFiles': len(rows), 'lines': sum(r['lines'] for r in rows),
                'topLevelClasses': len(top), 'missingTopLevelSources': sorted(set(top) - actual),
                'decompilerBlockers': len(warnings)}
    write_json(root / 'reports/coverage.json', coverage)
    print(json.dumps(coverage, ensure_ascii=False))


def verify(args):
    root = args.workspace
    if digest(root / 'original' / JAR_NAME) != JAR_SHA:
        raise ValueError('Original binary changed.')
    manifest = json.loads((root / 'provenance/reference-files.json').read_text(encoding='utf-8'))
    reference_root = root / 'reference/vineflower'
    changed = [name for name, sha in manifest.items() if not (reference_root / name).is_file() or digest(reference_root / name) != sha]
    if changed:
        raise ValueError('Immutable reference changed: ' + ', '.join(changed[:10]))
    actual = {p.relative_to(reference_root).as_posix() for p in reference_root.rglob('*') if p.is_file()}
    if actual != set(manifest):
        raise ValueError('Unexpected files were added to immutable reference.')
    cfr_manifest = root / 'provenance/cfr-files.json'
    if cfr_manifest.exists():
        for name, sha in json.loads(cfr_manifest.read_text(encoding='utf-8')).items():
            p = root / 'reference/cfr' / name
            if not p.is_file() or digest(p) != sha:
                raise ValueError('CFR reference changed: ' + name)
    baseline = json.loads((root / 'provenance/working-baseline.json').read_text(encoding='utf-8'))
    modified = [name for name, sha in baseline.items() if not (root / name).is_file() or digest(root / name) != sha]
    working_actual = {p.relative_to(root).as_posix() for p in (root / 'src').rglob('*') if p.is_file()}
    result = {'originalHashVerified': True, 'referenceFilesVerified': len(manifest), 'workingFilesModified': modified,
              'workingFilesAdded': sorted(working_actual - set(baseline)),
              'meaning': 'Integrity only, not compilation or behavior equivalence.'}
    write_json(root / 'reports/integrity.json', result)
    print(json.dumps(result, ensure_ascii=False))


def compare(args):
    root = args.workspace
    verify(args)
    download(CFR_URL, root / 'tools-cache/cfr-0.152.jar', CFR_SHA)
    output = root / 'reference/cfr'
    if output.exists() and any(output.iterdir()):
        raise ValueError('CFR reference exists; choose a new workspace to regenerate it.')
    deps = json.loads((root / 'provenance/dependencies.json').read_text(encoding='utf-8'))
    for item in deps:
        if digest(Path(item['path'])) != item['sha256']:
            raise ValueError('Dependency changed: ' + item['path'])
    argfile = root / 'reports/cfr.args'
    jvm_args(argfile, ['-Xmx4G', '-jar', root / 'tools-cache/cfr-0.152.jar', root / 'original' / JAR_NAME,
                      '--outputdir', output, '--silent', 'true', '--extraclasspath', os.pathsep.join(x['path'] for x in deps)])
    with (root / 'reports/cfr.log').open('w', encoding='utf-8') as log:
        subprocess.run([str(args.java_home / 'bin/java.exe'), '@' + str(argfile)], stdout=log, stderr=subprocess.STDOUT, check=True)
    files = {p.relative_to(output).as_posix(): digest(p) for p in sorted(output.rglob('*')) if p.is_file()}
    if not any(n.endswith('.java') for n in files):
        raise ValueError('CFR generated no sources.')
    write_json(root / 'provenance/cfr-files.json', files)
    print(json.dumps({'comparisonFiles': len(files), 'cfrSha256': CFR_SHA}))


def compile_check(args):
    root = args.workspace
    deps = json.loads((root / 'provenance/dependencies.json').read_text(encoding='utf-8'))
    for item in deps:
        if digest(Path(item['path'])) != item['sha256']:
            raise ValueError('Dependency changed: ' + item['path'])
    source_root = root / 'src/main/java'
    sources = sorted(source_root.rglob('*.java'))
    name = 'compile-check'
    if args.source:
        sources = [(source_root / safe_member(name)).resolve() for name in args.source]
        if any(source_root.resolve() not in p.parents or not p.is_file() or p.suffix != '.java' for p in sources):
            raise ValueError('Focused sources must be existing Java files under src/main/java.')
        name = 'focused-compile-check'
        original = root / 'original' / JAR_NAME
        if digest(original) != JAR_SHA:
            raise ValueError('Original binary changed.')
        deps.append({'path': str(original)})
    classes = root / 'build' / name
    classes.mkdir(parents=True, exist_ok=True)
    argfile = root / 'reports' / (name + '.args')
    jvm_args(argfile, ['--release', '21', '-encoding', 'UTF-8', '-proc:none', '-XDrawDiagnostics', '-Xmaxerrs', '5000',
                      '-sourcepath', '', '-classpath', os.pathsep.join(x['path'] for x in deps), '-d', classes, *sources])
    with (root / 'reports' / (name + '.log')).open('w', encoding='utf-8') as log:
        result = subprocess.run([str(args.java_home / 'bin/javac.exe'), '@' + str(argfile)], stdout=log, stderr=subprocess.STDOUT)
    logtext = (root / 'reports' / (name + '.log')).read_text(encoding='utf-8', errors='replace')
    diagnostics = re.findall(r'^(.*\.java):(\d+):(\d+): (compiler\.err\.[^\r\n]+)', logtext, re.M)
    counts = {}
    for _, _, _, code in diagnostics:
        key = code.split(':', 1)[0]
        counts[key] = counts.get(key, 0) + 1
    summary = {'exitCode': result.returncode, 'sourceFiles': len(sources), 'diagnosticCount': len(diagnostics),
               'categories': counts, 'buildable': result.returncode == 0,
               'scope': 'selected files against original binary' if args.source else 'all recovered sources',
               'selectedSources': args.source,
               'deploymentAllowed': False, 'note': 'No mod jar is packaged. Compile success would not prove runtime equivalence.'}
    write_json(root / 'reports' / (name + '.json'), summary)
    by_name = {}
    for p in sources:
        by_name.setdefault(p.name, []).append(p.relative_to(root).as_posix())
    write_json(root / 'reports' / (name + '-diagnostics.json'), [
        {'file': name, 'sourceCandidates': by_name.get(Path(name).name, []), 'line': int(line), 'column': int(col), 'diagnostic': detail}
        for name, line, col, detail in diagnostics])
    if not args.source:
        grouped = {}
        for filename, _, _, detail in diagnostics:
            entry = grouped.setdefault(filename, {'count': 0, 'categories': set()})
            entry['count'] += 1
            entry['categories'].add(detail.split(':', 1)[0])
        lines = ['# 完整编译阻碍索引', '', '这是构建恢复清单，不是运行时缺陷清单。', '',
                 '| 文件 | 诊断数 | 类别 |', '|---|---:|---|']
        for filename, entry in sorted(grouped.items(), key=lambda item: (-item[1]['count'], item[0])):
            links = ', '.join(f'[{filename}](../{p})' for p in by_name.get(Path(filename).name, [])) or filename
            lines.append(f"| {links} | {entry['count']} | {', '.join(sorted(entry['categories']))} |")
        (root / 'reports/COMPILE-BLOCKERS.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False))
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'index', 'verify', 'compare', 'compile-check'])
    parser.add_argument('--server', type=Path, default=SERVER)
    parser.add_argument('--client', type=Path, default=SERVER.parent / 'bmc5client/game')
    parser.add_argument('--workspace', type=Path, default=SERVER.parent / 'customnpcs-source')
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--research', action='store_true')
    parser.add_argument('--source', action='append', default=[], help='Compile-check only: source relative to src/main/java; repeatable. Uses original binary for other classes.')
    args = parser.parse_args()
    if args.source and args.action != 'compile-check':
        parser.error('--source is only valid for compile-check.')
    args.workspace = args.workspace.resolve()
    for active in (args.server.resolve(), args.client.resolve()):
        if args.workspace == active or active in args.workspace.parents or args.workspace in active.parents:
            parser.error('Recovery workspace must be separate from the server and client checkouts.')
    if args.action == 'prepare': prepare(args)
    elif args.action == 'index': index(args)
    elif args.action == 'verify': verify(args)
    elif args.action == 'compare': compare(args)
    else: return compile_check(args)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
