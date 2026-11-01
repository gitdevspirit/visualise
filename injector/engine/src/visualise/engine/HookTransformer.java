package visualise.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the hook table to game classes (by obfuscated name) and widens accessor targets. */
final class HookTransformer {
    private static final String HOOKS = "visualise/boot/Hooks";

    enum Kind { HEAD, HEAD_CANCEL, RETURN, BEFORE_CALL, BEFORE_CALL_AFTER_LDC }

    /** One hook, written entirely in MCP names. */
    static final class Hook {
        final String owner, method, desc;         // desc may be null = any
        final Kind kind;
        final String hookName, hookDesc;           // static method on visualise/boot/Hooks
        final int floatSlot;                       // >=0: push this float local before calling the hook
        final String callOwner, callName, ldc;     // BEFORE_CALL*: the call to anchor on (MCP); ldc for *_AFTER_LDC
        boolean applied;

        Hook(String owner, String method, String desc, Kind kind, String hookName, String hookDesc, int floatSlot,
             String callOwner, String callName, String ldc) {
            this.owner = owner; this.method = method; this.desc = desc; this.kind = kind;
            this.hookName = hookName; this.hookDesc = hookDesc; this.floatSlot = floatSlot;
            this.callOwner = callOwner; this.callName = callName; this.ldc = ldc;
        }
        String label() { return owner + "." + method + " " + kind; }
    }

    static List<Hook> defaultHooks() {
        final String MC = "net/minecraft/client/Minecraft";
        final String ER = "net/minecraft/client/renderer/EntityRenderer";
        final String GI = "net/minecraft/client/gui/GuiIngame";
        List<Hook> h = new ArrayList<Hook>();
        h.add(new Hook(MC, "startGame", "()V", Kind.HEAD, "startGameHead", "()V", -1, null, null, null));
        h.add(new Hook(MC, "startGame", "()V", Kind.RETURN, "startGameReturn", "()V", -1, null, null, null));
        h.add(new Hook(MC, "runTick", "()V", Kind.HEAD, "runTickHead", "()V", -1, null, null, null));
        h.add(new Hook(MC, "runTick", "()V", Kind.RETURN, "runTickReturn", "()V", -1, null, null, null));
        h.add(new Hook(MC, "loadWorld", "(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", Kind.HEAD,
                "loadWorld", "()V", -1, null, null, null));
        h.add(new Hook(MC, "clickMouse", "()V", Kind.HEAD_CANCEL, "clickMouse", "()Z", -1, null, null, null));
        h.add(new Hook(MC, "updateFramebufferSize", "()V", Kind.RETURN, "resize", "()V", -1, null, null, null));
        h.add(new Hook(MC, "runTick", "()V", Kind.BEFORE_CALL, "keyState", "(IZ)V", -1,
                "net/minecraft/client/settings/KeyBinding", "setKeyBindState", null));
        h.add(new Hook(ER, "renderWorldPass", "(IFJ)V", Kind.BEFORE_CALL_AFTER_LDC, "render3D", "(F)V", 2,
                "net/minecraft/profiler/Profiler", "endStartSection", "hand"));
        h.add(new Hook(GI, "renderGameOverlay", "(F)V", Kind.RETURN, "render2D", "(F)V", 1, null, null, null));
        return h;
    }

    private final Mapper mapper;
    private final List<Hook> hooks;
    private final Map<String, List<Hook>> byOwnerObf = new HashMap<String, List<Hook>>();
    private final Map<String, Set<String>> widenObf = new HashMap<String, Set<String>>();  // obf owner -> MCP member names
    private final Map<String, String> widenOwnerMcp = new HashMap<String, String>();

    HookTransformer(Mapper mapper, List<Hook> hooks, Map<String, Set<String>> widenMcp) {
        this.mapper = mapper;
        this.hooks = hooks;
        for (Hook h : hooks) {
            String o = mapper.cls(h.owner);
            List<Hook> l = byOwnerObf.get(o);
            if (l == null) { l = new ArrayList<Hook>(); byOwnerObf.put(o, l); }
            l.add(h);
        }
        for (Map.Entry<String, Set<String>> e : widenMcp.entrySet()) {
            widenObf.put(mapper.cls(e.getKey()), e.getValue());
            widenOwnerMcp.put(mapper.cls(e.getKey()), e.getKey());
        }
    }

    boolean wants(String internalName) { return byOwnerObf.containsKey(internalName) || widenObf.containsKey(internalName); }

