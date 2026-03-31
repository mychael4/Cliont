import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.URL;
import java.util.zip.*;

public class MinecraftLauncher {

    private static String version;

    public static void setVersion(String v) {
        version = v;
    }

    public static void launch() throws Exception {

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

        // ===== Vanilla Downloads =====
        downloadLogging(vanillaJson, profileDir);
        downloadAssets(vanillaJson, profileDir);

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

        for (JsonElement libEl : libraries) {

            JsonObject lib = libEl.getAsJsonObject();

            if (!isLibraryAllowed(lib)) continue;

            String artifactPath = null;
            String artifactUrl = null;


            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");

                // ===== Normale Library =====
                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");

                    String path = artifact.get("path").getAsString();
                    String url = artifact.get("url").getAsString();

                    String fileName = Paths.get(path).getFileName().toString();

                    if (!addedLibs.contains(fileName)) {
                        addedLibs.add(fileName);

                        Path libFile = profileDir.resolve("libraries").resolve(path);

                        if (!Files.exists(libFile) || Files.size(libFile) == 0) {
                            System.out.println("Download library: " + path);
                            Files.createDirectories(libFile.getParent());

                            try (InputStream in = new URL(url).openStream()) {
                                Files.copy(in, libFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }

                        if (Files.exists(libFile)) {
                            classpathEntries.add(libFile.toAbsolutePath().toString());
                        }
                    }
                }

                // ===== Natives nur wenn die Library wirklich natives hat =====
                if (lib.has("natives") && lib.has("downloads")) {

                    String nativeKey = getNativeClassifierKey(lib);

                    if (nativeKey != null) {
                        nativeKey = resolveClassifierKey(nativeKey);

                        if (downloads.has("classifiers")) {

                            JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                            if (classifiers.has(nativeKey)) {

                                JsonObject classifier = classifiers.getAsJsonObject(nativeKey);

                                String nativePath = classifier.get("path").getAsString();
                                String url = classifier.get("url").getAsString();

                                Path nativeJar = profileDir.resolve("libraries").resolve(nativePath);

                                // DOWNLOAD
                                if (!Files.exists(nativeJar)) {
                                    System.out.println("Download native: " + nativePath);
                                    Files.createDirectories(nativeJar.getParent());

                                    try (InputStream in = new URL(url).openStream()) {
                                        Files.copy(in, nativeJar, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                }

                                // UNPACK
                                unpackNatives(nativeJar, nativesDir);

                            } else {
                                System.out.println("Classifier fehlt: " + nativeKey);
                            }

                        } else {
                            System.out.println("Keine classifiers in downloads für: " + lib.get("name"));
                        }
                    }
                }
            }

            // ===== Falls kein downloads.artifact vorhanden: aus Maven bauen =====
            if (artifactPath == null && lib.has("name")) {
                artifactPath = mavenToPath(lib.get("name").getAsString());

                String baseUrl = "https://libraries.minecraft.net/";
                if (lib.has("url")) {
                    baseUrl = lib.get("url").getAsString();
                }

                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }

                artifactUrl = baseUrl + artifactPath;
            }

            // ===== Artifact herunterladen + in Classpath =====
            if (artifactPath != null && addedLibs.add(artifactPath)) {
                Path libFile = profileDir.resolve("libraries").resolve(artifactPath);

                if (!Files.exists(libFile) && artifactUrl != null) {
                    System.out.println("Download library: " + artifactPath);
                    Files.createDirectories(libFile.getParent());

                    try (InputStream in = new URL(artifactUrl).openStream()) {
                        Files.copy(in, libFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                if (Files.exists(libFile)) {
                    classpathEntries.add(libFile.toAbsolutePath().toString());
                } else {
                    System.out.println("Library fehlt: " + libFile);
                }
            }
        }

        System.out.println("=== EXTRACT ALL NATIVES FROM LIBRARIES ===");
        extractAllNatives(profileDir.resolve("libraries"), nativesDir);

        // ===== Client-JAR hinzufügen =====
        Path clientJar = profileDir.resolve("client.jar");
        if (!Files.exists(clientJar)) {
            throw new FileNotFoundException("Client.jar fehlt im Profil: " + clientJar);
        }
        classpathEntries.add(clientJar.toAbsolutePath().toString());

        System.out.println("=== MAIN CLASS ===");
        System.out.println(mainClass);

        System.out.println("=== NATIVES DIR ===");
        System.out.println(nativesDir.toAbsolutePath());

        System.out.println("=== CLASSPATH ENTRIES ===");
        for (String entry : classpathEntries) {
            System.out.println(entry);
        }

        String classpath = String.join(";", classpathEntries);

        // ===== Java Command =====
        List<String> command = new ArrayList<>();

        Path javaExe = getJavaRuntime(vanillaJson);

        command.add(javaExe.toAbsolutePath().toString());
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);

        command.add("--username");
        command.add("Player");
        command.add("--version");
        command.add(version);
        command.add("--gameDir");
        command.add(profileDir.toAbsolutePath().toString());
        command.add("--assetsDir");
        command.add(profileDir.resolve("assets").toAbsolutePath().toString());
        command.add("--assetIndex");
        command.add(assetIndex);
        command.add("--accessToken");
        command.add("0");
        command.add("--uuid");
        command.add("0");
        command.add("--userType");
        command.add("legacy");

        new ProcessBuilder(command)
                .inheritIO()
                .start();
    }

    private static void downloadAssets(JsonObject versionJson, Path profileDir) throws Exception {

        JsonObject assetIndexObj = versionJson.getAsJsonObject("assetIndex");

        String url = assetIndexObj.get("url").getAsString();
        String id = assetIndexObj.get("id").getAsString();

        Path assetsDir = profileDir.resolve("assets");
        Path indexDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");

        Files.createDirectories(indexDir);
        Files.createDirectories(objectsDir);

        Path indexFile = indexDir.resolve(id + ".json");

        if (!Files.exists(indexFile)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, indexFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        JsonObject indexJson = JsonParser.parseReader(new FileReader(indexFile.toFile())).getAsJsonObject();
        JsonObject objects = indexJson.getAsJsonObject("objects");

        for (String name : objects.keySet()) {
            JsonObject obj = objects.getAsJsonObject(name);

            String hash = obj.get("hash").getAsString();
            String sub = hash.substring(0, 2);

            Path assetFile = objectsDir.resolve(sub).resolve(hash);

            if (Files.exists(assetFile)) continue;

            Files.createDirectories(assetFile.getParent());

            String assetUrl = "https://resources.download.minecraft.net/" + sub + "/" + hash;

            try (InputStream in = new URL(assetUrl).openStream()) {
                Files.copy(in, assetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void unpackNatives(Path jarFile, Path nativesDir) throws IOException {
        System.out.println("jarFile = " + jarFile);

        if (!Files.exists(jarFile) || Files.size(jarFile) == 0) {
            System.out.println("Native JAR fehlt oder ist leer: " + jarFile);
            return;
        }

        Files.createDirectories(nativesDir);

        boolean foundNative = false;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();

                if (name.startsWith("META-INF/")) continue;

                String lower = name.toLowerCase();

                if (!(lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib"))) {
                    continue;
                }

                foundNative = true;

                Path outFile = nativesDir.resolve(Paths.get(name).getFileName().toString());

                System.out.println("Extract native file: " + outFile.getFileName());

                Files.copy(zis, outFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (!foundNative) {
            System.out.println("Keine Natives gefunden in: " + jarFile.getFileName());
        }
    }
    private static Path getJavaRuntime(JsonObject versionJson) throws Exception {

        int javaVersion = 8;

        if (versionJson.has("javaVersion")) {
            javaVersion = versionJson
                    .getAsJsonObject("javaVersion")
                    .get("majorVersion")
                    .getAsInt();
        }

        Path runtimeDir = Paths.get(System.getenv("APPDATA"), ".cliont", "runtimes", "java" + javaVersion);
        Path javaExe = runtimeDir.resolve("bin").resolve("java.exe");

        if (Files.exists(javaExe)) return javaExe;

        String url;

        if (javaVersion == 8) {
            url = "https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jre/hotspot/normal/eclipse";
        } else if (javaVersion == 17) {
            url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse";
        } else {
            url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
        }

        Path zip = runtimeDir.resolve("java.zip");
        Files.createDirectories(runtimeDir);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
        }

        unzip(zip, runtimeDir);
        Files.delete(zip);

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(runtimeDir)) {
            for (Path dir : dirs) {
                javaExe = dir.resolve("bin").resolve("java.exe");
                if (Files.exists(javaExe)) {
                    return javaExe;
                }
            }
        }

        throw new RuntimeException("Java runtime not found after extraction");
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {

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

    private static void downloadLogging(JsonObject versionJson, Path profileDir) throws Exception {

        if (!versionJson.has("logging")) return;

        JsonObject logging = versionJson.getAsJsonObject("logging");
        if (!logging.has("client")) return;

        JsonObject client = logging.getAsJsonObject("client");
        JsonObject file = client.getAsJsonObject("file");

        String url = file.get("url").getAsString();
        String id = file.get("id").getAsString();

        Path logFile = profileDir.resolve(id);

        if (Files.exists(logFile)) return;

        System.out.println("Download logging config...");

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, logFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isLibraryAllowed(JsonObject lib) {

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
        Set<String> addedNames = new HashSet<>();

        if (vanillaJson.has("libraries")) {
            for (JsonElement el : vanillaJson.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                String key = lib.has("name") ? lib.get("name").getAsString() : lib.toString();

                if (addedNames.add(key)) {
                    merged.add(lib);
                }
            }
        }

        if (launchJson.has("libraries")) {
            for (JsonElement el : launchJson.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                String key = lib.has("name") ? lib.get("name").getAsString() : lib.toString();

                if (addedNames.add(key)) {
                    merged.add(lib);
                }
            }
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

    private static void extractAllNatives(Path librariesDir, Path nativesDir) throws IOException {
        if (!Files.exists(librariesDir)) {
            System.out.println("Libraries-Ordner fehlt: " + librariesDir);
            return;
        }

        Files.createDirectories(nativesDir);

        try (var paths = Files.walk(librariesDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .forEach(jar -> {
                        try {
                            System.out.println("Prüfe JAR auf Natives: " + jar);
                            unpackNatives(jar, nativesDir);
                        } catch (IOException e) {
                            System.out.println("Fehler beim Entpacken von: " + jar);
                            e.printStackTrace();
                        }
                    });
        }
    }
}