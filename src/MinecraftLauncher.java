import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.*;
public class MinecraftLauncher {

    private static String version;

    public static void setVersion(String v) {
        version = v;
    }

    public static Process launch(MinecraftAuth.LoginData loginData) throws Exception {

        if (version == null || version.isEmpty()) {
            throw new IllegalStateException("Version wurde nicht gesetzt. Nutze setVersion(v).");
        }

        Path profileDir = Paths.get(System.getenv("APPDATA"), ".cliont", "profiles", version);
        if (!Files.exists(profileDir)) {
            throw new FileNotFoundException("Profilordner existiert nicht: " + profileDir);
        }

        // ===== Vanilla JSON laden =====
        Path versionJsonFile = profileDir.resolve("version.json");
        JsonObject vanillaJson = JsonParser.parseReader(new FileReader(versionJsonFile.toFile())).getAsJsonObject();

        // ===== Optional Fabric JSON laden =====
        Path fabricJsonFile = profileDir.resolve("fabric.json");
        JsonObject launchJson = vanillaJson;

        if (Files.exists(fabricJsonFile)) {
            launchJson = JsonParser.parseReader(new FileReader(fabricJsonFile.toFile())).getAsJsonObject();
            System.out.println("Fabric-Profil erkannt");
        }


        // ===== Corrupted file check and auto-repair before launch =====
        repairCorruptedProfileFiles(profileDir, vanillaJson, launchJson);

        // ===== Libraries zusammenführen =====
        JsonArray libraries = mergeLibraries(vanillaJson, launchJson);

        // ===== Main Class =====
        String mainClass = launchJson.get("mainClass").getAsString();

        // ===== Asset Index immer aus Vanilla =====
        String assetIndex = vanillaJson.getAsJsonObject("assetIndex").get("id").getAsString();

        // ===== Classpath bauen =====
        List<String> classpathEntries = new ArrayList<>();
        Path nativesDir = profileDir.resolve("natives");
        Files.createDirectories(nativesDir);

        Set<String> addedLibs = new HashSet<>();


        // ===== Client-JAR hinzufügen =====
        Path clientJar = profileDir.resolve("client.jar");
        ensureCleanClientJar(profileDir, vanillaJson, vanillaJson);
        if (!Files.exists(clientJar)) {
            throw new FileNotFoundException("Client.jar fehlt im Profil: " + clientJar);
        }

        classpathEntries.add(clientJar.toAbsolutePath().toString());

        // Add compiled launcher classes to classpath
        Path launcherClasses = Paths.get("out", "production", "Cliont");
        if (Files.exists(launcherClasses)) {
            classpathEntries.add(launcherClasses.toAbsolutePath().toString());
            System.out.println("[Cliont Cape] Added launcher classes to classpath: " + launcherClasses);
        }

        for (JsonElement libEl : libraries) {

            JsonObject lib = libEl.getAsJsonObject();

            String path = null;

            // 1. Versuche artifact path (Standard)
            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");

                if (downloads.has("artifact")) {
                    path = downloads.getAsJsonObject("artifact")
                            .get("path").getAsString();
                }
            }

            // 2. Fallback: Maven path
            if (path == null && lib.has("name")) {
                path = mavenToPath(lib.get("name").getAsString());
            }

            if (path == null) continue;

            Path libFile = profileDir.resolve("libraries").resolve(path);

            if (Files.exists(libFile)) {
                if (Version.isZipValid(libFile)) {
                    classpathEntries.add(libFile.toAbsolutePath().toString());
                } else {
                    System.err.println("[WARN] Corrupted library skipped (invalid zip): " + path);
                    System.err.println("[WARN] Delete this file and re-install the profile to fix: " + libFile);
                }
            } else {
                System.out.println("Fehlende Library: " + path);
            }
        }

        // Pre-launch validation: verify client.jar is a valid zip
        validateCriticalJar(clientJar, "client.jar");

        System.out.println("=== MAIN CLASS ===");
        System.out.println(mainClass);

