import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CurseForgeBrowser {

    private static final String API_KEY = "$2a$10$8ZIpAo3NEB6uw9njXQP.Ge3cA2uG9HLaktlDbEEH3YpfNdFwGd/pq";
    private static final int GAME_ID = 432;

    private static JPanel contentPanel;
    private static JTextField searchField;

    private static int currentIndex = 0;
    private static final int pageSize = 20;
    private static String currentSearch = "";

    public static JPanel create() {

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 30, 30));

        // ---------- TOP PANEL ----------
        JPanel topPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        searchField.setFont(new Font("Arial", Font.PLAIN, 18));
        searchField.setBackground(Color.darkGray);
        searchField.setMargin(new Insets(30, 10, 50, 10));

        topPanel.add(searchField, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ---------- CONTENT ----------
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(30, 30, 30));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // ---------- LOAD MORE ----------
        JButton loadMoreButton = new JButton("Load More");
        loadMoreButton.addActionListener(e -> {
            currentIndex += pageSize;
            loadMods();
        });

        mainPanel.add(loadMoreButton, BorderLayout.SOUTH);

        loadMods();

        return mainPanel;
    }

    // ---------- SEARCH ----------
    private static void doSearch() {
        currentSearch = searchField.getText();
        currentIndex = 0;
        contentPanel.removeAll();
        loadMods();
    }

    // ---------- LOAD MODS ----------
    private static void loadMods() {
        try {
            String url = "https://api.curseforge.com/v1/mods/search"
                    + "?gameId=" + GAME_ID
                    + "&sortField=2"
                    + "&sortOrder=desc"
                    + "&pageSize=" + pageSize
                    + "&index=" + currentIndex;

            if (!currentSearch.isEmpty()) {
                String encoded = URLEncoder.encode(currentSearch, StandardCharsets.UTF_8);
                url += "&searchFilter=" + encoded;
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", API_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                JOptionPane.showMessageDialog(null,
                        "Fehler beim Laden: " + response.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");

            for (JsonElement element : data) {

                JsonObject mod = element.getAsJsonObject();

                String name = mod.get("name").getAsString();
                String summary = mod.get("summary").getAsString();
                int modId = mod.get("id").getAsInt();

                String iconUrl = null;
                if (mod.has("logo") && !mod.get("logo").isJsonNull()) {
                    iconUrl = mod.getAsJsonObject("logo")
                            .get("thumbnailUrl").getAsString();
                }

                JPanel card = createModCard(name, summary, iconUrl, modId);
                contentPanel.add(card);
            }

            contentPanel.revalidate();
            contentPanel.repaint();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- CREATE CARD ----------
    private static JPanel createModCard(String name, String summary, String iconUrl, int modId) {

        JPanel card = new JPanel(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        card.setBackground(new Color(45, 45, 45));

        // Icon
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(100, 100));

        if (iconUrl != null) {
            try (InputStream in = new URL(iconUrl).openStream()) {
                Image img = ImageIO.read(in)
                        .getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                iconLabel.setIcon(new ImageIcon(img));
            } catch (Exception ignored) {}
        }

        card.add(iconLabel, BorderLayout.WEST);

        // Text
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(new Color(45, 45, 45));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 18));

        JLabel summaryLabel = new JLabel(
                "<html><body style='width:500px'>" + summary + "</body></html>");
        summaryLabel.setForeground(Color.LIGHT_GRAY);

        textPanel.add(nameLabel);
        textPanel.add(summaryLabel);

        card.add(textPanel, BorderLayout.CENTER);

        // Download Button
        JButton downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> downloadLatestFile(modId));
        card.add(downloadButton, BorderLayout.EAST);

        return card;
    }

    // ---------- DOWNLOAD ----------
    private static void downloadLatestFile(int modId) {
        try {
            String fileUrl = "https://api.curseforge.com/v1/mods/" + modId + "/files";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .header("x-api-key", API_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray files = root.getAsJsonArray("data");

            if (files.size() == 0) {
                JOptionPane.showMessageDialog(null, "Keine Dateien gefunden.");
                return;
            }

            JsonObject latestFile = files.get(0).getAsJsonObject();
            String downloadUrl = latestFile.get("downloadUrl").getAsString();
            String fileName = latestFile.get("fileName").getAsString();

            if (downloadUrl == null || downloadUrl.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Keine Download-URL.");
                return;
            }

            downloadFile(downloadUrl, fileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void downloadFile(String url, String fileName) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            String userHome = System.getProperty("user.home");
            Path path = Path.of(userHome, "Downloads", fileName);

            Files.createDirectories(path.getParent());
            Files.copy(response.body(), path, StandardCopyOption.REPLACE_EXISTING);

            JOptionPane.showMessageDialog(null,
                    "Download fertig:\n" + path.toAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
