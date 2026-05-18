import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricApiInstaller {

    public static void installFabricApi(String minecraftVersion, Path profileDir) throws Exception {

        // mods-Ordner
        Path modsDir = profileDir.resolve("mods");
        Files.createDirectories(modsDir);

        // ⚠️ API Key hier eintragen
        String apiKey = "$2a$10$8ZIpAo3NEB6uw9njXQP.Ge3cA2uG9HLaktlDbEEH3YpfNdFwGd/pq";

        int projectId = 306612;

        HttpClient client = HttpClient.newHttpClient();

        JsonObject selectedFile = null;
        int index = 0;

        while (true) {

            String apiUrl =
                    "https://api.curseforge.com/v1/mods/" + projectId
                            + "/files?pageSize=50&index=" + index;

            System.out.println("Lade CurseForge-Dateien ab Index " + index);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("x-api-key", apiKey)
                    .header("User-Agent", "Cliont Launcher")
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "CurseForge API Fehler: HTTP " + response.statusCode()
                                + "\n" + response.body()
                );
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray files = root.getAsJsonArray("data");

            // keine Daten → Ende
            if (files == null || files.isEmpty()) {
                break;
            }

            // 🔍 Dateien prüfen
            for (JsonElement element : files) {

                JsonObject file = element.getAsJsonObject();

                if (!file.has("gameVersions")) continue;

                JsonArray gameVersions = file.getAsJsonArray("gameVersions");

                boolean versionMatches = false;
                boolean hasFabric = false;

                for (JsonElement gv : gameVersions) {
                    String value = gv.getAsString();

                    if (value.equals(minecraftVersion)) {
                        versionMatches = true;
                    }

                    if (value.equalsIgnoreCase("Fabric")) {
                        hasFabric = true;
                    }
                }

                if (versionMatches && hasFabric) {
                    selectedFile = file;
                    break;
                }
            }

            // gefunden → raus
            if (selectedFile != null) {
                break;
            }

            // letzte Seite
            if (files.size() < 50) {
                break;
            }

            index += 50;
        }

        if (selectedFile == null) {
            throw new RuntimeException(
                    "Keine kompatible Fabric API für Minecraft " + minecraftVersion + " gefunden."
            );
        }

        String downloadUrl = selectedFile.get("downloadUrl").getAsString();
        String fileName = selectedFile.get("fileName").getAsString();

        if (downloadUrl == null || downloadUrl.equals("null")) {
            throw new RuntimeException("CurseForge hat keine Download-URL zurückgegeben.");
        }

        Path target = modsDir.resolve(fileName);

        // schon installiert?
        if (Files.exists(target)) {
            System.out.println("Fabric API ist bereits installiert.");
            return;
        }

        // Download
        HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Cliont Launcher")
                .build();

        client.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(target));

        System.out.println("Fabric API installiert: " + fileName);
    }
}