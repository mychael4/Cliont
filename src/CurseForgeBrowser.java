import com.google.gson.*;
import org.w3c.dom.Text;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileReader;
import java.io.IOException;
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
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.List;

public class CurseForgeBrowser {

    private static final String API_KEY = "$2a$10$8ZIpAo3NEB6uw9njXQP.Ge3cA2uG9HLaktlDbEEH3YpfNdFwGd/pq"; // CurseForge API Key hier einsetzen
    private static final int GAME_ID = 432;

    private static JPanel contentPanel;
    private static JTextField searchField;

    private static int currentIndex = 0;
    private static final int pageSize = 20;
    private static String currentSearch = "";

    private static Timer searchTimer;

    private static final Color BG = new Color(32, 34, 37);
    private static final Color CARD = new Color(47, 49, 54);
    private static final Color HOVER = new Color(64, 68, 75);

    public static JPanel create() throws IOException, FontFormatException {

        Color Cliont = new Color(188, 19, 254);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(43, 43, 43));
        mainPanel.setBorder(null);

        // ---------- SEARCH BAR ----------
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topPanel.setBackground(new Color(60, 60, 60));

        searchField = new JTextField(){
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.setFont(getFont());
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        searchField.setFont(new Font("Arial", Font.PLAIN, 16));
        searchField.setPreferredSize(new Dimension(200, 35));
        searchField.setBackground(new Color(70, 70, 70)); // Hintergrundfarbe der Box
        searchField.setForeground(Color.WHITE);



        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void restartTimer() {
                if (searchTimer != null) searchTimer.stop();
                searchTimer = new Timer(300, e -> doSearch());
                searchTimer.setRepeats(false);
                searchTimer.start();
            }
            public void insertUpdate(DocumentEvent e) { restartTimer(); }
            public void removeUpdate(DocumentEvent e) { restartTimer(); }
            public void changedUpdate(DocumentEvent e) { restartTimer(); }
        });

        topPanel.add(searchField, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ---------- MOD LIST ----------
        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(18f);

        JLabel offline = new JLabel("Ein Problem ist aufgetreten");
        offline.setFont(mcFont);
        offline.setForeground(Color.WHITE);

        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(new Color(43, 43, 43));
        contentPanel.setBorder(null);

        contentPanel.add(offline);


        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(43, 43, 43));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);


        // ---------- LOAD MORE ----------
        //JButton loadMore = new JButton("Load More");
        //loadMore.addActionListener(e -> {
        //    currentIndex += pageSize;
        //    loadMods();
        //});
        //mainPanel.add(loadMore, BorderLayout.SOUTH);
        loadMods();

        return mainPanel;
    }

    static class ProfileInfo {
        Path path;
        String name;
        String version;
        String loader;
    }

    static class FileInfo {
        String downloadUrl;
        String fileName;
        java.util.List<String> gameVersions;
        int modLoader; // 4 = Fabric
    }

    private static void doSearch() {
        currentSearch = searchField.getText();
        currentIndex = 0;
        contentPanel.removeAll();
        contentPanel.revalidate();
        contentPanel.repaint();
        loadMods();
    }

    static java.util.List<ProfileInfo> getProfiles() {

        java.util.List<ProfileInfo> list = new java.util.ArrayList<>();

        Path dir = Path.of(System.getenv("APPDATA"), ".cliont", "profiles");

        try {

            if (!Files.exists(dir)) return list;

            Files.list(dir).forEach(p -> {

                try {

                    Path jsonPath = p.resolve("profile.json");

                    if (!Files.exists(jsonPath)) return;

                    JsonObject json = JsonParser.parseReader(
                            new FileReader(jsonPath.toFile())
                    ).getAsJsonObject();

                    ProfileInfo info = new ProfileInfo();
                    info.path = p;
                    info.name = json.get("name").getAsString();
                    info.version = json.get("version").getAsString();
                    info.loader = json.get("loader").getAsString();

                    list.add(info);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
    static java.util.List<FileInfo> getFiles(int modId) {

        java.util.List<FileInfo> list = new java.util.ArrayList<>();

        try {

            String url = "https://api.curseforge.com/v1/mods/" + modId + "/files";

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonArray files = JsonParser.parseString(response.body())
                    .getAsJsonObject()
                    .getAsJsonArray("data");

            for (JsonElement el : files) {

                JsonObject f = el.getAsJsonObject();

                FileInfo info = new FileInfo();

                // Download URL
                if (f.has("downloadUrl") && !f.get("downloadUrl").isJsonNull()) {
                    info.downloadUrl = f.get("downloadUrl").getAsString();
                } else {
                    continue; // ohne Download URL nutzlos
                }

                // Dateiname
                if (f.has("fileName") && !f.get("fileName").isJsonNull()) {
                    info.fileName = f.get("fileName").getAsString();
                }

                // Standardwerte
                info.gameVersions = new java.util.ArrayList<>();
                info.modLoader = -1;

                // 🔥 gameVersions sauber auslesen
                if (f.has("gameVersions") && f.get("gameVersions").isJsonArray()) {

                    JsonArray gv = f.getAsJsonArray("gameVersions");

                    for (JsonElement versionElement : gv) {
                        String v = versionElement.getAsString();
                        info.gameVersions.add(v.toLowerCase());

                        // Loader erkennen
                        String lower = v.toLowerCase();
                        if (lower.contains("fabric")) {
                            info.modLoader = 4;
                        } else if (lower.contains("neoforge")) {
                            info.modLoader = 6;
                        } else if (lower.contains("forge")) {
                            info.modLoader = 1;
                        }
                    }
                }

                // Fallback: offizielles modLoader Feld nutzen
                if (f.has("modLoader")
                        && !f.get("modLoader").isJsonNull()
                        && info.modLoader == -1) {

                    info.modLoader = f.get("modLoader").getAsInt();
                }

                list.add(info);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private static void loadMods() {

        new SwingWorker<Void, JPanel>() {

            @Override
            protected Void doInBackground() throws Exception {

                String url = "https://api.curseforge.com/v1/mods/search"
                        + "?gameId=" + GAME_ID
                        + "&sortField=2"
                        + "&sortOrder=desc"
                        + "&pageSize=" + pageSize
                        + "&index=" + currentIndex;

                if (!currentSearch.isEmpty()) {
                    url += "&searchFilter="
                            + URLEncoder.encode(currentSearch, StandardCharsets.UTF_8);
                }

                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                JsonObject root = JsonParser.parseString(response.body())
                        .getAsJsonObject();

                JsonArray data = root.getAsJsonArray("data");

                for (JsonElement element : data) {

                    JsonObject mod = element.getAsJsonObject();

                    String name = mod.get("name").getAsString();
                    String summary = mod.get("summary").getAsString();
                    int modId = mod.get("id").getAsInt();
                    long downloads = mod.get("downloadCount").getAsLong();

                    String iconUrl = null;

                    if (mod.has("logo")
                            && !mod.get("logo").isJsonNull()) {

                        JsonObject logo = mod.getAsJsonObject("logo");

                        if (logo.has("thumbnailUrl")
                                && !logo.get("thumbnailUrl").isJsonNull()) {

                            iconUrl = logo.get("thumbnailUrl").getAsString();
                        }
                    }

                    // 🔥 Mod-Dateien direkt holen
                    java.util.List<FileInfo> files = getFiles(modId);

                    // 🔥 Karte erstellen (mit Install-Button)
                    JPanel card = createModCard(
                            name,
                            summary,
                            iconUrl,
                            modId,
                            downloads,
                            files
                    );

                    publish(card);
                }

                return null;
            }

            @Override
            protected void process(List<JPanel> cards) {

                for (JPanel card : cards) {
                    contentPanel.add(card);
                }

                contentPanel.revalidate();
                contentPanel.repaint();
            }

        }.execute();
    }

    static boolean isCompatible(ProfileInfo p, FileInfo f) {

        String profileVersion = p.version.toLowerCase();

        boolean versionMatch = f.gameVersions.stream().anyMatch(gv ->
                gv.equals(profileVersion)
                        || gv.contains(profileVersion)
                        || profileVersion.contains(gv)
        );

        boolean loaderMatch = false;

        switch (p.loader.toLowerCase()) {
            case "fabric":
                loaderMatch = (f.modLoader == 4);
                break;
            case "forge":
                loaderMatch = (f.modLoader == 1);
                break;
            case "neoforge":
                loaderMatch = (f.modLoader == 6);
                break;
            case "vanilla":
                loaderMatch = (f.modLoader == -1);
                break;
        }

        return versionMatch && loaderMatch;
    }

    static void openInstallPanel(int modId) {

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(BG);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, BorderLayout.CENTER);

        JButton back = new JButton("←");
        back.addActionListener(e -> {
            Mods.modsLayout.show(Mods.modsPanel, "browser");
        });
        panel.add(back, BorderLayout.NORTH);

        new SwingWorker<Void, JPanel>() {

            protected Void doInBackground() {

                java.util.List<FileInfo> files = getFiles(modId);

                for (ProfileInfo profile : getProfiles()) {
                    publish(createInstallCard(profile, files));
                }

                return null;
            }

            protected void process(java.util.List<JPanel> cards) {
                for (JPanel c : cards) list.add(c);
                list.revalidate();
            }

        }.execute();

        // 🔥 WICHTIG: eindeutiger Key!
        String key = "install_" + modId;

        // ❌ KEIN remove
        Mods.modsPanel.add(panel, key);
        Mods.modsLayout.show(Mods.modsPanel, key);
    }

    static JPanel createInstallCard(ProfileInfo profile, java.util.List<FileInfo> files) {

        JPanel card = new JPanel(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // 🔥 LEFT: Profil-Infos
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(CARD);

        JLabel name = new JLabel(profile.name);
        name.setForeground(Color.WHITE);
        name.setFont(new Font("Arial", Font.BOLD, 14));

        JLabel info = new JLabel(profile.version + " • " + profile.loader);
        info.setForeground(Color.LIGHT_GRAY);
        info.setFont(new Font("Arial", Font.PLAIN, 12));

        leftPanel.add(name);
        leftPanel.add(info);

        card.add(leftPanel, BorderLayout.WEST);

        // 🔥 prüfen ob kompatible Datei existiert
        boolean hasCompatibleVersion = false;
        FileInfo bestMatch = null;

        for (FileInfo f : files) {
            if (isCompatible(profile, f)) {
                hasCompatibleVersion = true;
                bestMatch = f;
                break;
            }
        }

        if (hasCompatibleVersion) {
            // ✅ Kompatibel
            JLabel compatible = new JLabel("✓ Compatible");
            compatible.setForeground(new Color(100, 255, 100));
            compatible.setFont(new Font("Arial", Font.BOLD, 12));

            JButton install = new JButton("Install");
            install.setBackground(new Color(188, 19, 254));
            install.setForeground(Color.WHITE);
            
            FileInfo finalBestMatch = bestMatch;

            install.addActionListener(e -> {
                System.out.println("Start install for profile: " + profile.name);
                if (finalBestMatch != null) {
                    // 🔥 Installation im Background-Thread!
                    new Thread(() -> {
                        installToProfile(profile, finalBestMatch);
                    }).start();
                } else {
                    System.out.println("No compatible file found!");
                }
            });

            JPanel rightPanel = new JPanel(new BorderLayout(10, 0));
            rightPanel.setBackground(CARD);
            rightPanel.add(compatible, BorderLayout.WEST);
            rightPanel.add(install, BorderLayout.EAST);

            card.add(rightPanel, BorderLayout.EAST);
        } else {
            // ❌ Nicht kompatibel
            JLabel notCompatible = new JLabel("✗ Not compatible");
            notCompatible.setForeground(new Color(255, 100, 100));
            notCompatible.setFont(new Font("Arial", Font.BOLD, 12));
            card.add(notCompatible, BorderLayout.EAST);
        }
        
        return card;
    }

    static void installToProfile(ProfileInfo profile, FileInfo file) {

        try {

            Path modsDir = profile.path.resolve("mods");
            Files.createDirectories(modsDir);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(file.downloadUrl))
                    .build();

            // 🔥 WICHTIG: ofByteArray() statt ofInputStream()!
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("Download failed: HTTP " + response.statusCode());
            }

            byte[] bytes = response.body();
            if (bytes.length == 0) {
                throw new IOException("Download result was empty for " + file.fileName);
            }

            Path target = modsDir.resolve(file.fileName);

            // 🔥 Byte-Array direkt schreiben - zuverlässiger
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("✓ Installed: " + file.fileName + " (" + bytes.length + " bytes)");

            // 🔥 UI-Update auf dem EDT!
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "Installed in " + profile.name + "\n(" + (response.body().length / 1024 / 1024) + " MB)");
            });

        } catch (Exception e) {
            e.printStackTrace();
            // 🔥 Fehlerbehandlung auf dem EDT!
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "Error installing: " + e.getMessage(),
                        "Installation Error",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private static JPanel createModCard(String name, String summary, String iconUrl, int modId, long downloads, List<FileInfo> files) {

        Color Cliont = new Color(43, 43, 43);

        JPanel card = new JPanel(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        card.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { card.setBackground(HOVER); }
            public void mouseExited(MouseEvent e) { card.setBackground(CARD); }
        });

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(80, 80));
        if (iconUrl != null) {
            try {
                InputStream in = new URL(iconUrl).openStream();
                Image img = ImageIO.read(in).getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                iconLabel.setIcon(new ImageIcon(img));
            } catch (Exception ignored) {}
        }
        card.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setBackground(new Color(70, 70, 70)); // Hintergrundfarbe der Box
        textPanel.setForeground(Color.WHITE);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(CARD);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 16));
        nameLabel.setForeground(Color.WHITE);

        JLabel summaryLabel = new JLabel("<html><body style='width:450px'>" + summary + "</body></html>");
        summaryLabel.setForeground(Color.LIGHT_GRAY);

        JLabel downloadLabel = new JLabel(NumberFormat.getInstance().format(downloads) + " downloads");
        downloadLabel.setForeground(Cliont);

        textPanel.add(nameLabel);
        textPanel.add(summaryLabel);
        textPanel.add(downloadLabel);
        card.add(textPanel, BorderLayout.CENTER);

        JButton installButton = new JButton("Install");
        installButton.setBackground(Cliont);
        installButton.addActionListener(e -> openInstallPanel(modId));
        card.add(installButton, BorderLayout.EAST);

        return card;
    }

    private static void downloadLatestFile(int modId) {
        try {
            String url = "https://api.curseforge.com/v1/mods/" + modId + "/files";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray files = root.getAsJsonArray("data");

            if (files.size() == 0) {
                JOptionPane.showMessageDialog(null, "No files found");
                return;
            }

            JsonObject file = files.get(0).getAsJsonObject();
            String downloadUrl = file.get("downloadUrl").getAsString();
            String fileName = file.get("fileName").getAsString();

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

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Path path = Path.of(System.getProperty("user.home"), "Downloads", fileName);
            Files.createDirectories(path.getParent());
            Files.copy(response.body(), path, StandardCopyOption.REPLACE_EXISTING);

            JOptionPane.showMessageDialog(null, "Downloaded to:\n" + path);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}