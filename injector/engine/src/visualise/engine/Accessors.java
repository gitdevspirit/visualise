package visualise.engine;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces Mixin-style accessor interfaces (@Accessor / @Invoker) with direct member access.
 *
 * The payload keeps writing  ((IAccessorMinecraft) mc).getTimer();  This class reads those interfaces,
 * records what each method stands for, and the payload rewriter turns every call site into a plain
 * getfield / putfield / invokevirtual on the real class. The target members are made public by the
 * transformer so the access is legal from the payload.
 */
final class Accessors {
    static final class Entry {
        final String owner;        // MCP internal name of the target class
        final String member;       // MCP member name
        final String desc;         // field desc, or method desc for invokers
        final int kind;            // 0 getter, 1 setter, 2 invoker
        Entry(String owner, String member, String desc, int kind) { this.owner = owner; this.member = member; this.desc = desc; this.kind = kind; }
    }

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    /** accessor interface internal name -> target class internal name */
    final Map<String, String> targets = new HashMap<String, String>();
    /** iface + '.' + method + desc -> entry */
    final Map<String, Entry> entries = new HashMap<String, Entry>();

    boolean isAccessor(String internalName) { return targets.containsKey(internalName); }
    Entry get(String iface, String method, String desc) { return entries.get(iface + '.' + method + desc); }

    /** Target members (MCP owner -> member names) that must be public for the payload to reach them. */
    Map<String, Set<String>> widenTargets() {
        Map<String, Set<String>> out = new HashMap<String, Set<String>>();
        for (Entry e : entries.values()) {
            Set<String> s = out.get(e.owner);
            if (s == null) { s = new HashSet<String>(); out.put(e.owner, s); }
            s.add(e.member);
        }
        return out;
    }

    void parse(byte[] bytes) {
        final String[] iface = new String[1];
        final List<String> mixinTargets = new ArrayList<String>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            public void visit(int v, int a, String name, String sig, String sup, String[] itf) { iface[0] = name; }

            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!MIXIN.equals(desc)) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    public AnnotationVisitor visitArray(String n) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            public void visit(String n2, Object v) {
                                if (v instanceof Type) mixinTargets.add(((Type) v).getInternalName());
                                else if (v instanceof String) mixinTargets.add(((String) v).replace('.', '/'));
                            }
                        };
                    }
                    public void visit(String n, Object v) {
                        if (v instanceof Type) mixinTargets.add(((Type) v).getInternalName());
                    }
                };
            }

            public MethodVisitor visitMethod(int access, final String mname, final String mdesc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        final boolean acc = ACCESSOR.equals(desc), inv = INVOKER.equals(desc);
                        if (!acc && !inv) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            String explicit;
                            public void visit(String n, Object v) { if ("value".equals(n) && v instanceof String) explicit = (String) v; }
                            public void visitEnd() {
                                if (mixinTargets.isEmpty()) return;
                                Type[] args = Type.getArgumentTypes(mdesc);
                                Type ret = Type.getReturnType(mdesc);
                                String owner = mixinTargets.get(0);
                                String member = explicit != null ? explicit : derive(mname, inv);
                                Entry e;
                                if (inv) e = new Entry(owner, member, mdesc, 2);
                                else if (args.length == 0 && ret.getSort() != Type.VOID) e = new Entry(owner, member, ret.getDescriptor(), 0);
                                else if (args.length == 1 && ret.getSort() == Type.VOID) e = new Entry(owner, member, args[0].getDescriptor(), 1);
                                else return;
                                entries.put(iface[0] + '.' + mname + mdesc, e);
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        if (iface[0] != null && !mixinTargets.isEmpty()) targets.put(iface[0], mixinTargets.get(0));
    }

    /** Mixin's default naming: strip get/set/is (accessor) or call/invoke (invoker), lower-case the first letter. */
    static String derive(String method, boolean invoker) {
        String[] prefixes = invoker ? new String[]{"call", "invoke"} : new String[]{"get", "set", "is"};
        for (String p : prefixes) {
            if (method.startsWith(p) && method.length() > p.length() && Character.isUpperCase(method.charAt(p.length()))) {
                String rest = method.substring(p.length());
                return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
            }
        }
        return method;
    }
}
