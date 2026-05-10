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
        if (!Files.exists(clientJar)) {
            throw new FileNotFoundException("Client.jar fehlt im Profil: " + clientJar);
        }
        classpathEntries.add(clientJar.toAbsolutePath().toString());

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
                classpathEntries.add(libFile.toAbsolutePath().toString());
            } else {
                System.out.println("Fehlende Library: " + path);
            }
        }

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

        // ===== Java Command =====
        List<String> command = new ArrayList<>();

        Path javaExe = Version.getJavaRuntime(vanillaJson);

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
}