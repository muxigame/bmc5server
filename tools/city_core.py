"""Guard upgrades of the explicitly locked city modules; never open worlds."""
from pathlib import Path, PurePosixPath
import hashlib, re, shutil, zipfile


def digest(path):
    with path.open('rb') as f:
        h = hashlib.sha256()
        for b in iter(lambda: f.read(1024 * 1024), b''):
            h.update(b)
    return h.hexdigest()


def safe(root, name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or ':' in name or '\\' in name:
        raise ValueError('Unsafe city module path')
    dest = root.joinpath(*p.parts)
    if not dest.resolve().is_relative_to(root.resolve()):
        raise ValueError('City module path escapes installation')
    return dest


def records(lock):
    rows = lock.get('cityCore', {}).get('modules', [])
    active = {r.get('installedPath', r['path']): r
              for r in lock.get('localBuilds', []) + lock['files'] + lock['external']}
    ids = set()
    for r in rows:
        if r['modId'] in ids or r['path'] not in active:
            raise ValueError('Duplicate or unlocked city module')
        a = active[r['path']]
        if r['sha256'] != a['sha256'] or r['size'] != a['size']:
            raise ValueError('City module differs from runtime asset lock')
        ids.add(r['modId'])
        safe(Path.cwd(), r['path'])
        for old in r.get('superseded', []):
            if old['path'] in active:
                raise ValueError('Superseded city module is still locked active')
            safe(Path.cwd(), old['path'])
    return rows


def mod_ids(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        name = next((x for x in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml') if x in names), None)
        result = set()
        table = ''
        for line in z.read(name).decode('utf8').splitlines() if name else []:
            if line.strip().startswith('['):
                table = line.strip().split('#', 1)[0].strip()
            match = re.match(r'^\s*modId\s*=\s*[\"\x27]([^\"\x27]+)', line)
            if table == '[[mods]]' and match:
                result.add(match[1])
        return result


def preflight(root, destination, lock):
    """Validate every retirement and conflicting installed module before any write."""
    rows = records(lock)
    if not rows:
        return
    by_id = {r['modId']: r for r in rows}
    for r in rows:
        for v in [r, *r.get('superseded', [])]:
            p = safe(destination, v['path'])
            if p.exists() and (not p.is_file() or p.stat().st_size != v['size'] or digest(p) != v['sha256']):
                raise RuntimeError('Refusing to replace modified city module: ' + str(p))
        for old in r.get('superseded', []):
            backup = safe(root / '.runtime/city-core-retired', old['path'])
            if backup.exists() and (not backup.is_file() or digest(backup) != old['sha256']):
                raise RuntimeError('Modified city module rollback copy: ' + str(backup))
    for jar in (destination / 'mods').glob('*.jar'):
        for mod_id in mod_ids(jar) & by_id.keys():
            r = by_id[mod_id]
            known = {safe(destination, v['path']) for v in [r, *r.get('superseded', [])]}
            # CustomNPCs retirements are already guarded by local_rebuild.py.
            for v in lock.get('localBuilds', []):
                if v.get('modId') == mod_id:
                    known.update(safe(destination, o['path']) for o in v.get('superseded', []))
                    known.add(safe(destination, v['path']))
            if jar not in known:
                raise RuntimeError('Unrecognized duplicate city module; inspect before setup: ' + str(jar))


def retire(root, destination, lock):
    preflight(root, destination, lock)
    for r in records(lock):
        target = safe(destination, r['path'])
        if not target.is_file() or digest(target) != r['sha256']:
            raise RuntimeError('New city module must be verified before retirement')
    for r in records(lock):
        for old in r.get('superseded', []):
            p = safe(destination, old['path'])
            if not p.exists():
                continue
            backup = safe(root / '.runtime/city-core-retired', old['path'])
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                with p.open('rb') as src, backup.open('xb') as dst:
                    shutil.copyfileobj(src, dst)
            if digest(p) != old['sha256'] or digest(backup) != old['sha256']:
                raise RuntimeError('City module changed during rollback preservation')
            p.unlink()


def verify(root, destination, lock):
    try:
        preflight(root, destination, lock)
        failures = []
        for r in records(lock):
            p = safe(destination, r['path'])
            if not p.is_file() or digest(p) != r['sha256']:
                failures.append('Missing city module: ' + r['path'])
            if any(safe(destination, o['path']).exists() for o in r.get('superseded', [])):
                failures.append('Superseded city module remains: ' + r['modId'])
        return failures
    except (ValueError, RuntimeError, zipfile.BadZipFile) as e:
        return [str(e)]
