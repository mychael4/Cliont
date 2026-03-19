import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.URL;
import java.util.zip.*;

public class MinecraftLauncher {

    private static String version; // aktuell ausgewählte Version

    // Version setzen
    public static void setVersion(String v) {
        version = v;
    }

    // Minecraft starten
    public static void launch() throws Exception {

        if(version == null || version.isEmpty()) {
            throw new IllegalStateException("Version wurde nicht gesetzt. Nutze setVersion(v).");
        }

        // Profilordner (alle Daten liegen direkt im Profil)
        Path profileDir = Paths.get(System.getenv("APPDATA"), ".cliont", "profiles", version);
        if(!Files.exists(profileDir)) {
            throw new FileNotFoundException("Profilordner existiert nicht: " + profileDir);
        }

        // ===== Version JSON laden =====
        Path jsonFile = profileDir.resolve("version.json");
        JsonObject versionJson = JsonParser.parseReader(new FileReader(jsonFile.toFile())).getAsJsonObject();
        downloadLogging(versionJson, profileDir);
        downloadAssets(versionJson, profileDir);

        // ===== Libraries sammeln =====
        JsonArray libraries = versionJson.has("libraries") ? versionJson.getAsJsonArray("libraries") : new JsonArray();
        String mainClass = versionJson.get("mainClass").getAsString();

        // ===== Asset Index =====
        String assetIndex = versionJson.getAsJsonObject("assetIndex").get("id").getAsString();

        // ===== Classpath bauen =====
        List<String> classpathEntries = new ArrayList<>();
        Path nativesDir = profileDir.resolve("natives");
        Files.createDirectories(nativesDir);

        for(JsonElement libEl : libraries) {

            Set<String> addedLibs = new HashSet<>();

            JsonObject lib = libEl.getAsJsonObject();

            if(!isLibraryAllowed(lib)) continue;

            if(!lib.has("downloads")) continue;

            JsonObject downloads = lib.getAsJsonObject("downloads");

            // ===== Normale Library =====
            if(downloads.has("artifact")) {
                String path = downloads
                        .getAsJsonObject("artifact")
                        .get("path")
                        .getAsString();

                String fileName = Paths.get(path).getFileName().toString();

                // ❗ DUPLIKATE VERMEIDEN
                if(addedLibs.contains(fileName)) continue;
                addedLibs.add(fileName);

                Path libFile = profileDir.resolve("libraries").resolve(path);

                if(Files.exists(libFile)) {
                    classpathEntries.add(libFile.toAbsolutePath().toString());
                }
            }
            // ===== Natives =====
            if(downloads.has("classifiers")) {

                JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                for(String key : classifiers.keySet()) {

                    if(!key.contains("windows")) continue;

                    JsonObject classifier = classifiers.getAsJsonObject(key);

                    String path = classifier.get("path").getAsString();
                    String url = classifier.get("url").getAsString();

                    Path nativeJar = profileDir.resolve("libraries").resolve(path);

                    if(!Files.exists(nativeJar)) {

                        System.out.println("Download native: " + nativeJar.getFileName());

                        Files.createDirectories(nativeJar.getParent());

                        try(InputStream in = new URL(url).openStream()) {
                            Files.copy(in, nativeJar);
                        }
                    }

                    unpackNatives(nativeJar, nativesDir);
                }
            }
        }

        // Client-JAR hinzufügen
        Path clientJar = profileDir.resolve("client.jar");
        if(!Files.exists(clientJar)) throw new FileNotFoundException("Client.jar fehlt im Profil: " + clientJar);
        classpathEntries.add(clientJar.toAbsolutePath().toString());

        String classpath = String.join(";", classpathEntries);

        // ===== Java Command =====
        List<String> command = new ArrayList<>();

        Path javaExe = getJavaRuntime(versionJson);

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

        if(!Files.exists(indexFile)) {

            try(InputStream in = new URL(url).openStream()) {
                Files.copy(in, indexFile);
            }
        }

        JsonObject indexJson = JsonParser.parseReader(new FileReader(indexFile.toFile())).getAsJsonObject();
        JsonObject objects = indexJson.getAsJsonObject("objects");

        for(String name : objects.keySet()) {

            JsonObject obj = objects.getAsJsonObject(name);

            String hash = obj.get("hash").getAsString();

            String sub = hash.substring(0, 2);

            Path assetFile = objectsDir.resolve(sub).resolve(hash);

            if(Files.exists(assetFile)) continue;

            Files.createDirectories(assetFile.getParent());

            String assetUrl = "https://resources.download.minecraft.net/" + sub + "/" + hash;

            try(InputStream in = new URL(assetUrl).openStream()) {
                Files.copy(in, assetFile);
            }
        }
    }

