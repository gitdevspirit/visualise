package visualise.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Turns payload bytecode (written against MCP names + accessor interfaces) into bytecode the game can link. */
final class PayloadRewriter {
    private final Accessors accessors;
    private final Mapper mapper;

    PayloadRewriter(Accessors accessors, Mapper mapper) { this.accessors = accessors; this.mapper = mapper; }

    byte[] rewrite(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor remap = mapper.identity ? cw : new ClassRemapper(cw, new Remapper() {
            @Override public String map(String internalName) { return mapper.cls(internalName); }
            @Override public String mapMethodName(String owner, String name, String desc) { return mapper.method(owner, name, desc); }
            @Override public String mapFieldName(String owner, String name, String desc) { return mapper.field(owner, name); }
        });
        cr.accept(new AccessorVisitor(remap), 0);
        return cw.toByteArray();
    }

    private final class AccessorVisitor extends ClassVisitor {
        AccessorVisitor(ClassVisitor next) { super(Opcodes.ASM9, next); }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (mv == null) return null;
            return new MethodVisitor(Opcodes.ASM9, mv) {
                private String retarget(String t) {
                    String target = t == null ? null : accessors.targets.get(t);
                    return target != null ? target : t;
                }

                @Override public void visitTypeInsn(int opcode, String type) { super.visitTypeInsn(opcode, retarget(type)); }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    Accessors.Entry e = opcode == Opcodes.INVOKEINTERFACE ? accessors.get(owner, name, desc) : null;
                    if (e == null) { super.visitMethodInsn(opcode, owner, name, desc, itf); return; }
                    switch (e.kind) {
                        case 0: super.visitFieldInsn(Opcodes.GETFIELD, e.owner, e.member, e.desc); break;
                        case 1: super.visitFieldInsn(Opcodes.PUTFIELD, e.owner, e.member, e.desc); break;
                        default: super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, e.owner, e.member, e.desc, false);
                    }
                }

                @Override
                public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                    super.visitFrame(type, nLocal, fix(local), nStack, fix(stack));
                }

                private Object[] fix(Object[] a) {
                    if (a == null) return null;
                    Object[] o = a.clone();
                    for (int i = 0; i < o.length; i++) if (o[i] instanceof String) o[i] = retarget((String) o[i]);
                    return o;
                }

                @Override
                public void visitLocalVariable(String n, String d, String s, org.objectweb.asm.Label a, org.objectweb.asm.Label b, int idx) {
                    // debug info only; point it at the real type so remapping stays consistent
                    if (d.startsWith("L") && d.endsWith(";")) {
                        String t = retarget(d.substring(1, d.length() - 1));
                        d = "L" + t + ";";
                    }
                    super.visitLocalVariable(n, d, null, a, b, idx);
                }
            };
        }
    }
}
