"""Pinned NPC model/preset kit. Keeps existing authored resources; never reads worlds."""
from pathlib import Path, PurePosixPath
import hashlib, json, shutil, stat, tempfile, urllib.parse, urllib.request, zipfile

MAX_BYTES=64*1024*1024

def digest(data): return hashlib.sha256(data).hexdigest()

def safe(root,name):
    p=PurePosixPath(name)
    allowed=('config/yes_steve_model/custom/cpn_steve/','config/yes_steve_model/custom/cpn_alex/','customnpcs/designs/','licenses/','docs/')
    if not name.startswith(allowed) or p.is_absolute() or '..' in p.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe NPC kit path: '+name)
    dest=root.joinpath(*p.parts)
    if not dest.resolve().is_relative_to(root.resolve()): raise ValueError('NPC kit path escapes target')
    return dest

def cached(root,record,bundle=None):
    if bundle: path=Path(bundle)
    else:
        path=root/'.runtime/npc-design-kit'/(record['sha256']+'.zip')
        if not path.exists():
            u=urllib.parse.urlsplit(record['url'])
            if u.scheme!='https' or not u.hostname or u.username or u.password: raise ValueError('Kit requires credential-free HTTPS')
            path.parent.mkdir(parents=True,exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=path.parent,suffix='.partial',delete=False) as f:
                tmp=Path(f.name)
                try:
                    req=urllib.request.Request(record['url'],headers={'User-Agent':'muxigame-npc-kit/1'})
                    with urllib.request.urlopen(req,timeout=120) as response:
                        remaining=record['size']+1
                        while remaining:
                            chunk=response.read(min(1024*1024,remaining))
                            if not chunk: break
                            f.write(chunk);remaining-=len(chunk)
                    f.close()
                    if tmp.stat().st_size!=record['size'] or digest(tmp.read_bytes())!=record['sha256']: raise ValueError('NPC kit download checksum mismatch')
                    tmp.replace(path)
                finally: tmp.unlink(missing_ok=True)
    if path.stat().st_size!=record['size'] or digest(path.read_bytes())!=record['sha256']: raise ValueError('NPC kit size/SHA-256 mismatch')
    return path

def inspect(path,destination):
    with zipfile.ZipFile(path) as z:
        infos=z.infolist();names=[x.filename for x in infos]
        if len(names)>160 or len(set(names))!=len(names) or sum(x.file_size for x in infos)>MAX_BYTES: raise ValueError('Invalid NPC kit archive')
        if any(x.is_dir() or stat.S_ISLNK(x.external_attr>>16) for x in infos): raise ValueError('NPC kit requires regular files')
        manifest=json.loads(z.read('kit-manifest.json'))
        if manifest['schema']!=1: raise ValueError('Unsupported NPC kit schema')
        rows=manifest['files']
        if len({r['path'] for r in rows})!=len(rows) or set(names)!={'kit-manifest.json',*(r['path'] for r in rows)}: raise ValueError('NPC kit entries differ from manifest')
        plan=[]
        for row in rows:
            target=safe(destination,row['path']);data=z.read(row['path'])
            if len(data)!=row['size'] or digest(data)!=row['sha256']: raise ValueError('NPC kit entry checksum mismatch')
            if target.exists() and not target.is_file(): raise ValueError('NPC kit target is not a regular file')
            plan.append((row,target,data))
        return plan

def install(root,destination,lock,bundle=None):
    record=lock.get('npcDesignKit')
    if not record: return {'installed':0,'preserved':0}
    plan=inspect(cached(root,record,bundle),destination)
    installed=preserved=0
    # Validate the entire archive before writing; existing authored resources are never overwritten.
    for row,target,data in plan:
        if target.exists(): preserved+=1;continue
        target.parent.mkdir(parents=True,exist_ok=True)
        with target.open('xb') as f: f.write(data)
        installed+=1
    print('NPC design kit: installed %d files; preserved %d existing resources.'%(installed,preserved),flush=True)
    return {'installed':installed,'preserved':preserved}

def verify(root,destination,lock):
    record=lock.get('npcDesignKit')
    if not record: return []
    return ['Missing NPC kit asset: '+row['path'] for row in record['files'] if not safe(destination,row['path']).is_file()]


if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--game',required=True);p.add_argument('--bundle');a=p.parse_args()
    root=Path(__file__).resolve().parents[1]
    install(root,Path(a.game).resolve(),json.loads((root/'runtime-lock.json').read_text(encoding='utf-8-sig')),a.bundle)