    byte[] transform(String internalName, byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        Set<String> widen = widenObf.get(internalName);
        if (widen != null) System.out.println("[visualise] widened accessor targets in " + internalName);
        if (widen != null) widen(cn, widen, widenOwnerMcp.get(internalName));

        List<Hook> mine = byOwnerObf.get(internalName);
        if (mine != null) for (Hook h : mine) apply(cn, h);

        if (mine != null) for (Hook h : mine) if (h.applied) System.out.println("[visualise] hooked " + h.label());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);   // no COMPUTE_FRAMES: it would load game classes
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** Printed at shutdown: hooks that never matched (class never loaded, or the names/descriptor differ in this client). */
    void report() {
        int missing = 0;
        for (Hook h : hooks) if (!h.applied) { missing++; System.out.println("[visualise] hook NOT APPLIED " + h.label()); }
        if (missing == 0) System.out.println("[visualise] all " + hooks.size() + " hooks were applied");
    }

    // ---------------------------------------------------------------- widen
    private void widen(ClassNode cn, Set<String> mcpNames, String mcpOwner) {
        for (FieldNode f : cn.fields) {
            for (String n : mcpNames) {
                if (mapper.field(mcpOwner, n).equals(f.name)) f.access = open(f.access);
            }
        }
        for (MethodNode m : cn.methods) {
            for (String n : mcpNames) {
                if (mapper.methodByName(mcpOwner, n).equals(m.name)) m.access = open(m.access);
            }
        }
    }

    private static int open(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL)) | Opcodes.ACC_PUBLIC;
    }

    // ---------------------------------------------------------------- hooks
    private void apply(ClassNode cn, Hook h) {
        String name = h.method.equals("<init>") ? h.method : mapper.method(h.owner, h.method, h.desc);
        if (mapper.identity == false && h.desc == null) name = mapper.methodByName(h.owner, h.method);
        String desc = mapper.desc(h.desc);
        for (MethodNode m : cn.methods) {
            if (!m.name.equals(name) || (desc != null && !m.desc.equals(desc))) continue;
            if (m.instructions.size() == 0) continue;
            switch (h.kind) {
                case HEAD:        m.instructions.insert(call(h)); h.applied = true; break;
                case HEAD_CANCEL: headCancel(m, h); h.applied = true; break;
                case RETURN:      returns(m, h); break;
                case BEFORE_CALL:
                case BEFORE_CALL_AFTER_LDC: beforeCall(m, h); break;
            }
        }
    }

    private InsnList call(Hook h) {
        InsnList l = new InsnList();
        if (h.floatSlot >= 0) l.add(new VarInsnNode(Opcodes.FLOAD, h.floatSlot));
        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, h.hookName, h.hookDesc, false));
        return l;
    }

    private void headCancel(MethodNode m, Hook h) {
        // Only void methods: if (Hooks.x()) return;   The label sits at offset >0 so the implicit initial frame applies.
        InsnList l = new InsnList();
        LabelNode go = new LabelNode();
        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, h.hookName, h.hookDesc, false));
        l.add(new JumpInsnNode(Opcodes.IFEQ, go));
        l.add(new InsnNode(Opcodes.RETURN));
        l.add(go);
        l.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(l);
    }

    private void returns(MethodNode m, Hook h) {
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (n.getOpcode() == Opcodes.RETURN) {
                m.instructions.insertBefore(n, call(h));
                h.applied = true;
            }
        }
    }

    private void beforeCall(MethodNode m, Hook h) {
        String owner = mapper.cls(h.callOwner);
        String name = mapper.methodByName(h.callOwner, h.callName);
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (!(n instanceof MethodInsnNode)) continue;
            MethodInsnNode mi = (MethodInsnNode) n;
            if (!mi.name.equals(name) || !mi.owner.equals(owner)) continue;
            if (h.kind == Kind.BEFORE_CALL_AFTER_LDC) {
                AbstractInsnNode prev = mi.getPrevious();
                if (!(prev instanceof LdcInsnNode) || !h.ldc.equals(((LdcInsnNode) prev).cst)) continue;
            }
            InsnList l = new InsnList();
            if (h.kind == Kind.BEFORE_CALL) l.add(new InsnNode(Opcodes.DUP2));   // (int key, boolean state) stay on the stack
            l.add(call(h));
            m.instructions.insertBefore(h.kind == Kind.BEFORE_CALL_AFTER_LDC ? mi.getPrevious() : mi, l);
            h.applied = true;
        }
    }
}
