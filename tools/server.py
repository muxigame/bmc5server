"""Portable, checksum-locked BMC5 development server bootstrap. Python 3.10+."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from local_rebuild import install as install_rebuilds, locked_records, overrides as rebuild_overrides, validated_sources, verify_rebuilds

if sys.version_info < (3, 10):
    sys.exit('Python 3.10+ required. On Windows try: py -3.12 tools/server.py ...')

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.runtime'


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def safe_path(name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe asset path: ' + name)
    dest = ROOT.joinpath(*p.parts)
    if not dest.resolve().is_relative_to(ROOT):
        raise ValueError('Asset escapes checkout: ' + name)
    return dest


def fetch(record, destination):
    if destination.is_file() and digest(destination) == record['sha256']:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_name(destination.name + '.partial')
    print('Downloading ' + record['url'], flush=True)
    request = urllib.request.Request(record['url'], headers={'User-Agent': 'muxigame-bmc5-bootstrap/1'})
    with urllib.request.urlopen(request, timeout=120) as response, partial.open('wb') as out:
        shutil.copyfileobj(response, out, 1024 * 1024)
    if digest(partial) != record['sha256']:
        raise RuntimeError('Checksum mismatch: ' + destination.name)
    partial.replace(destination)
    return destination


def java_binary(value):
    java = value or (str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
                     if os.environ.get('JAVA_HOME') else shutil.which('java'))
    if not java:
        raise RuntimeError('Install JDK 21+ and set JAVA_HOME or pass --java PATH.')
    result = subprocess.run([java, '-version'], capture_output=True, text=True)
    version = re.search(r'version "(\d+)', result.stderr + result.stdout)
    if result.returncode or not version or int(version[1]) < 21:
        raise RuntimeError('JDK 21+ required.')
    return java


def arguments_file(lock):
    return ROOT / 'libraries/net/neoforged/neoforge' / lock['neoforge'] / ('win_args.txt' if os.name == 'nt' else 'unix_args.txt')


def verify(lock):
    failures = verify_rebuilds(ROOT, lock)
    for record in lock.get('retired', []):
        if safe_path(record['path']).exists():
            failures.append('Retired asset still present: ' + record['path'] + ' (run setup)')
    for record in locked_records(lock):
        path = safe_path(record['path'])
        if not path.is_file() or path.stat().st_size != record['size'] or digest(path) != record['sha256']:
            failures.append(record['path'])
    if failures:
        raise RuntimeError('Missing/modified locked assets (setup restores missing files only):\n' + '\n'.join(failures[:30]))
    print('Verified %d runtime assets.' % (len(lock['files']) + len(lock['external'])), flush=True)


def setup(args, lock):
    validated_sources(ROOT, lock, getattr(args, 'mcef_jar', None), getattr(args, 'npc_jar', None))
    java = java_binary(args.java)
    bundle = Path(args.bundle).resolve() if args.bundle else CACHE / 'server-assets.zip'
    if args.bundle:
        if digest(bundle) != lock['bundle']['sha256']:
            raise RuntimeError('Local bundle checksum mismatch.')
    else:
        fetch(lock['bundle'], bundle)
    expected = {record['path']: record for record in lock['files']}
    retired = {record['path']: record for record in lock.get('retired', [])}
    rebuilt = rebuild_overrides(lock)
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != set(expected) | set(retired):
            raise RuntimeError('Bundle entries differ from runtime-lock.json.')
        for name in names:
            if name in retired or name in rebuilt:
                continue
            dest = safe_path(name)
            if dest.exists():
                if digest(dest) != expected[name]['sha256']:
                    raise RuntimeError('Refusing to overwrite modified asset: ' + name)
                continue
            dest.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(name) as src, dest.open('xb') as out:
                shutil.copyfileobj(src, out)
    for record in lock['external']:
        dest = safe_path(record['path'])
        if dest.exists() and digest(dest) != record['sha256']:
            raise RuntimeError('Refusing to overwrite modified asset: ' + record['path'])
        fetch(record, dest)
    install_rebuilds(ROOT, ROOT, lock, getattr(args, 'mcef_jar', None), getattr(args, 'npc_jar', None))
    for name, record in retired.items():
        dest = safe_path(name)
        if dest.exists():
            if digest(dest) != record['sha256']:
                raise RuntimeError('Refusing to retire modified asset: ' + name)
            backup = CACHE / 'retired' / name
            backup.parent.mkdir(parents=True, exist_ok=True)
            if backup.exists():
                raise RuntimeError('Retired backup already exists; inspect manually: ' + str(backup))
            dest.rename(backup)
    verify(lock)
    installer = fetch(lock['installer'], CACHE / 'neoforge-installer.jar')
    if not arguments_file(lock).is_file():
        print('Installing NeoForge and Minecraft dependencies...', flush=True)
        with (CACHE / 'install.log').open('w', encoding='utf-8') as log:
            result = subprocess.run([java, '-jar', str(installer), '--installServer', str(ROOT)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError('NeoForge installer failed; inspect .runtime/install.log.')
    for source, target in [('config-examples/server.properties', 'server.properties'),
                           ('config-examples/muxi-game-core.json', 'config/muxi-game-core.json'),
                           *[(f'config-examples/maid-sites/{name}.json', f'config/touhou_little_maid/sites/{name}.json') for name in ('llm', 'tts', 'stt')]]:
        dest = ROOT / target
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / source, dest)
    if args.accept_eula:
        (ROOT / 'eula.txt').write_text('# Accepted explicitly by the operator via --accept-eula\neula=true\n', encoding='utf-8')
    print('Setup complete. Review server.properties; run: python tools/server.py start', flush=True)


def read_properties():
    path = ROOT / 'server.properties'
    if not path.is_file():
        raise RuntimeError('Run setup first.')
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8-sig').splitlines()
                if line and not line.startswith('#') and '=' in line)


def command(args, lock):
    props = read_properties()
    if props.get('server-ip') not in ('127.0.0.1', '::1') and props.get('online-mode') != 'true':
        raise RuntimeError('Public binding requires online-mode=true in this development launcher. Custom identity/login deployments need a separately reviewed production launcher.')
    if not (ROOT / 'eula.txt').is_file() or not re.search(r'^eula=true\s*$', (ROOT / 'eula.txt').read_text(), re.M):
        raise RuntimeError('Read https://aka.ms/MinecraftEULA then run setup --accept-eula if you agree.')
    argfile = arguments_file(lock)
    if not argfile.is_file():
        raise RuntimeError('NeoForge not installed. Run setup.')
    java = java_binary(args.java)
    jvm = [java, '-Xms1G', '-Xmx' + args.memory, '-XX:+UseG1GC']
    if args.cpus:
        jvm.append('-XX:ActiveProcessorCount=' + str(args.cpus))
    if os.name == 'nt':
        # AF_UNIX has a short path limit. Do not use a deep user profile temp directory.
        short_root = Path(os.environ.get('SystemDrive', 'C:') + '/Temp')
        folder = short_root / ('bmc5-' + hashlib.sha256(str(ROOT).encode()).hexdigest()[:8])
        folder.mkdir(parents=True, exist_ok=True)
        jvm.append('-Djdk.net.unixdomain.tmpdir=' + str(folder))
    return jvm + ['@' + str(argfile.relative_to(ROOT)), 'nogui']


def smoke(args, lock):
    # Only this checkout's local loopback instance; never a production control interface.
    import queue
    import threading
    import time
    props = read_properties()
    if props.get('server-ip') != '127.0.0.1' or props.get('enable-rcon') != 'false':
        raise RuntimeError('Smoke requires server-ip=127.0.0.1 and enable-rcon=false.')
    port = int(props.get('server-port', '25585'))
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))
    verify(lock)
    CACHE.mkdir(exist_ok=True)
    events = queue.Queue()
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    process = subprocess.Popen(command(args, lock), cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', creationflags=flags)
    def pump():
        for line in process.stdout:
            events.put(line)
        events.put(None)
    threading.Thread(target=pump, daemon=True).start()
    ready = False
    deadline = time.monotonic() + args.timeout
    try:
        with (CACHE / 'smoke.log').open('w', encoding='utf-8') as log:
            while time.monotonic() < deadline:
                try:
                    line = events.get(timeout=1)
                except queue.Empty:
                    if process.poll() is not None:
                        break
                    continue
                if line is None:
                    break
                log.write(line)
                log.flush()
                if 'Done (' in line and 'For help' in line:
                    with socket.create_connection(('127.0.0.1', port), timeout=5):
                        pass
                    ready = True
                    print(line.strip(), flush=True)
                    break
                if 'ERROR' in line or 'Starting minecraft server' in line:
                    print(line.strip()[:500], flush=True)
            if process.poll() is None:
                process.stdin.write('stop\n')
                process.stdin.flush()
                process.wait(timeout=180)
            while not events.empty():
                line = events.get_nowait()
                if line:
                    log.write(line)
    finally:
        if process.poll() is None:
            # Kill only the child created here, never another Java/server process.
            process.kill()
            process.wait()
    result = {'ready': ready, 'exitCode': process.returncode, 'port': port, 'baseline': lock['baseline']}
    (CACHE / 'smoke-result.json').write_text(json.dumps(result, indent=2) + '\n')
    if not ready or process.returncode != 0:
        raise RuntimeError('Smoke failed. Inspect .runtime/smoke.log. ' + str(result))
    print('PASS: server reached Done, loopback port accepted, and graceful stop exited 0.', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['setup', 'verify', 'start', 'smoke'])
    parser.add_argument('--java', help='Path to Java 21+ executable')
    parser.add_argument('--mcef-jar', help='Checksum-matching source-rebuilt MCEF JAR (setup only)')
    parser.add_argument('--npc-jar', help='Checksum-matching CustomNPCs stock.4 JAR (setup only)')
    parser.add_argument('--bundle', help='Use a local checksum-matching release ZIP (setup only)')
    parser.add_argument('--accept-eula', action='store_true', help='Explicitly accept Minecraft EULA')
    parser.add_argument('--memory', default='6G', help='Maximum heap, e.g. 4G or 8G')
    parser.add_argument('--cpus', type=int, default=0, help='Limit active processors for development')
    parser.add_argument('--timeout', type=int, default=900, help='Smoke startup timeout in seconds')
    args = parser.parse_args()
    if not re.fullmatch(r'[1-9][0-9]*[MG]', args.memory) or args.cpus < 0:
        parser.error('Invalid memory/CPU limit')
    lock = json.loads((ROOT / 'runtime-lock.json').read_text(encoding='utf-8'))
    if args.action == 'setup':
        setup(args, lock)
    elif args.action == 'verify':
        verify(lock)
    elif args.action == 'smoke':
        smoke(args, lock)
    else:
        sys.exit(subprocess.call(command(args, lock), cwd=ROOT))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as exc:
        print('ERROR: ' + str(exc), file=sys.stderr)
        sys.exit(1)
