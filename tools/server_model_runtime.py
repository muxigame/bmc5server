"""Minimal official LWJGL core for YSM3's dedicated model parser; no GL/GLFW."""
from pathlib import Path
import json,os,platform,re,shutil,urllib.request,zipfile
from city_core import safe,digest

def selected(lock):
    rows=lock.get('serverModelRuntime',{}).get('libraries',[])
    if not rows:return []
    machine=platform.machine().lower()
    arch='arm64' if machine in ('arm64','aarch64') else 'x86' if machine in ('x86','i386','i686') else '' if machine in ('amd64','x86_64') else None
    system=platform.system().lower()
    if arch is None or system not in ('windows','linux','darwin'):raise RuntimeError('Unsupported dedicated model-library platform')
    suffix='natives-'+('macos' if system=='darwin' else system)+('-'+arch if arch else '')
    out=[r for r in rows if r['name']=='org.lwjgl:lwjgl:3.3.3' or r['name'].endswith(':'+suffix)]
    if len(out)!=2:raise RuntimeError('No pinned LWJGL native for this platform')
    return out

def matches(p,r):return p.is_file() and p.stat().st_size==r['size'] and digest(p)==r['sha256']

def setup(root,lock):
    rows=selected(lock)
    if not rows:return
    index=root/'.runtime/model-local-resources.json';sources=json.loads(index.read_text('utf8')) if index.exists() else {}
    for r in rows:
        dest=safe(root,r['path'])
        if dest.exists() and not matches(dest,r):raise RuntimeError('Modified model runtime resource: '+r['path'])
        if not dest.exists():
            dest.parent.mkdir(parents=True,exist_ok=True);source=Path(sources.get(r['path'],''))
            if source.is_file() and matches(source,r):
                try:os.link(source,dest)
                except OSError:shutil.copyfile(source,dest)
            else:
                part=dest.with_suffix('.partial')
                if part.exists():raise RuntimeError('Prior partial model download requires inspection')
                with urllib.request.urlopen(r['url'],timeout=120) as res,part.open('xb') as out:shutil.copyfileobj(res,out)
                if not matches(part,r):raise RuntimeError('Official model dependency checksum mismatch')
                part.replace(dest)
        if r.get('native'):
            n=r['native'];target=safe(root,'.runtime/model-natives/'+n['file'])
            if target.exists() and not matches(target,n):raise RuntimeError('Modified model native library')
            if not target.exists():
                with zipfile.ZipFile(dest) as archive:
                    if archive.namelist().count(n['member'])!=1:raise RuntimeError('Ambiguous native entry')
                    data=archive.read(n['member'])
                import hashlib
                if len(data)!=n['size'] or hashlib.sha256(data).hexdigest()!=n['sha256']:raise RuntimeError('Model native member checksum mismatch')
                target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as f:f.write(data)
    for name,rule in lock['serverModelRuntime'].get('removeExactClientFactoryOverrides',{}).items():
        path=safe(root,name);text=path.read_text('utf8')
        found=[key for key in rule['entries'] if re.search(r'(?m)^\s*'+re.escape(key)+r'\s*=',text)]
        if not found:continue
        if digest(path)!=rule['factorySHA256']:raise RuntimeError('Authored server dependency overrides require manual review: '+name)
        original=text
        for key in found:
            pattern=r'(?m)^[\t ]*'+re.escape(key)+r'\s*=\s*'+re.escape(json.dumps(rule['entries'][key]))+r'[\t ]*\r?$'
            text,count=re.subn(pattern,'',text)
            if count!=1:raise RuntimeError('Unexpected factory dependency override')
        backup=root/'.runtime/model-runtime-factory-fml.toml'
        if backup.exists() and digest(backup)!=rule['factorySHA256']:raise RuntimeError('Modified factory config rollback')
        if not backup.exists():backup.write_bytes(path.read_bytes())
        path.write_text(text,encoding='utf8',newline='')

def verify(root,lock):
    issues=[]
    for r in selected(lock):
        if not matches(safe(root,r['path']),r):issues.append('Model runtime '+r['path'])
        if r.get('native') and not matches(safe(root,'.runtime/model-natives/'+r['native']['file']),r['native']):issues.append('Model native '+r['native']['file'])
    return issues

def arguments(root,lock,original):
    rows=selected(lock)
    if not rows:return original
    if verify(root,lock):raise RuntimeError('Model runtime not installed or modified')
    jar=safe(root,next(r['path'] for r in rows if not r.get('native'))).resolve().as_posix()
    text=original.read_text('utf8');lines=text.splitlines();indexes=[i for i,s in enumerate(lines) if s.startswith('-p ')]
    if len(indexes)!=1 or '--add-modules ALL-MODULE-PATH' not in lines:raise RuntimeError('Unreviewed NeoForge argument-file layout')
    i=indexes[0];old=lines[i][3:].strip().strip('"');sep=';' if os.name=='nt' else ':'
    lines[i]='-p "'+jar+sep+old+'"'
    native=(root/'.runtime/model-natives').resolve().as_posix();lines.insert(0,'-Dorg.lwjgl.librarypath="'+native+'"')
    dest=root/'.runtime/server-model-args.txt';dest.write_text('\n'.join(lines)+'\n',encoding='utf8')
    (root/'.runtime/server-model-args-source.json').write_text(json.dumps({'original':str(original),'originalSHA256':digest(original),'generatedSHA256':digest(dest),'originalModified':False,'LWJGLCoreOnly':True,'GLFWLoadedByThisHelper':False},indent=2)+'\n',encoding='utf8')
    return dest
