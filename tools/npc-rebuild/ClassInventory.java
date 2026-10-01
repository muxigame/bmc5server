import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Exact normalized instruction comparison, not a claim of general semantic equivalence. */
public class ClassInventory {
    static String hash(String s)throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    public static void main(String[] args)throws Exception {
        Path output=Path.of(args[1]);Files.createDirectories(output);
        List<String> rows=new ArrayList<>();
        try(ZipFile z=new ZipFile(args[0])) {
            List<? extends ZipEntry> entries=Collections.list(z.entries());
            entries.sort(Comparator.comparing(ZipEntry::getName));
            for(ZipEntry e:entries) if(e.getName().endsWith(".class")) {
                ClassNode c=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                rows.add("CLASS\t"+c.name+"\t-\t"+c.access+" "+c.superName+" "+c.interfaces);
                for(FieldNode f:c.fields) rows.add("FIELD\t"+c.name+"\t"+f.name+" "+f.desc+"\t"+f.access+" "+f.signature+" "+f.value);
                for(MethodNode m:c.methods) {
                    rows.add("METHOD\t"+c.name+"\t"+m.name+m.desc+"\t"+m.access+" "+m.signature+" "+m.exceptions);
                    Textifier t=new Textifier();m.accept(new TraceMethodVisitor(t));
                    StringWriter s=new StringWriter();t.print(new PrintWriter(s));
                    String normalized=s.toString().replaceAll("(?m)^    MAX(STACK|LOCALS) = .*\\R?", "");
                    String id=hash(c.name+" "+m.name+m.desc);
                    Files.writeString(output.resolve(id+".txt"),normalized);
                    rows.add("CODE\t"+c.name+"\t"+m.name+m.desc+"\t"+hash(normalized)+" "+id);
                }
            }
        }
        Collections.sort(rows);Files.write(output.resolve("inventory.tsv"),rows);
    }
}
