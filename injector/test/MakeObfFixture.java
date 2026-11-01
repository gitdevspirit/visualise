import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Test helper: renames the fake game to short obfuscated names and writes the matching obf.map. */
public class MakeObfFixture {
    public static void main(String[] a) throws Exception {
        Path in = Paths.get(a[0]), out = Paths.get(a[1]), map = Paths.get(a[2]);
        List<Path> files = Files.walk(in).filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        Map<String, String> cls = new TreeMap<>(), meth = new HashMap<>(), fld = new HashMap<>();
        List<String> lines = new ArrayList<>();
        int[] n = {0};
        // pass 1: names
        for (Path p : files) {
            ClassReader cr = new ClassReader(Files.readAllBytes(p));
            cls.put(cr.getClassName(), "o" + Integer.toString(n[0]++, 36));
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int ac, String nm, String d, String s, String[] e) {
                    if (!nm.startsWith("<") && !nm.equals("main")) meth.computeIfAbsent(nm, k -> "m" + Integer.toString(n[0]++, 36));
                    return null;
                }
                public FieldVisitor visitField(int ac, String nm, String d, String s, Object v) {
                    fld.computeIfAbsent(nm, k -> "f" + Integer.toString(n[0]++, 36)); return null;
                }
            }, ClassReader.SKIP_CODE);
        }
        Remapper rm = new Remapper() {
            public String map(String t) { return cls.getOrDefault(t, t); }
            public String mapMethodName(String o, String nm, String d) { return meth.getOrDefault(nm, nm); }
            public String mapFieldName(String o, String nm, String d) { return fld.getOrDefault(nm, nm); }
        };
        for (String c : cls.keySet()) lines.add("C\t" + c + "\t" + cls.get(c));
        for (Path p : files) {
            byte[] b = Files.readAllBytes(p);
            ClassReader cr = new ClassReader(b);
            String owner = cr.getClassName();
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int ac, String nm, String d, String s, String[] e) {
                    if (meth.containsKey(nm)) lines.add("M\t" + owner + "\t" + nm + "\t" + d + "\t" + meth.get(nm)); return null; }
                public FieldVisitor visitField(int ac, String nm, String d, String s, Object v) {
                    lines.add("F\t" + owner + "\t" + nm + "\t" + fld.get(nm)); return null; }
            }, ClassReader.SKIP_CODE);
            ClassWriter cw = new ClassWriter(0);
            cr.accept(new ClassRemapper(cw, rm), 0);
            Path dest = out.resolve(cls.get(owner) + ".class");
            Files.createDirectories(dest.getParent());
            Files.write(dest, cw.toByteArray());
        }
        Files.write(map, lines);
        System.out.println("obf fixture: " + cls.size() + " classes, " + meth.size() + " method names, " + fld.size() + " field names");
    }
}
