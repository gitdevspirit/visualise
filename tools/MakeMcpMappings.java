import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Builds a Loom-compatible mappings jar whose "named" side is MCP (mcp_stable 22, 1.8.9),
 * keyed on Legacy Fabric's 1.8.9 intermediary names.
 *
 *   intermediary (method_123)  ->  obfuscated (a)  ->  SRG (func_1_a)  ->  MCP (renderGameOverlay)
 *
 * This lets the Forge-era MCP-named source compile and run on Legacy Fabric without renaming anything.
 *
 * Usage (JDK 11+):   java tools/MakeMcpMappings.java [--out mappings/mcp-1.8.9.jar]
 *                                                    [--intermediary file] [--srg file] [--csv file]
 * Each --file option may be a .jar/.zip or the plain file; without it the data is downloaded and cached
 * under mappings/cache/.
 */
public class MakeMcpMappings {
    // Which intermediary the GAME runs on decides which one the mod must be built against.
    // OneClient / Ornithe 1.8.9 = Calamus gen2. A Legacy Fabric instance = Legacy Fabric intermediary.
    static final String[] CALAMUS_URLS = {
        "https://maven.ornithemc.net/releases/net/ornithemc/calamus-intermediary-gen2/1.8.9/calamus-intermediary-gen2-1.8.9-v2.jar",
        "https://maven.ornithemc.net/releases/net/ornithemc/calamus-intermediary-gen2/1.8.9/calamus-intermediary-gen2-1.8.9.jar",
    };
    static final String[] LEGACY_FABRIC_URLS = {
        "https://maven.legacyfabric.net/net/legacyfabric/intermediary/1.8.9/intermediary-1.8.9-v2.jar",
        "https://maven.legacyfabric.net/net/legacyfabric/intermediary/1.8.9/intermediary-1.8.9.jar",
    };
    // If a download fails, pass the file with --intermediary (the launcher already has
    // calamus-intermediary-gen2-1.8.9.jar in its libraries folder).
    static final String[] SRG_URLS = {
        "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip",
    };
    static final String[] CSV_URLS = {
        "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/22-1.8.9/mcp_stable-22-1.8.9.zip",
    };

