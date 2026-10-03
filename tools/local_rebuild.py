"""Install explicitly pinned local source rebuilds, preserving known original binaries."""
from pathlib import Path, PurePosixPath
import hashlib, shutil, tempfile

def digest(path):
    with path.open("rb") as f:
        h = hashlib.sha256()
        for chunk in iter(lambda:f.read(1024*1024), b""): h.update(chunk)
    return h.hexdigest()

def safe(base, name):
    parts = PurePosixPath(name)
    if parts.is_absolute() or ".." in parts.parts or ":" in name or "\\" in name: raise ValueError("Unsafe rebuilt artifact path")
    target = base.joinpath(*parts.parts)
    if not target.resolve().is_relative_to(base.resolve()): raise ValueError("Rebuilt artifact escapes destination")
    return target

def overrides(lock):
    original = {r["path"]:r for r in lock["files"]}
    result = {}
    for record in lock.get("localBuilds", []):
        name = record["path"]
        if name not in original or name in result: raise ValueError("Rebuild must uniquely replace a bundled original")
        if record["modId"] not in ("mcef", "customnpcs"): raise ValueError("Unsupported local rebuild")
        safe(Path.cwd(), record.get("installedPath", name))
        result[name] = record
    return result

def locked_records(lock):
    result = {r["path"]:r for r in lock["files"] + lock["external"]}
    for name, record in overrides(lock).items():
        result.pop(name)
        installed = record.get("installedPath", name)
        if installed in result: raise ValueError("Rebuild destination collides with a locked asset")
        result[installed] = dict(record, path=installed)
    return list(result.values())

def validated_sources(root, lock, explicit=None, npc_explicit=None):
    sources = {}
    for name, record in overrides(lock).items():
        project = record["sourceProject"]
        if len(PurePosixPath(project).parts) != 1 or project in (".", "..") or ":" in project or "\\" in project: raise ValueError("Invalid rebuild project")
        selected = npc_explicit if record['modId'] == 'customnpcs' else explicit
        source = Path(selected).resolve() if selected else safe(root.parent / project, record["artifact"])
        if not source.is_file() or source.stat().st_size != record["size"] or digest(source) != record["sha256"]:
            option = '--npc-jar' if record['modId'] == 'customnpcs' else '--mcef-jar'
            raise RuntimeError("Provide the pinned " + record['modId'] + " build via " + option + ": " + str(source))
        sources[name] = source
    return sources

def retired_rebuilds(lock, name, record):
    original = {r['path']: r for r in lock['files']}
    rows = list(record.get('superseded', []))
    if record.get('installedPath', name) != name: rows.append(original[name])
    if len({r['path'] for r in rows}) != len(rows): raise ValueError('Duplicate superseded path')
    protected = {r['path'] for r in locked_records(lock)}
    if any(r['path'] in protected for r in rows): raise ValueError('Superseded path is still active')
    return rows


def verify_rebuilds(destination_root, lock):
    failures = []
    for name, record in overrides(lock).items():
        for old in retired_rebuilds(lock, name, record):
            if safe(destination_root, old['path']).exists(): failures.append('Superseded rebuild still present: ' + old['path'])
        try:
            check_npc_duplicates(destination_root, record, {safe(destination_root, record.get('installedPath', name))})
        except RuntimeError as error:
            failures.append(str(error))
    return failures


def check_npc_duplicates(destination_root, record, known):
    if record['modId'] != 'customnpcs': return
    mods = safe(destination_root, record.get('installedPath', record['path'])).parent
    for jar in mods.glob('*.jar'):
        if jar.name.lower().startswith('customnpcs-') and jar not in known:
            raise RuntimeError('Unrecognized CustomNPCs JAR; inspect before setup: ' + str(jar))


def install(root, destination_root, lock, explicit=None, npc_explicit=None):
    original = {r["path"]:r for r in lock["files"]}
    sources = validated_sources(root, lock, explicit, npc_explicit)
    plans = []
    # Check every rebuild before changing any target (including MCEF).
    for name, record in overrides(lock).items():
        source = sources[name]
        installed = record.get('installedPath', name)
        target = safe(destination_root, installed)
        current = digest(target) if target.is_file() else None
        if target.exists() and not target.is_file(): raise RuntimeError("Rebuild destination is not a regular file")
        expected_old = original[name]["sha256"] if installed == name else None
        if current not in (None, expected_old, record['sha256']): raise RuntimeError("Refusing to replace modified local rebuild: " + str(target))
        backups = []
        retired = retired_rebuilds(lock, name, record)
        check_npc_duplicates(destination_root, record, {target, *(safe(destination_root, old['path']) for old in retired)})
        candidates = retired + ([original[name]] if current == expected_old and current is not None else [])
        for old in candidates:
            old_path = safe(destination_root, old['path'])
            if not old_path.exists(): continue
            if not old_path.is_file() or digest(old_path) != old['sha256']: raise RuntimeError('Refusing to retire modified rebuild: ' + str(old_path))
            backup = safe(root / '.runtime/rebuilt-originals', old['path'])
            if backup.exists():
                if not backup.is_file() or digest(backup) != old['sha256']: raise RuntimeError('Original rebuild backup has changed: ' + str(backup))
            backups.append((old_path, backup, old['sha256']))
        plans.append((record, source, target, current, backups, retired))
    for record, source, target, current, backups, retired in plans:
        for old_path, backup, expected in backups:
            if digest(old_path) != expected: raise RuntimeError('Rebuild changed during backup')
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                with old_path.open('rb') as src, backup.open('xb') as dst: shutil.copyfileobj(src, dst)
            if digest(backup) != expected: raise RuntimeError('Rebuild backup checksum mismatch')
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix="local-rebuilt-", suffix=".tmp", delete=False) as f:
            staged = Path(f.name)
            with source.open("rb") as src: shutil.copyfileobj(src, f)
        try:
            if digest(staged) != record["sha256"]: raise RuntimeError("Rebuilt artifact checksum mismatch")
            if (digest(target) if target.is_file() else None) != current: raise RuntimeError("Rebuild changed during installation")
            staged.replace(target)
        finally:
            if staged.exists(): staged.unlink()
        for old in retired:
            old_path = safe(destination_root, old['path'])
            if old_path.exists():
                if digest(old_path) != old['sha256']: raise RuntimeError('Rebuild changed before retirement')
                backup = safe(root / '.runtime/rebuilt-originals', old['path'])
                if not backup.is_file() or digest(backup) != old['sha256']: raise RuntimeError('Retirement requires verified backup')
                old_path.unlink()
        print("Installed local rebuild " + record['modId'] + ' ' + record["version"], flush=True)
