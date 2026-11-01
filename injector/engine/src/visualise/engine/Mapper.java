package visualise.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP-name -> obfuscated-name lookups, read from obf.map (written by tools/MakeMcpMappings.java --emit-obf-map).
 * Without a map file every lookup is the identity, so a client that already runs MCP names works as-is.
 *
 * obf.map lines (tab separated):
 *   C  mcpClass  obfClass
 *   F  mcpOwner  mcpName  obfName
 *   M  mcpOwner  mcpName  mcpDesc  obfName
 */
final class Mapper {
    /** Supplies the direct supertypes (MCP names) of a class so members declared in a parent can be found. */
    interface Hierarchy { List<String> supers(String mcpClass); }

    private final Map<String, String> toObf = new HashMap<String, String>();
    private final Map<String, String> toMcp = new HashMap<String, String>();
    private final Map<String, String> fields = new HashMap<String, String>();
    private final Map<String, String> methods = new HashMap<String, String>();
    private final Map<String, String> methodsByName = new HashMap<String, String>();
    private final Map<String, String> fieldCache = new ConcurrentHashMap<String, String>();
    private final Map<String, String> methodCache = new ConcurrentHashMap<String, String>();
    private volatile Hierarchy hierarchy = new Hierarchy() { public List<String> supers(String c) { return new ArrayList<String>(); } };
    final boolean identity;

    Mapper(File file) throws Exception {
        if (file == null || !file.isFile()) { identity = true; return; }
        identity = false;
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length == 3 && p[0].equals("C")) { toObf.put(p[1], p[2]); toMcp.put(p[2], p[1]); }
                else if (p.length == 4 && p[0].equals("F")) fields.put(p[1] + '\0' + p[2], p[3]);
                else if (p.length == 5 && p[0].equals("M")) {
                    methods.put(p[1] + '\0' + p[2] + '\0' + p[3], p[4]);
                    methodsByName.putIfAbsent(p[1] + '\0' + p[2], p[4]);
                }
            }
        } finally { r.close(); }
    }

    void setHierarchy(Hierarchy h) { this.hierarchy = h; fieldCache.clear(); methodCache.clear(); }

    String cls(String mcp) { String o = toObf.get(mcp); return o != null ? o : mcp; }
    String mcpOf(String obf) { String m = toMcp.get(obf); return m != null ? m : obf; }

    /** Rewrites every class name inside a descriptor. */
    String desc(String d) {
        if (identity || d == null) return d;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (c == 'L') {
                int end = d.indexOf(';', i);
                sb.append('L').append(cls(d.substring(i + 1, end))).append(';');
                i = end;
            } else sb.append(c);
        }
        return sb.toString();
    }

    String field(String ownerMcp, String name) {
        if (identity) return name;
        String key = ownerMcp + '\0' + name;
        String hit = fieldCache.get(key);
        if (hit != null) return hit;
        String res = findField(ownerMcp, name, 0);
        res = res != null ? res : name;
        fieldCache.put(key, res);
        return res;
    }

    private String findField(String owner, String name, int depth) {
        String v = fields.get(owner + '\0' + name);
        if (v != null || depth > 16) return v;
        for (String s : hierarchy.supers(owner)) { v = findField(s, name, depth + 1); if (v != null) return v; }
        return null;
    }

    String method(String ownerMcp, String name, String descMcp) {
        if (identity) return name;
        String key = ownerMcp + '\0' + name + '\0' + descMcp;
        String hit = methodCache.get(key);
        if (hit != null) return hit;
        String res = findMethod(ownerMcp, name, descMcp, 0);
        res = res != null ? res : name;
        methodCache.put(key, res);
        return res;
    }

    private String findMethod(String owner, String name, String desc, int depth) {
        String v = methods.get(owner + '\0' + name + '\0' + desc);
        if (v != null || depth > 16) return v;
        for (String s : hierarchy.supers(owner)) { v = findMethod(s, name, desc, depth + 1); if (v != null) return v; }
        return null;
    }

    /** Hook lookup when the descriptor is not given: first mapping with that MCP name in the class. */
    String methodByName(String ownerMcp, String name) {
        if (identity) return name;
        String v = methodsByName.get(ownerMcp + '\0' + name);
        return v != null ? v : name;
    }
}
