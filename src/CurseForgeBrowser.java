import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    public static JPanel create() {

        Color Cliont = new Color(188, 19, 254);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG);

        // ---------- SEARCH BAR ----------
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        searchField = new JTextField();
        searchField.setFont(new Font("Arial", Font.PLAIN, 16));
        searchField.setPreferredSize(new Dimension(200, 35));


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
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(BG);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // ---------- LOAD MORE ----------
        JButton loadMore = new JButton("Load More");
        loadMore.addActionListener(e -> {
            currentIndex += pageSize;
            loadMods();
        });
        mainPanel.add(loadMore, BorderLayout.SOUTH);

        // erste Mod-Ladung
        loadMods();

        return mainPanel;
    }

    private static void doSearch() {
        currentSearch = searchField.getText();
        currentIndex = 0;
        contentPanel.removeAll();
        contentPanel.revalidate();
        contentPanel.repaint();
        loadMods();
    }

    private static void loadMods() {
        new SwingWorker<Void, JPanel>() {
            protected Void doInBackground() throws Exception {
                String url = "https://api.curseforge.com/v1/mods/search"
                        + "?gameId=" + GAME_ID
                        + "&sortField=2"
                        + "&sortOrder=desc"
                        + "&pageSize=" + pageSize
                        + "&index=" + currentIndex;

                if (!currentSearch.isEmpty()) {
                    url += "&searchFilter=" + URLEncoder.encode(currentSearch, StandardCharsets.UTF_8);
                }

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray data = root.getAsJsonArray("data");

                for (JsonElement element : data) {
                    JsonObject mod = element.getAsJsonObject();
                    String name = mod.get("name").getAsString();
                    String summary = mod.get("summary").getAsString();
                    int modId = mod.get("id").getAsInt();
                    long downloads = mod.get("downloadCount").getAsLong();

                    String iconUrl = null;
                    if (mod.has("logo") && !mod.get("logo").isJsonNull()) {
                        iconUrl = mod.getAsJsonObject("logo").get("thumbnailUrl").getAsString();
                    }

                    JPanel card = createModCard(name, summary, iconUrl, modId, downloads);
                    publish(card);
                }
                return null;
            }

            protected void process(List<JPanel> cards) {
                for (JPanel card : cards) {
                    contentPanel.add(card);
                }
                contentPanel.revalidate();
                contentPanel.repaint();
            }

        }.execute();
    }

    private static JPanel createModCard(String name, String summary, String iconUrl, int modId, long downloads) {

        Color Cliont = new Color(188, 19, 254);

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
        installButton.addActionListener(e -> downloadLatestFile(modId));
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