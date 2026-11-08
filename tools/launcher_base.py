"""Install a hash-pinned public launcher baseline, then the city-core overlay.

No accounts, worlds, launcher settings or launch actions are copied. Seed files
preserve local edits; exact known factory seeds migrate to the pinned release.
Managed upgrades accept only explicitly known hashes.
"""
from pathlib import Path
import concurrent.futures, hashlib, json, os, shutil, urllib.request, urllib.parse
from city_core import safe, digest

IMMUTABLE={'.jar','.zip','.png','.jpg','.jpeg','.webp','.avif','.ogg','.wav','.mp3','.ttf','.otf','.ysm','.ysmc','.dll','.pak','.bin'}

def matches(path, rec):
    return path.is_file() and path.stat().st_size==rec['size'] and digest(path)==rec['sha256']

def load(root, lock):
    ref=lock['launcherBase'];p=safe(root,ref['path'])
    if digest(p)!=ref['sha256']:raise RuntimeError('Launcher baseline lock changed')
    data=json.loads(p.read_text('utf8'));names=[r['path'] for r in data['files']]
    if len(names)!=len(set(names)):raise ValueError('Duplicate launcher paths')
    for name in names:safe(root,name)
    return data

def effective(base, lock):
    skip={r['path'] for r in lock.get('localBuilds',[])}
    skip.update(o['path'] for r in lock.get('cityCore',{}).get('modules',[]) for o in r.get('superseded',[]))
    return [r for r in base['files'] if r['path'] not in skip]

def resource(root, base, rec, local):
    p=Path(local.get(rec['sourcePath'],''))
    if p.is_file() and matches(p,rec):return p
    cache=safe(root/'.runtime/launcher-downloads',rec['sha256'])
    if matches(cache,rec):return cache
    cache.parent.mkdir(parents=True,exist_ok=True)
    partial=cache.with_suffix('.partial')
    if partial.exists():raise RuntimeError('Unfinished prior download; inspect before retry: '+str(partial))
    url=rec.get('url') or base['filesBaseURL']+urllib.parse.quote(rec['sourcePath'],safe='/')
    if rec.get('url'):
        approved=urllib.parse.urlsplit(base['filesBaseURL']);chosen=urllib.parse.urlsplit(url)
        if chosen.scheme!='https' or (chosen.scheme,chosen.netloc)!=(approved.scheme,approved.netloc) or chosen.username or chosen.password:
            raise ValueError('Pinned file URL must use the approved HTTPS origin')
    request=urllib.request.Request(url,headers={'User-Agent':'BMC-city-core-integrator/1'})
    with urllib.request.urlopen(request,timeout=120) as response,partial.open('xb') as out:
        shutil.copyfileobj(response,out,2**20)
    if not matches(partial,rec):raise RuntimeError('Public launcher object changed: '+rec['sourcePath'])
    partial.replace(cache);return cache

def setup(root, destination, lock, *, local_resources=None, replace_owned_seed=False):
    base=load(root,lock);rows=effective(base,lock);local=local_resources or {}
    old={r['path']:r for r in base.get('knownPrevious',[])};allowed={r['path'] for r in rows}
    allowed.update(r.get('installedPath',r['path']) for r in lock.get('localBuilds',[]))
    allowed.update(r['path'] for r in lock.get('external',[]))
    rebuild_retired={o['path']:o for r in lock.get('localBuilds',[])+lock.get('cityCore',{}).get('modules',[]) for o in r.get('superseded',[])}
    allowed.update(rebuild_retired)
    changes=[];retired=[];created=set()
    for rec in rows:
        p=safe(destination,rec['path'])
        if matches(p,rec):continue
        if p.exists():
            if rec['policy']=='Seed' and not replace_owned_seed and (rec['path'] not in old or not matches(p,old[rec['path']])):continue
            if rec['path'] not in old or not matches(p,old[rec['path']]):raise RuntimeError('Refusing modified baseline file: '+rec['path'])
        changes.append((rec,p,p.exists()))
    for jar in (destination/'mods').glob('*.jar'):
        name=jar.relative_to(destination).as_posix()
        if name in allowed:
            if name in rebuild_retired and not matches(jar,rebuild_retired[name]):raise RuntimeError('Modified superseded local rebuild: '+name)
            continue
        if name not in old or not matches(jar,old[name]):raise RuntimeError('Unknown extra mod: '+name)
        retired.append((jar,old[name]))
    for p,rec in retired+[(p,old[r['path']]) for r,p,existed in changes if existed]:
        b=safe(root/'.runtime/launcher-retired',rec['path'])
        if b.exists() and not matches(b,rec):raise RuntimeError('Modified baseline rollback object: '+str(b))
    # Fetch and verify every replacement before the first mutation.
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        sources=list(pool.map(lambda item:resource(root,base,item[0],local),changes))
    stats={'changed':0,'linkedReadOnlyFiles':0,'linkedLogicalBytes':0,'copiedBytes':0,'retired':0,'worldCopies':0,'sourceWrites':0}
    backup=root/'.runtime/launcher-retired'
    for p,rec in retired:
        b=safe(backup,rec['path'])
        b.parent.mkdir(parents=True,exist_ok=True)
        if b.exists():
            if not matches(b,rec):raise RuntimeError('Modified rollback object: '+str(b))
            p.unlink()
        else:p.replace(b)
        stats['retired']+=1
    for (rec,p,existed),source in zip(changes,sources):
        p.parent.mkdir(parents=True,exist_ok=True)
        if existed:
            previous=old[rec['path']]
            if not matches(p,previous):raise RuntimeError('Baseline changed during installation')
            b=safe(backup,rec['path'])
            b.parent.mkdir(parents=True,exist_ok=True)
            if b.exists():
                if not matches(b,previous):raise RuntimeError('Modified baseline rollback copy')
                p.unlink()
            else:p.replace(b)
        else:created.add(rec['path'])
        # Atomic new files only: never write through a shared immutable resource.
        if source.suffix.lower() in IMMUTABLE or Path(rec['sourcePath']).suffix.lower() in IMMUTABLE:
            try:os.link(source,p);stats['linkedReadOnlyFiles']+=1;stats['linkedLogicalBytes']+=rec['size']
            except OSError:shutil.copyfile(source,p);stats['copiedBytes']+=rec['size']
        else:shutil.copyfile(source,p);stats['copiedBytes']+=rec['size']
        if not matches(p,rec):raise RuntimeError('Installed launcher file mismatch')
        stats['changed']+=1
    (root/'.runtime').mkdir(exist_ok=True)
    (root/'.runtime/launcher-base-install.json').write_text(json.dumps({'version':base['pack']['version'],'counts':stats,'preservesAuthoredSeedFiles':not replace_owned_seed,'existingWorldsOpened':False},indent=2)+'\n',encoding='utf8')
    return created,stats

def verify(root,destination,lock):
    base=load(root,lock);issues=[]
    for rec in effective(base,lock):
        p=safe(destination,rec['path'])
        if rec['policy']=='Seed':
            if not p.is_file():issues.append(rec['path'])
        elif not matches(p,rec):issues.append(rec['path'])
    return issues