    private static void unpackNatives(Path jarFile, Path nativesDir) throws IOException {

        if(!Files.exists(jarFile)) return;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarFile))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                if(entry.isDirectory()) continue;

                String name = entry.getName();

                if(!(name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")))
                    continue;

                Path outFile = nativesDir.resolve(Paths.get(name).getFileName());

                Files.copy(zis, outFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path getJavaRuntime(JsonObject versionJson) throws Exception {

        int javaVersion = 8;

        if(versionJson.has("javaVersion")) {
            javaVersion = versionJson
                    .getAsJsonObject("javaVersion")
                    .get("majorVersion")
                    .getAsInt();
        }

        Path runtimeDir = Paths.get(System.getenv("APPDATA"),
                ".cliont", "runtimes", "java" + javaVersion);

        Path javaExe = runtimeDir.resolve("bin").resolve("java.exe");

        if(Files.exists(javaExe)) return javaExe;

        String url;

        if(javaVersion == 8)
            url = "https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jre/hotspot/normal/eclipse";
        else if(javaVersion == 17)
            url = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse";
        else
            url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";

        Path zip = runtimeDir.resolve("java.zip");

        Files.createDirectories(runtimeDir);

        try(InputStream in = new URL(url).openStream()) {
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

        try(ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {

            ZipEntry entry;

            while((entry = zis.getNextEntry()) != null) {

                Path out = targetDir.resolve(entry.getName());

                if(entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {

                    Files.createDirectories(out.getParent());

                    try(OutputStream os = Files.newOutputStream(out)) {

                        byte[] buffer = new byte[4096];
                        int len;

                        while((len = zis.read(buffer)) > 0)
                            os.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private static void downloadLogging(JsonObject versionJson, Path profileDir) throws Exception {

        if(!versionJson.has("logging")) return;

        JsonObject logging = versionJson.getAsJsonObject("logging");

        if(!logging.has("client")) return;

        JsonObject client = logging.getAsJsonObject("client");

        JsonObject file = client.getAsJsonObject("file");

        String url = file.get("url").getAsString();
        String id = file.get("id").getAsString();

        Path logFile = profileDir.resolve(id);

        if(Files.exists(logFile)) return;

        System.out.println("Download logging config...");

        try(InputStream in = new URL(url).openStream()) {
            Files.copy(in, logFile);
        }
    }

    private static boolean isLibraryAllowed(JsonObject lib) {

        if(!lib.has("rules")) return true;

        JsonArray rules = lib.getAsJsonArray("rules");

        boolean allowed = false;

        for(JsonElement ruleEl : rules) {

            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();

            boolean applies = true;

            if(rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");

                String name = os.get("name").getAsString();

                if(name.equals("windows")) {
                    applies = System.getProperty("os.name").toLowerCase().contains("win");
                } else if(name.equals("linux")) {
                    applies = System.getProperty("os.name").toLowerCase().contains("linux");
                } else if(name.equals("osx")) {
                    applies = System.getProperty("os.name").toLowerCase().contains("mac");
                }
            }

            if(applies) {
                allowed = action.equals("allow");
            }
        }

        return allowed;
    }
}