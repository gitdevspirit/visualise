package visualise.engine;

import org.objectweb.asm.ClassReader;
import visualise.boot.Boot;
import visualise.boot.Engine;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class EngineImpl implements Engine {
    private final Mapper mapper;
    private final Accessors accessors = new Accessors();
    private final Map<String, byte[]> payload = new LinkedHashMap<String, byte[]>();   // MCP internal name -> bytes
    private final HookTransformer hooks;
    private final String mainClassObf;
    private volatile ClassLoader gameLoader;

    public EngineImpl(String dir) throws Exception {
        File d = new File(dir);
        mapper = new Mapper(new File(d, "obf.map"));

        File jar = new File(d, "visualise-payload.jar");
        if (jar.isFile()) {
            JarFile jf = new JarFile(jar);
            try {
                for (Enumeration<JarEntry> en = jf.entries(); en.hasMoreElements(); ) {
                    JarEntry e = en.nextElement();
                    if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                    InputStream in = jf.getInputStream(e);
                    byte[] b = readAll(in);
                    in.close();
                    String name = e.getName().substring(0, e.getName().length() - 6);
                    if (name.startsWith("myau/mixin/IAccessor")) accessors.parse(b);
                    else if (!name.startsWith("myau/mixin/Mixin") && !name.startsWith("visualise/boot/")) payload.put(name, b);
                }
            } finally { jf.close(); }
        } else {
            System.err.println("[visualise] no visualise-payload.jar in " + dir + " - hooks will be installed but nothing will run");
        }

        mapper.setHierarchy(new Mapper.Hierarchy() {
            public List<String> supers(String mcpClass) { return supersOf(mcpClass); }
        });
        hooks = new HookTransformer(mapper, HookTransformer.defaultHooks(), accessors.widenTargets());
        mainClassObf = mapper.cls("net/minecraft/client/Minecraft");
        Runtime.getRuntime().addShutdownHook(new Thread() { public void run() { hooks.report(); } });
        System.out.println("[visualise] " + payload.size() + " payload classes, " + accessors.entries.size()
                + " accessors, mappings " + (mapper.identity ? "none (MCP names)" : "loaded"));
    }

    // ---------------------------------------------------------------- Engine
    public byte[] transform(ClassLoader loader, String name, byte[] bytes) {
        if (name.equals(mainClassObf) && loader != null) gameLoader = loader;
        if (!hooks.wants(name)) return null;
        try {
            byte[] out = hooks.transform(name, bytes);
            return out;
        } catch (Throwable t) {
            System.err.println("[visualise] failed to transform " + name + ": " + t);
            return null;
        }
    }

    public void startPayload() throws Exception {
        ClassLoader loader = gameLoader;
        if (loader == null) throw new IllegalStateException("game classloader not seen (is " + mainClassObf + " ever loaded?)");

        PayloadRewriter rw = new PayloadRewriter(accessors, mapper);
        Set<String> done = new HashSet<String>();
        for (String n : new ArrayList<String>(payload.keySet())) define(n, loader, rw, done);

        Class<?> entry = Class.forName("myau.agent.Entry", true, loader);
        entry.getMethod("start").invoke(null);
        System.out.println("[visualise] payload started");
    }

    /** Defines a payload class after its payload supertypes (JNI DefineClass resolves supers eagerly). */
    private void define(String mcpName, ClassLoader loader, PayloadRewriter rw, Set<String> done) {
        if (!done.add(mcpName)) return;
        byte[] in = payload.get(mcpName);
        if (in == null) return;
        ClassReader cr = new ClassReader(in);
        if (cr.getSuperName() != null) define(cr.getSuperName(), loader, rw, done);
        for (String i : cr.getInterfaces()) define(i, loader, rw, done);
        byte[] out = rw.rewrite(in);
        Boot.define0(loader, mcpName, out);   // JNI wants the internal (slash) name
    }

    // ---------------------------------------------------------------- hierarchy for the mapper
    private final Map<String, List<String>> superCache = new HashMap<String, List<String>>();

    private synchronized List<String> supersOf(String mcpClass) {
        List<String> hit = superCache.get(mcpClass);
        if (hit != null) return hit;
        List<String> res = new ArrayList<String>();
        try {
            byte[] b = payload.get(mcpClass);
            boolean fromPayload = b != null;
            if (b == null && gameLoader != null) {
                InputStream in = gameLoader.getResourceAsStream(mapper.cls(mcpClass) + ".class");
                if (in != null) { b = readAll(in); in.close(); }
            }
            if (b != null) {
                ClassReader cr = new ClassReader(b);
                if (cr.getSuperName() != null) res.add(fromPayload ? cr.getSuperName() : mapper.mcpOf(cr.getSuperName()));
                for (String i : cr.getInterfaces()) res.add(fromPayload ? i : mapper.mcpOf(i));
            }
        } catch (Throwable ignored) { /* unknown class: no supertypes */ }
        superCache.put(mcpClass, res);
        return res;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