    // ---------------------------------------------------------------- data holders
    static class Intermediary {
        final Map<String, String> classes = new LinkedHashMap<>();                 // obf -> intermediary
        final Map<String, List<String[]>> methods = new LinkedHashMap<>();         // obfOwner -> [obfName, obfDesc, interName]
        final Map<String, List<String[]>> fields = new LinkedHashMap<>();          // obfOwner -> [obfName, desc, interName]
        boolean descInIntermediary;   // tiny v2: descriptors use the FIRST namespace (Calamus: intermediary, Legacy Fabric: official)
    }
    static class Srg {
        final Map<String, String> classes = new HashMap<>();   // obf -> srg/mcp class
        final Map<String, String> fields = new HashMap<>();    // obfOwner + " " + obfName -> srgName
        final Map<String, String> methods = new HashMap<>();   // obfOwner + " " + obfName + " " + obfDesc -> srgName
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) opt.put(args[i].substring(2), args[++i]);
            else usage();
        }
        Path out = Paths.get(opt.getOrDefault("out", "mappings/mcp-1.8.9.jar"));
        Path cache = Paths.get(opt.getOrDefault("cache", "mappings/cache"));
        Files.createDirectories(cache);
        if (out.getParent() != null) Files.createDirectories(out.getParent());

        String flavor = opt.getOrDefault("flavor", "calamus");
        if (!flavor.equals("calamus") && !flavor.equals("legacy")) usage();
        String interOpt = opt.get("intermediary");
        Path bundled = Paths.get("tools/data/calamus-intermediary-gen2-1.8.9.jar");
        if (interOpt == null && flavor.equals("calamus") && Files.exists(bundled)) {
            interOpt = bundled.toString();   // the exact file OneClient/Ornithe ship: no guessing at download URLs
        }
        Path interFile = locate(interOpt, cache, flavor + "-intermediary-1.8.9.jar",
                flavor.equals("calamus") ? CALAMUS_URLS : LEGACY_FABRIC_URLS);
        Path srgFile = locate(opt.get("srg"), cache, "mcp-1.8.9-srg.zip", SRG_URLS);
        Path csvFile = locate(opt.get("csv"), cache, "mcp_stable-22-1.8.9.zip", CSV_URLS);

        System.out.println("Reading intermediary: " + interFile);
        String interText = readText(interFile, "mappings/mappings.tiny", ".tiny");
        Intermediary inter = parseIntermediary(interText);
        System.out.println("  " + inter.classes.size() + " classes");

        System.out.println("Reading SRG: " + srgFile);
        Srg srg = parseSrg(readText(srgFile, "joined.srg", ".srg"));
        System.out.println("  " + srg.classes.size() + " classes, " + srg.methods.size() + " methods, " + srg.fields.size() + " fields");

        System.out.println("Reading MCP csv: " + csvFile);
        Map<String, String> mcpMethods = parseCsv(readText(csvFile, "methods.csv", "methods.csv"));
        Map<String, String> mcpFields = parseCsv(readText(csvFile, "fields.csv", "fields.csv"));
        System.out.println("  " + mcpMethods.size() + " method names, " + mcpFields.size() + " field names");

        String tiny = build(inter, srg, mcpMethods, mcpFields);

        // Loom (via Legacy Looming) expects an "official -> intermediary" file. For Calamus we hand it the
        // Calamus file itself (re-emitted in that layout if it came as v2), so the mod is remapped to the
        // names the game really runs on. build.gradle points Loom at this file.
        Path loomInter = out.resolveSibling("intermediary-1.8.9.jar");
        Files.deleteIfExists(loomInter);
        if (flavor.equals("calamus")) {
            checkCalamusNames(inter);
            String loomText = inter.descInIntermediary ? loomIntermediary(inter) : interText;
            writeJar(loomInter, "mappings/mappings.tiny", loomText);
            System.out.println("Wrote " + loomInter.toAbsolutePath() + "  (Calamus names, official->intermediary layout)");
        }

        writeJar(out, "mappings/mappings.tiny", tiny);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** Calamus gen2 names look like net/minecraft/unmapped/C_12345678; gen1 / Legacy Fabric look like class_123. */
    static void checkCalamusNames(Intermediary inter) {
        int gen2 = 0, gen1 = 0;
        for (String n : inter.classes.values()) {
            if (n.contains("/C_")) gen2++;
            else if (n.contains("class_")) gen1++;
        }
        System.out.println("  intermediary style: " + gen2 + " Calamus gen2 (C_...) classes, " + gen1 + " class_N classes");
        if (gen2 == 0 || gen1 > gen2) {
            throw new IllegalStateException("--flavor calamus but the intermediary has no C_... names (looks like Legacy Fabric / gen1). "
                    + "The game (OneClient/Ornithe) would not find these classes. Use tools/data/calamus-intermediary-gen2-1.8.9.jar.");
        }
    }

    static void writeJar(Path out, String entry, String text) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out), mf)) {
            jos.putNextEntry(new JarEntry(entry));
            jos.write(text.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }

    /** Re-emits a Calamus-style file as "tiny v2: official, intermediary" (descriptors in official names). */
    static String loomIntermediary(Intermediary inter) {
        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, String> c : inter.classes.entrySet()) reverse.put(c.getValue(), c.getKey());
        StringBuilder sb = new StringBuilder("tiny\t2\t0\tofficial\tintermediary\n");
        for (Map.Entry<String, String> c : inter.classes.entrySet()) {
            sb.append("c\t").append(c.getKey()).append('\t').append(c.getValue()).append('\n');
            for (String[] f : inter.fields.getOrDefault(c.getKey(), Collections.<String[]>emptyList()))
                sb.append("\tf\t").append(remapDesc(f[1], reverse)).append('\t').append(f[0]).append('\t').append(f[2]).append('\n');
            for (String[] m : inter.methods.getOrDefault(c.getKey(), Collections.<String[]>emptyList()))
                sb.append("\tm\t").append(remapDesc(m[1], reverse)).append('\t').append(m[0]).append('\t').append(m[2]).append('\n');
        }
        return sb.toString();
    }

    static void usage() {
        System.err.println("usage: java tools/MakeMcpMappings.java [--out jar] [--intermediary f] [--flavor calamus|legacy] [--srg f] [--csv f] [--cache dir]");
        System.exit(2);
    }

    // ---------------------------------------------------------------- build
    static String build(Intermediary inter, Srg srg, Map<String, String> mcpMethods, Map<String, String> mcpFields) {
        StringBuilder sb = new StringBuilder("tiny\t2\t0\tintermediary\tnamed\n");
        int mappedMethods = 0, mappedFields = 0, srgOnlyMethods = 0, srgOnlyFields = 0, dupes = 0, noSrgClass = 0;

        Map<String, String> reverse = new HashMap<>();   // intermediary -> obfuscated
        for (Map.Entry<String, String> c : inter.classes.entrySet()) reverse.put(c.getValue(), c.getKey());

        for (Map.Entry<String, String> c : inter.classes.entrySet()) {
            String obf = c.getKey(), interName = c.getValue();
            String namedClass = srg.classes.get(obf);
            if (namedClass == null) { namedClass = interName; noSrgClass++; }

            StringBuilder members = new StringBuilder();
            Set<String> seen = new HashSet<>();

            for (String[] m : inter.methods.getOrDefault(obf, Collections.<String[]>emptyList())) {
                String obfDesc = inter.descInIntermediary ? remapDesc(m[1], reverse) : m[1];
                String srgName = srg.methods.get(obf + " " + m[0] + " " + obfDesc);
                if (srgName == null) continue;
                String named = mcpMethods.get(srgName);
                if (named == null) { named = srgName; srgOnlyMethods++; }
                String desc = inter.descInIntermediary ? m[1] : remapDesc(m[1], inter.classes);
                if (!seen.add("m " + named + desc)) {           // MCP name clash inside one class: keep SRG name
                    named = srgName; dupes++;
                    seen.add("m " + named + desc);
                }
                members.append("\tm\t").append(desc).append('\t').append(m[2]).append('\t').append(named).append('\n');
                mappedMethods++;
            }
            for (String[] f : inter.fields.getOrDefault(obf, Collections.<String[]>emptyList())) {
                String srgName = srg.fields.get(obf + " " + f[0]);
                if (srgName == null) continue;
                String named = mcpFields.get(srgName);
                if (named == null) { named = srgName; srgOnlyFields++; }
                String desc = inter.descInIntermediary ? f[1] : remapDesc(f[1], inter.classes);
                if (!seen.add("f " + named)) { named = srgName; dupes++; seen.add("f " + named); }
                members.append("\tf\t").append(desc).append('\t').append(f[2]).append('\t').append(named).append('\n');
                mappedFields++;
            }

            if (namedClass.equals(interName) && members.length() == 0) continue;
            sb.append("c\t").append(interName).append('\t').append(namedClass).append('\n').append(members);
        }
        System.out.println("Mapped " + mappedMethods + " methods (" + srgOnlyMethods + " keep SRG names) and "
                + mappedFields + " fields (" + srgOnlyFields + " keep SRG names); "
                + dupes + " name clashes, " + noSrgClass + " classes without SRG entry");
        if (mappedMethods == 0 || inter.classes.isEmpty() || srg.classes.isEmpty()) {
            throw new IllegalStateException("Nothing mapped - the input files do not match each other (wrong Minecraft version?)");
        }
        return sb.toString();
    }

    /** Rewrites class names inside a descriptor from the obfuscated to the intermediary namespace. */
    static String remapDesc(String desc, Map<String, String> classes) {
        StringBuilder out = new StringBuilder(desc.length() + 16);
        for (int i = 0; i < desc.length(); i++) {
            char ch = desc.charAt(i);
            if (ch == 'L') {
                int end = desc.indexOf(';', i);
                String name = desc.substring(i + 1, end);
                out.append('L').append(classes.getOrDefault(name, name)).append(';');
                i = end;
            } else out.append(ch);
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- parsers
    static Intermediary parseIntermediary(String text) {
        Intermediary r = new Intermediary();
        String[] lines = text.split("\r?\n");
        if (lines.length == 0) throw new IllegalArgumentException("empty intermediary file");
        String[] head = lines[0].split("\t");
        boolean v2 = head[0].equals("tiny");
        int srcIdx = 0, interIdx = 1;
        r.descInIntermediary = v2 && head.length > 3 && head[3].equals("intermediary");
        List<String> ns = new ArrayList<>(Arrays.asList(head).subList(v2 ? 3 : 1, head.length));
        // Legacy Fabric uses "official"; Calamus (Ornithe) gen2 splits it into clientOfficial / serverOfficial.
        // MCP's joined.srg is keyed on the client's obfuscated names, so prefer the client column.
        for (String cand : new String[]{"official", "clientOfficial", "serverOfficial"}) {
            if (ns.contains(cand)) { srcIdx = ns.indexOf(cand); break; }
        }
        if (ns.contains("intermediary")) interIdx = ns.indexOf("intermediary");
        System.out.println("  namespaces " + ns + " -> obfuscated=" + ns.get(srcIdx) + ", intermediary=" + ns.get(interIdx));

        String curOwner = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            if (v2) {
                String[] p = line.split("\t", -1);
                if (p[0].equals("c")) {
                    String obf = p[1 + srcIdx], in = p[1 + interIdx];
                    if (obf.isEmpty() || in.isEmpty()) { curOwner = null; continue; }
                    r.classes.put(obf, in); curOwner = obf;
                } else if (p[0].isEmpty() && p.length > 4 && curOwner != null && !p[1].isEmpty()) {
                    // member line: "", m|f, desc, name_ns0, name_ns1 ...
                    String kind = p[1], desc = p[2];
                    String obfName = p[3 + srcIdx], interName = p[3 + interIdx];
                    if (obfName.isEmpty() || interName.isEmpty()) continue;
                    (kind.equals("m") ? r.methods : r.fields).computeIfAbsent(curOwner, k -> new ArrayList<>())
                            .add(new String[]{obfName, desc, interName});
                }
            } else {
                String[] p = line.split("\t");
                switch (p[0]) {
                    case "CLASS": r.classes.put(p[1 + srcIdx], p[1 + interIdx]); break;
                    case "FIELD": // FIELD owner desc name1 name2
                        r.fields.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new String[]{p[3 + srcIdx], p[2], p[3 + interIdx]}); break;
                    case "METHOD":
                        r.methods.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new String[]{p[3 + srcIdx], p[2], p[3 + interIdx]}); break;
                    default: break;
                }
            }
        }
        return r;
    }

    static Srg parseSrg(String text) {
        Srg r = new Srg();
        for (String line : text.split("\r?\n")) {
            String[] p = line.trim().split(" ");
            if (p.length < 3) continue;
            switch (p[0]) {
                case "CL:": r.classes.put(p[1], p[2]); break;
                case "FD:": r.fields.put(owner(p[1]) + " " + member(p[1]), member(p[2])); break;
                case "MD:": if (p.length >= 5) r.methods.put(owner(p[1]) + " " + member(p[1]) + " " + p[2], member(p[3])); break;
                default: break;
            }
        }
        return r;
    }

    static String owner(String path) { return path.substring(0, path.lastIndexOf('/')); }
    static String member(String path) { return path.substring(path.lastIndexOf('/') + 1); }

    /** methods.csv / fields.csv: searge,name,side,desc  ->  searge -> name */
    static Map<String, String> parseCsv(String text) {
        Map<String, String> r = new HashMap<>();
        String[] lines = text.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].split(",", 4);
            if (p.length >= 2 && !p[0].isEmpty() && !p[1].isEmpty()) r.put(p[0], p[1]);
        }
        return r;
    }

    // ---------------------------------------------------------------- file access
    static Path locate(String override, Path cache, String cacheName, String[] urls) throws IOException {
        if (override != null) {
            Path p = Paths.get(override);
            if (!Files.exists(p)) throw new FileNotFoundException(override);
            return p;
        }
        Path cached = cache.resolve(cacheName);
        if (Files.exists(cached) && Files.size(cached) > 0) return cached;
        IOException last = null;
        for (String u : urls) {
            try {
                System.out.println("Downloading " + u);
                HttpURLConnection c = (HttpURLConnection) java.net.URI.create(u).toURL().openConnection();
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "MakeMcpMappings");
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " for " + u);
                try (InputStream in = c.getInputStream()) { Files.copy(in, cached, StandardCopyOption.REPLACE_EXISTING); }
                return cached;
            } catch (IOException e) { last = e; System.err.println("  failed: " + e.getMessage()); }
        }
        throw new IOException("Could not download " + cacheName + ". Download it manually and pass it with --"
                + (cacheName.contains("intermediary") ? "intermediary" : cacheName.startsWith("mcp_") ? "csv" : "srg") + " <file>.", last);
    }

    static String readText(Path file, String entryName, String suffix) throws IOException {
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".jar") || n.endsWith(".zip")) {
            try (ZipFile z = new ZipFile(file.toFile())) {
                ZipEntry e = z.getEntry(entryName);
                if (e == null) {
                    for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                        ZipEntry c = en.nextElement();
                        if (c.getName().endsWith(suffix) || c.getName().endsWith("/" + entryName)) { e = c; break; }
                    }
                }
                if (e == null) throw new FileNotFoundException(entryName + " not found inside " + file);
                try (InputStream in = z.getInputStream(e)) { return new String(readAll(in), StandardCharsets.UTF_8); }
            }
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }
}