        System.out.println("=== NATIVES DIR ===");
        System.out.println(nativesDir.toAbsolutePath());

        System.out.println("=== CLASSPATH ENTRIES ===");
        for (String entry : classpathEntries) {
            System.out.println(entry);
            System.out.println("Classpath size: " + classpathEntries.size());
        }

        String classpath = String.join(File.pathSeparator, classpathEntries);

        if (loginData == null) {
            throw new IllegalArgumentException("LoginData darf nicht null sein.");
        }

        String username = (loginData.username != null && !loginData.username.isBlank())
                ? loginData.username
                : "Player";
        String uuid = (loginData.uuid != null && !loginData.uuid.isBlank())
                ? loginData.uuid
                : "00000000-0000-0000-0000-000000000000";
        String accessToken = (loginData.minecraftAccessToken != null && !loginData.minecraftAccessToken.isBlank())
                ? loginData.minecraftAccessToken
                : "0";

        // ===== Java Command =====
        List<String> command = new ArrayList<>();

        Path javaExe = Version.getJavaRuntime(vanillaJson);

        command.add(javaExe.toAbsolutePath().toString());
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);

        command.add("--username");
        command.add(username);
        command.add("--version");
        command.add(version);
        command.add("--gameDir");
        command.add(profileDir.toAbsolutePath().toString());
        command.add("--assetsDir");
        command.add(profileDir.resolve("assets").toAbsolutePath().toString());
        command.add("--assetIndex");
        command.add(assetIndex);
        command.add("--accessToken");
        command.add(accessToken);
        command.add("--uuid");
        command.add(uuid);
        command.add("--userType");
        command.add("msa");

        command.add("--versionType");
        command.add("release");

        command.add("--userProperties");
        command.add("{}");

        command.add("--xuid");
        command.add("0");

        // Stream the whole Minecraft log to both the console and a log file so
        // nothing is lost even if the console output is cancelled.
        Path logDir = profileDir.resolve("logs");
        Files.createDirectories(logDir);
        Path mcLogFile = logDir.resolve("minecraft-latest.log");

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        java.io.PrintWriter logWriter = new java.io.PrintWriter(
                Files.newBufferedWriter(mcLogFile, java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING),
                true);

        // Stream stdout to console and log file in real-time.
        new Thread(() -> {
            try (var is = process.getInputStream();
                 var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    synchronized (logWriter) { logWriter.println(line); }
                }
            } catch (Exception ignored) {}
        }, "minecraft-stdout").start();

        // Stream stderr to console and log file in real-time.
        new Thread(() -> {
            try (var is = process.getErrorStream();
                 var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println(line);
                    synchronized (logWriter) { logWriter.println(line); }
                }
            } catch (Exception ignored) {}
        }, "minecraft-stderr").start();

        // Minecraft writes its full log (including errors) to gameDir/logs/latest.log.
        // Tail that file so the whole log is listed in the console.
        Path mcOwnLog = profileDir.resolve("logs").resolve("latest.log");
        new Thread(() -> {
            try {
                // Wait for Minecraft to create its log file.
                for (int i = 0; i < 60 && !Files.exists(mcOwnLog); i++) {
                    Thread.sleep(500);
                }
                if (!Files.exists(mcOwnLog)) return;
                try (var raf = new java.io.RandomAccessFile(mcOwnLog.toFile(), "r")) {
                    long pos = 0;
                    while (process.isAlive() || pos < raf.length()) {
                        long len = raf.length();
                        if (len > pos) {
                            raf.seek(pos);
                            String line;
                            while ((line = raf.readLine()) != null) {
                                System.out.println(line);
                                synchronized (logWriter) { logWriter.println(line); }
                            }
                            pos = raf.getFilePointer();
                        } else {
                            Thread.sleep(200);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }, "minecraft-log-tail").start();

        System.out.println("[Cliont] Minecraft log file: " + mcLogFile.toAbsolutePath());

        return process;
    }

    /**
     * Ensures the profile's client.jar is an official, usable Minecraft jar.
     * Custom or partially-patched jars (for example client-patched-cape.jar or
     * renamed backup files) can break the bootstrap classpath and cause ICU /
     * DateFormat resolution errors. If the jar is broken, it is replaced by a
     * clean download from the profile version metadata.
     */
    private static void ensureCleanClientJar(Path profileDir, JsonObject versionJson, JsonObject vanillaJson) throws Exception {
        Path clientJar = profileDir.resolve("client.jar");
        JsonObject manifestJson = versionJson != null && versionJson.has("downloads")
                ? versionJson
                : vanillaJson;

        if (!Files.exists(clientJar) || !isUsableMinecraftClientJar(clientJar)) {
            System.out.println("[Cliont] client.jar invalid or missing; re-downloading a clean official client jar.");
            deleteKnownClientJarJunk(profileDir);
            downloadOfficialClientJar(profileDir, manifestJson, clientJar);
        }
    }

    private static void downloadOfficialClientJar(Path profileDir, JsonObject manifestJson, Path clientJar) throws Exception {
        if (manifestJson == null || !manifestJson.has("downloads")
                || !manifestJson.getAsJsonObject("downloads").has("client")) {
            throw new IllegalStateException("No official client download metadata found for profile: " + profileDir);
        }

        JsonObject clientDownload = manifestJson.getAsJsonObject("downloads").getAsJsonObject("client");
        if (!clientDownload.has("url")) {
            throw new IllegalStateException("Official client jar URL missing for profile: " + profileDir);
        }

        String clientUrl = clientDownload.get("url").getAsString();
        HttpClient client = HttpClient.newBuilder()
                .sslContext(javax.net.ssl.SSLContext.getDefault())
                .build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(clientUrl)).timeout(java.time.Duration.ofSeconds(90)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        Path tmpJar = profileDir.resolve("client.jar.download.tmp");
        Files.createDirectories(profileDir);
        try (InputStream in = response.body()) {
            Files.copy(in, tmpJar, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!Version.isZipValid(tmpJar) || !containsMinecraftBootstrap(tmpJar)) {
            Files.deleteIfExists(tmpJar);
            throw new IllegalStateException("Downloaded client jar is invalid or not a Minecraft boot jar: " + clientUrl);
        }
        Files.move(tmpJar, clientJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("[Cliont] Clean client.jar restored: " + clientJar);
    }

    private static boolean isUsableMinecraftClientJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return false;
        }
        if (!Version.isZipValid(jar)) {
            return false;
        }
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry("net/minecraft/client/main/Main.class") != null
                    || jf.getEntry("net/minecraft/client/Main.class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsMinecraftBootstrap(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            return jf.getEntry("net/minecraft/client/main/Main.class") != null
                    || jf.getEntry("net/minecraft/client/Main.class") != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static void repairCorruptedProfileFiles(Path profileDir, JsonObject vanillaJson, JsonObject launchJson) throws Exception {
        ensureCleanClientJar(profileDir, vanillaJson, vanillaJson);

        JsonArray libraries = mergeLibraries(vanillaJson, launchJson);
        HttpClient client = HttpClient.newBuilder()
                .sslContext(javax.net.ssl.SSLContext.getDefault())
                .build();

        for (JsonElement libEl : libraries) {
            JsonObject lib = libEl.getAsJsonObject();
            String path = null;
            String url = null;

            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    path = artifact.get("path").getAsString();
                    if (artifact.has("url")) {
                        url = artifact.get("url").getAsString();
                    }
                }
            }
            if (path == null && lib.has("name")) {
                path = mavenToPath(lib.get("name").getAsString());
                url = resolveLibraryUrl(lib.get("name").getAsString());
            }
            if (path == null || url == null) {
                continue;
            }

            Path libFile = profileDir.resolve("libraries").resolve(path);
            if (!Files.exists(libFile) || !Version.isZipValid(libFile)) {
                if (Files.exists(libFile) && !Version.isZipValid(libFile)) {
                    System.out.println("[Cliont] Corrupt library detected; re-downloading: " + libFile);
                    Files.deleteIfExists(libFile);
                } else if (!Files.exists(libFile)) {
                    System.out.println("[Cliont] Missing library detected; re-downloading: " + libFile);
                }
                Files.createDirectories(libFile.getParent());
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(java.time.Duration.ofSeconds(60)).build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                Path tmp = libFile.resolveSibling(libFile.getFileName().toString() + ".download.tmp");
                try (InputStream in = response.body()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Version.isZipValid(tmp)) {
                    Files.move(tmp, libFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.deleteIfExists(tmp);
                    throw new IllegalStateException("Downloaded library is invalid: " + libFile);
                }
            }
        }
    }

    private static String resolveLibraryUrl(String name) {
        if (name == null || name.isEmpty()) return null;
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
        if (name.contains("net.fabricmc")) {
            return "https://maven.fabricmc.net/" + path;
        }
        if (name.contains("org.ow2.asm") || name.contains("org.spongepowered")) {
            return "https://repo1.maven.org/maven2/" + path;
        }
        return "https://libraries.minecraft.net/" + path;
    }

    private static void deleteKnownClientJarJunk(Path profileDir) throws IOException {
        if (profileDir == null || !Files.exists(profileDir)) {
            return;
        }
        String[] junkNames = {
                "client-patched-cape.jar",
                "client.jar.before-cape-import-render-fix.bak",
                "client.jar.before-cape-ui-fix.bak",
                "client.jar.options-crash.bak"
        };
        for (String name : junkNames) {
            Path p = profileDir.resolve(name);
            if (Files.exists(p)) {
                Files.deleteIfExists(p);
            }
        }
        try (var stream = Files.walk(profileDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("client.jar.") && path.getFileName().toString().endsWith(".bak"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    /**
     * Validates that a critical JAR file is a readable ZIP. Throws if it is
     * corrupted so the launcher fails fast instead of launching a broken game.
     */
    private static void validateCriticalJar(Path jar, String label) {
        if (jar == null || !Files.exists(jar)) return;
        if (!Version.isZipValid(jar)) {
            throw new IllegalStateException(
                    "Korruptes JAR (" + label + ") : " + jar
                            + "\nLösche diese Datei und installiere das Profil erneut.");
        }
    }

    static void unzip(Path zipFile, Path targetDir) throws IOException {

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());

                    try (OutputStream os = Files.newOutputStream(out)) {
                        byte[] buffer = new byte[4096];
                        int len;

                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    static boolean isLibraryAllowed(JsonObject lib) {

        if (!lib.has("rules")) return true;

        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allowed = false;

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();

            boolean applies = true;

            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");

                if (os.has("name")) {
                    String name = os.get("name").getAsString();

                    if (name.equals("windows")) {
                        applies &= osName.contains("win");
                    } else if (name.equals("linux")) {
                        applies &= osName.contains("linux");
                    } else if (name.equals("osx")) {
                        applies &= osName.contains("mac");
                    }
                }

                if (os.has("arch")) {
                    String arch = os.get("arch").getAsString().toLowerCase();

                    if (arch.equals("x86")) {
                        applies &= (osArch.contains("x86") || osArch.contains("i386"));
                    } else if (arch.equals("x86_64") || arch.equals("amd64")) {
                        applies &= (osArch.contains("amd64") || osArch.contains("x86_64"));
                    } else if (arch.equals("arm64") || arch.equals("aarch64")) {
                        applies &= (osArch.contains("arm64") || osArch.contains("aarch64"));
                    } else {
                        applies &= osArch.contains(arch);
                    }
                }
            }

            if (applies) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }

    private static JsonArray mergeLibraries(JsonObject vanillaJson, JsonObject launchJson) {

        JsonArray merged = new JsonArray();
        Map<String, JsonObject> latestLibs = new HashMap<>();

        // Hilfsfunktion zum Extrahieren von group:artifact
        java.util.function.Function<String, String> getGroupArtifact = (name) -> {
            String[] parts = name.split(":");
            return parts.length >= 2 ? parts[0] + ":" + parts[1] : name;
        };

        // Hilfsfunktion zum Vergleichen von Versionen (einfach string-basiert)
        java.util.function.BiFunction<String, String, String> getLatestVersion = (v1, v2) -> {
            return v1.compareTo(v2) > 0 ? v1 : v2;
        };

        java.util.function.Function<JsonObject, String> getLibraryKey = (lib) -> {
            String key = lib.has("name") ? lib.get("name").getAsString() : "";
            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    key += "|artifact:" + downloads.getAsJsonObject("artifact").get("path").getAsString();
                } else if (downloads.has("classifiers")) {
                    key += "|classifiers";
                }
            }
            if (lib.has("natives")) {
                key += "|natives";
            }
            return key;
        };

        // Verarbeite Vanilla Libraries
        if (vanillaJson.has("libraries")) {
            for (JsonElement el : vanillaJson.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                if (!isLibraryAllowed(lib)) continue;
                String key = getLibraryKey.apply(lib);
                JsonObject existing = latestLibs.get(key);
                if (existing == null) {
                    latestLibs.put(key, lib);
                } else {
                    String version = lib.has("name") ? lib.get("name").getAsString().split(":")[2] : "";
                    String existingVersion = existing.has("name") ? existing.get("name").getAsString().split(":")[2] : "";
                    if (getLatestVersion.apply(version, existingVersion).equals(version)) {
                        latestLibs.put(key, lib);
                    }
                }
            }
        }

        // Verarbeite Launch Libraries (z.B. Fabric)
        if (launchJson != null && launchJson.has("libraries")) {
            for (JsonElement el : launchJson.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                if (!isLibraryAllowed(lib)) continue;
                String key = getLibraryKey.apply(lib);
                JsonObject existing = latestLibs.get(key);
                if (existing == null) {
                    latestLibs.put(key, lib);
                } else {
                    String version = lib.has("name") ? lib.get("name").getAsString().split(":")[2] : "";
                    String existingVersion = existing.has("name") ? existing.get("name").getAsString().split(":")[2] : "";
                    if (getLatestVersion.apply(version, existingVersion).equals(version)) {
                        latestLibs.put(key, lib);
                    }
                }
            }
        }

        // Sortiere Libraries: Dependencies zuerst (ASM, Mixin, etc. vor Loaders)
        List<String> keys = new ArrayList<>(latestLibs.keySet());
        keys.sort((k1, k2) -> {
            // Loader und spezifische Abhängigkeiten ans Ende
            boolean k1IsLoader = k1.contains("fabric-loader") || k1.contains("neoforge") || k1.contains("forge");
            boolean k2IsLoader = k2.contains("fabric-loader") || k2.contains("neoforge") || k2.contains("forge");

            if (k1IsLoader && !k2IsLoader) return 1;
            if (!k1IsLoader && k2IsLoader) return -1;

            return k1.compareTo(k2);
        });

        // In Array umwandeln in sortierter Reihenfolge
        for (String key : keys) {
            merged.add(latestLibs.get(key));
        }

        return merged;
    }

    private static String mavenToPath(String maven) {
        String[] parts = maven.split(":");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Ungültiger Maven-Name: " + maven);
        }

        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];

        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    static String getNativeClassifierKey(JsonObject lib) {
        if (!lib.has("natives")) return null;

        JsonObject natives = lib.getAsJsonObject("natives");

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            if (natives.has("windows")) {
                return natives.get("windows").getAsString();
            }
        } else if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            if (natives.has("linux")) {
                return natives.get("linux").getAsString();
            }
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            if (natives.has("osx")) {
                return natives.get("osx").getAsString();
            }
        }

        return null;
    }

    static String resolveClassifierKey(String key) {
        String arch = System.getProperty("os.arch").toLowerCase();

        String archValue = "64";
        if (arch.contains("86") && !arch.contains("64")) {
            archValue = "32";
        }

        return key.replace("${arch}", archValue);
    }
}