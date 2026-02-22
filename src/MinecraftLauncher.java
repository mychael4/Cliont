import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class MinecraftLauncher {

    public static String version;

    public static void setVersion(String v) {
        version = v;
    }

    public static void launch() throws Exception {

        Path mcDirPath = Paths.get(System.getProperty("user.home"),
                "AppData", "Roaming", ".cliont");
        String MC_DIR = mcDirPath.toString();
        String VERSION = version;
        String PLAYER_NAME = "My_chael";

        // ===== Version JSON laden =====
        File jsonFile = new File(MC_DIR + "\\versions\\" +
                VERSION + "\\" + VERSION + ".json");

        JsonObject versionJson = JsonParser
                .parseReader(new FileReader(jsonFile))
                .getAsJsonObject();

        // ===== Parent prüfen =====
        JsonObject parentJson = null;
        String inheritsFrom = null;

        if (versionJson.has("inheritsFrom")) {

            inheritsFrom = versionJson
                    .get("inheritsFrom")
                    .getAsString();

            File parentFile = new File(MC_DIR + "\\versions\\"
                    + inheritsFrom + "\\"
                    + inheritsFrom + ".json");

            parentJson = JsonParser
                    .parseReader(new FileReader(parentFile))
                    .getAsJsonObject();
        }

        // ===== Libraries sammeln =====
        JsonArray libraries = new JsonArray();

        if (parentJson != null && parentJson.has("libraries")) {
            for (JsonElement el : parentJson.getAsJsonArray("libraries")) {
                libraries.add(el);
            }
        }

        if (versionJson.has("libraries")) {
            for (JsonElement el : versionJson.getAsJsonArray("libraries")) {
                libraries.add(el);
            }
        }

        // ===== Main-Class =====
        String mainClass = versionJson
                .get("mainClass")
                .getAsString();

        // ===== Asset Index =====
        String assetIndex = versionJson
                .getAsJsonObject("assetIndex")
                .get("id")
                .getAsString();

        // ===== Classpath bauen =====
        List<String> classpathEntries = new ArrayList<>();

        File nativesDir = new File(MC_DIR + "\\versions\\"
                + VERSION + "\\natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        for (JsonElement libEl : libraries) {

            JsonObject lib = libEl.getAsJsonObject();

            // ---- Normale JAR ----
            if (lib.has("downloads")) {

                JsonObject downloads =
                        lib.getAsJsonObject("downloads");

                // Artifact (normale Library)
                if (downloads.has("artifact")) {

                    String path = downloads
                            .getAsJsonObject("artifact")
                            .get("path")
                            .getAsString();

                    File libFile =
                            new File(MC_DIR + "\\libraries\\" + path);

                    classpathEntries.add(libFile.getAbsolutePath());
                }

                // ---- Natives ----
                if (downloads.has("classifiers")) {

                    JsonObject classifiers =
                            downloads.getAsJsonObject("classifiers");

                    for (String key : classifiers.keySet()) {

                        if (key.contains("windows")) {

                            JsonObject classifier =
                                    classifiers.getAsJsonObject(key);

                            String path =
                                    classifier.get("path")
                                            .getAsString();

                            File nativeJar =
                                    new File(MC_DIR + "\\libraries\\" + path);

                            System.out.println("Entpacke Native: "
                                    + nativeJar.getName());

                            unpackNatives(nativeJar, nativesDir);
                        }
                    }
                }
            }
        }

        // ===== Minecraft JAR =====
        String jarVersion = (inheritsFrom != null)
                ? inheritsFrom
                : VERSION;

        classpathEntries.add(
                MC_DIR + "\\versions\\"
                        + jarVersion + "\\"
                        + jarVersion + ".jar"
        );

        String classpath =
                String.join(";", classpathEntries);

        // ===== Java Command =====
        List<String> command = new ArrayList<>();

        command.add("java");
        command.add("-Xmx2G");
        command.add("-Dorg.lwjgl.librarypath="
                + nativesDir.getAbsolutePath());
        command.add("-Djava.library.path="
                + nativesDir.getAbsolutePath());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);

        command.add("--username");
        command.add(PLAYER_NAME);
        command.add("--version");
        command.add(VERSION);
        command.add("--gameDir");
        command.add(MC_DIR);
        command.add("--assetsDir");
        command.add(MC_DIR + "\\assets");
        command.add("--assetIndex");
        command.add(assetIndex);
        command.add("--accessToken");
        command.add("0");
        command.add("--userType");
        command.add("legacy");

        new ProcessBuilder(command)
                .inheritIO()
                .start();

        System.out.println("Minecraft gestartet.");
    }

    // ===== Natives entpacken =====
    static void unpackNatives(File jarFile,
                              File outputDir) throws IOException {

        try (ZipInputStream zis =
                     new ZipInputStream(
                             new FileInputStream(jarFile))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                if (entry.isDirectory()) continue;

                File outFile =
                        new File(outputDir, entry.getName());

                outFile.getParentFile().mkdirs();

                try (FileOutputStream fos =
                             new FileOutputStream(outFile)) {

                    byte[] buffer = new byte[4096];
                    int len;

                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                zis.closeEntry();
            }
        }
    }
}