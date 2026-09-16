import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.*;

/**
 * Small utility to install an emote package (provided as a mod-style JAR produced by Blockbench export or a prepared mod JAR)
 * into a launcher profile for different loaders.
 *
 * Usage examples:
 *  - Fabric/Forge/NeoForge: copy the mod JAR into <profile>/mods/
 *  - Vanilla: extract assets/ from the JAR and package them into a resourcepack placed into <profile>/resourcepacks/
 */
public class EmoteInstaller {

    public static void installEmote(Path modJar, Path profileDir, String loader, String mcVersion) throws IOException {
        if (!Files.exists(modJar)) throw new FileNotFoundException("Mod JAR not found: " + modJar);

        String lower = loader == null ? "" : loader.toLowerCase();

        if (lower.contains("vanilla")) {
            installAsResourcePack(modJar, profileDir, mcVersion);
        } else {
            installAsModJar(modJar, profileDir);
        }
    }

    private static void installAsModJar(Path modJar, Path profileDir) throws IOException {
        Path modsDir = profileDir.resolve("mods");
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(modJar.getFileName());
        Files.copy(modJar, dest, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Installed mod JAR to mods/: " + dest.toAbsolutePath());
    }

    private static void installAsResourcePack(Path modJar, Path profileDir, String mcVersion) throws IOException {
        Path resourcepacks = profileDir.resolve("resourcepacks");
        Files.createDirectories(resourcepacks);

        String baseName = "emote-resourcepack";
        String outName = baseName + (mcVersion == null || mcVersion.isBlank() ? "" : "-" + mcVersion) + ".zip";
        Path outZip = resourcepacks.resolve(outName);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip))) {
            // pack.mcmeta
            ZipEntry packMeta = new ZipEntry("pack.mcmeta");
            zos.putNextEntry(packMeta);
            String meta = "{\"pack\":{\"pack_format\":7,\"description\":\"Emote resource pack (generated)\"}}";
            zos.write(meta.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();

            // Copy assets/ entries from modJar
            try (ZipFile zf = new ZipFile(modJar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                boolean found = false;
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String name = e.getName();
                    if (!name.startsWith("assets/")) continue;
                    ZipEntry outEntry = new ZipEntry(name);
                    if (e.isDirectory()) {
                        zos.putNextEntry(outEntry);
                        zos.closeEntry();
                        found = true;
                        continue;
                    }
                    zos.putNextEntry(outEntry);
                    try (InputStream is = zf.getInputStream(e)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) zos.write(buf, 0, r);
                    }
                    zos.closeEntry();
                    found = true;
                }
                if (!found) {
                    System.err.println("Warning: No assets/ entries found in the mod JAR. The produced resourcepack may be empty.");
                }
            }
        }

        System.out.println("Resource pack created: " + outZip.toAbsolutePath());
    }

    // Simple CLI so this can be invoked directly from the launcher if desired.
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java EmoteInstaller <mod-jar-path> <profile-path> <loader> [mc-version]");
            System.err.println("loader: vanilla | fabric | forge | neoforge");
            System.exit(2);
        }
        Path modJar = Paths.get(args[0]);
        Path profile = Paths.get(args[1]);
        String loader = args[2];
        String mcVersion = args.length >= 4 ? args[3] : "";
        installEmote(modJar, profile, loader, mcVersion);
    }
}
