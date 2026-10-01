import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Compilation-only copies. Original dependencies and runtime jars are never modified. */
public class AccessOverlay {
    record Rule(String access, String member) {}
    static Map<String,List<Rule>> rules = new HashMap<>();
    static int access(int old, String spec) {
        int level = spec.startsWith("public") ? 1 : spec.startsWith("protected") ? 4 : spec.startsWith("private") ? 2 : 0;
        int result = (old & ~7) | level;
        if (spec.endsWith("-f")) result &= ~Opcodes.ACC_FINAL;
        if (spec.endsWith("+f")) result |= Opcodes.ACC_FINAL;
        return result;
    }
    public static void main(String[] args) throws Exception {
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String clean = line.split("#",2)[0].trim();
            if (clean.isEmpty()) continue;
            String[] p=clean.split("\\s+");
            rules.computeIfAbsent(p[1].replace('.','/'), k->new ArrayList<>()).add(new Rule(p[0],p.length>2?p[2]:""));
        }
        Set<String> seen=new HashSet<>();
        int count=0;
        try (ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for(int i=2;i<args.length;i++) try(ZipFile zip=new ZipFile(args[i])) {
                var entries=zip.entries();
                while(entries.hasMoreElements()) {
                    ZipEntry e=entries.nextElement();
                    if(!e.getName().endsWith(".class") || !seen.add(e.getName())) continue;
                    byte[] original=zip.getInputStream(e).readAllBytes();
                    ClassNode c=new ClassNode(); new ClassReader(original).accept(c,0);
                    boolean changed=false;
                    for(Rule r:rules.getOrDefault(c.name,List.of())) {
                        if(r.member.isEmpty()) {c.access=access(c.access,r.access);changed=true;}
                        for(FieldNode f:c.fields) if(r.member.equals("*") || r.member.equals(f.name)) {f.access=access(f.access,r.access);changed=true;}
                        for(MethodNode m:c.methods) if(r.member.equals("*()") || r.member.equals(m.name+m.desc)) {m.access=access(m.access,r.access);changed=true;}
                    }
                    for(InnerClassNode ic:c.innerClasses) for(Rule r:rules.getOrDefault(ic.name,List.of())) if(r.member.isEmpty()) {ic.access=access(ic.access,r.access);changed=true;}
                    if(changed) {
                        ClassWriter w=new ClassWriter(0);c.accept(w);
                        ZipEntry target=new ZipEntry(e.getName()); target.setTime(0);out.putNextEntry(target);out.write(w.toByteArray());out.closeEntry();count++;
                    }
                }
            }
        }
        System.out.println("Compilation overlay classes: "+count);
    }
}
