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
        if record["modId"] != "mcef": raise ValueError("Unsupported local rebuild")
        result[name] = record
    return result

def locked_records(lock):
    result = {r["path"]:r for r in lock["files"] + lock["external"]}
    result.update(overrides(lock)); return list(result.values())

def validated_sources(root, lock, explicit=None):
    sources = {}
    for name, record in overrides(lock).items():
        project = record["sourceProject"]
        if len(PurePosixPath(project).parts) != 1 or project in (".", "..") or ":" in project or "\\" in project: raise ValueError("Invalid rebuild project")
        source = Path(explicit).resolve() if explicit else safe(root.parent / project, record["artifact"])
        if not source.is_file() or source.stat().st_size != record["size"] or digest(source) != record["sha256"]:
            raise RuntimeError("Build the pinned MCEF source first, or pass --mcef-jar with its checksum-matching JAR: " + str(source))
        sources[name] = source
    return sources

def install(root, destination_root, lock, explicit=None):
    original = {r["path"]:r for r in lock["files"]}
    sources = validated_sources(root, lock, explicit)
    for name, record in overrides(lock).items():
        source = sources[name]
        target = safe(destination_root, name)
        current = digest(target) if target.is_file() else None
        if target.exists() and not target.is_file(): raise RuntimeError("MCEF destination is not a regular file")
        if current == record["sha256"]: continue
        expected_old = original[name]["sha256"]
        if current not in (None, expected_old): raise RuntimeError("Refusing to replace modified local MCEF: " + str(target))
        if current is not None:
            backup = safe(root / ".runtime/rebuilt-originals", name); backup.parent.mkdir(parents=True, exist_ok=True)
            if backup.exists():
                if digest(backup) != expected_old: raise RuntimeError("Original MCEF backup has changed: " + str(backup))
            else:
                with target.open("rb") as src, backup.open("xb") as dst: shutil.copyfileobj(src, dst)
            if digest(backup) != expected_old: raise RuntimeError("Original MCEF backup checksum mismatch")
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix="mcef-rebuilt-", suffix=".tmp", delete=False) as f:
            staged = Path(f.name)
            with source.open("rb") as src: shutil.copyfileobj(src, f)
        try:
            if digest(staged) != record["sha256"]: raise RuntimeError("Rebuilt MCEF checksum mismatch")
            if (digest(target) if target.is_file() else None) != current: raise RuntimeError("MCEF changed during installation")
            staged.replace(target)
        finally:
            if staged.exists(): staged.unlink()
        print("Installed source-rebuilt MCEF " + record["version"], flush=True)
