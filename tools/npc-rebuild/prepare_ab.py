from pathlib import Path
import os,shutil,subprocess,zipfile,json
R=Path(r'D:\CPN\customnpcs-source'); base=Path(r'D:\CPN\work\npc-audit-server'); home=Path(r'D:\CPN\work\npc-rebuild-ab')
home.mkdir(exist_ok=True)
source=(Path(__file__).parent/'NpcAudit.java').read_text(encoding='utf-8')
(home/'NpcAudit.java').write_text(source,encoding='utf-8')
deps=json.loads((R/'provenance/dependencies.json').read_text(encoding='utf-8'))
original=R/'original/CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar'
cp=os.pathsep.join([str(original),*[d['path'] for d in deps]])
classes=home/'classes';classes.mkdir(exist_ok=True)
args=['--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),str(home/'NpcAudit.java')]
(home/'javac.args').write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
subprocess.run([r'D:\CPN\dependencies\jdk-21.0.12.1+1\bin\javac.exe','@'+str(home/'javac.args')],check=True)
jar=home/'npc-audit-local-only.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="All Rights Reserved"\n[[mods]]\nmodId="npc_audit"\nversion="1.0.0"\ndisplayName="Local rebuild A/B harness"\n[[dependencies.npc_audit]]\nmodId="customnpcs"\ntype="required"\nversionRange="[0,)"\nordering="AFTER"\nside="SERVER"\n')
candidate=Path(json.loads((R/'reports/rebuild-latest.json').read_text())['artifact'])
for label,mod in [('original',original),('rebuilt',candidate)]:
 dest=home/label
 if dest.exists():raise ValueError('Refuse to overwrite existing test root '+str(dest))
 dest.mkdir()
 shutil.copytree(base/'libraries',dest/'libraries',copy_function=os.link)
 shutil.copytree(base/'config',dest/'config')
 (dest/'mods').mkdir()
 for f in (base/'mods').glob('*.jar'):
  if not f.name.startswith(('CustomNPCs','npc-audit')):os.link(f,dest/'mods'/f.name)
 shutil.copyfile(mod,dest/'mods'/original.name)
 shutil.copyfile(jar,dest/'mods'/jar.name)
 shutil.copyfile(base/'server.properties',dest/'server.properties')
 (dest/'ab.flag').touch();(dest/'eula.txt').write_text('eula=true\n')
print(home)
