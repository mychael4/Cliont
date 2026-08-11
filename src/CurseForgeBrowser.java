import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.GsonBuilder;
import java.util.concurrent.atomic.AtomicReference;

public class CurseForgeBrowser {
    private static final String API_KEY = "$2a$10$8ZIpAo3NEB6uw9njXQP.Ge3cA2uG9HLaktlDbEEH3YpfNdFwGd/pq";
    private static final int GAME_ID = 432;
    private static final int MAX_SEARCH_PAGES = 5;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<Integer, List<FileInfo>> FILE_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger LOAD_GENERATION = new AtomicInteger();
    private static final Color Cliont = Main.cliont;
    private static JPanel contentPanel;
    private static JLabel statusLabel;
    private static JTextField searchField;
    private static int currentIndex = 0;
    private static final int pageSize = 20;
    private static String currentSearch = "";
    private static String currentVersionFilter = "All Versions";
    private static String currentLoaderFilter = "All";
    private static int currentContentType = 6;
    private static Timer searchTimer;
    private static SwingWorker<Void, JPanel> currentLoadWorker;

    public static JPanel create() throws IOException, FontFormatException {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG);
        mainPanel.setBorder(null);

        UIUtils.RoundedPanel topPanel = new UIUtils.RoundedPanel(12, UIUtils.LIGHT_BORDER);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topPanel.setBackground(UIUtils.BUTTON);

        JPanel tabsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        tabsPanel.setOpaque(false);

        String[] contentTypes = {"Mods", "Modpacks", "Resource Packs"};
        int[] contentTypeIds = {6, 4471, 12};
        List<JButton> tabButtons = new ArrayList<>();

        for (int i = 0; i < contentTypes.length; i++) {
            final int idx = i;
            JButton tab = new JButton(contentTypes[i]) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = currentContentType == contentTypeIds[idx] ? Cliont : UIUtils.BUTTON;
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(UIUtils.TEXT);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(getText())) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 4;
                    g2.drawString(getText(), x, y);
                    g2.dispose();
                }
            };
            tab.setContentAreaFilled(false);
            tab.setBorderPainted(false);
            tab.setFocusPainted(false);
            tab.setFont(new Font("Arial", Font.PLAIN, 12));
            tab.setPreferredSize(new Dimension(110, 28));

            final int contentTypeId = contentTypeIds[i];
            tab.addActionListener(e -> {
                currentContentType = contentTypeId;
                currentIndex = 0;
                loadMods();
                tabButtons.forEach(AbstractButton::repaint);
            });

            tabButtons.add(tab);
            tabsPanel.add(tab);
        }

        topPanel.add(tabsPanel);
        topPanel.add(Box.createVerticalStrut(8));

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);

        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        searchField.setFont(new Font("Arial", Font.PLAIN, 14));
        searchField.setPreferredSize(new Dimension(250, 35));
        searchField.setBackground(UIUtils.INPUT);
        searchField.setForeground(UIUtils.TEXT);
        searchField.setCaretColor(Cliont);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void restartTimer() {
                if (searchTimer != null) searchTimer.stop();
                searchTimer = new Timer(300, e -> doSearch());
                searchTimer.setRepeats(false);
                searchTimer.start();
            }

            public void insertUpdate(DocumentEvent e) {
                restartTimer();
            }

            public void removeUpdate(DocumentEvent e) {
                restartTimer();
            }

            public void changedUpdate(DocumentEvent e) {
                restartTimer();
            }
        });

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filterPanel.setOpaque(false);

        JComboBox<String> loaderFilter = new JComboBox<>(new String[]{"All", "Vanilla", "Forge", "Fabric", "NeoForge"});
        styleComboBox(loaderFilter, 120, 32);
        loaderFilter.setBorder(null);

        JComboBox<String> versionFilter = new JComboBox<>();
        styleComboBox(versionFilter, 130, 32);
        versionFilter.addItem("All Versions");
        versionFilter.setBorder(null);

        Set<String> versions = new java.util.TreeSet<>();
        for (ProfileInfo profile : getProfiles()) {
            if (profile.version != null && !profile.version.isBlank()) versions.add(profile.version);
        }
        for (String version : versions) versionFilter.addItem(version);

        loaderFilter.addActionListener(e -> {
            currentLoaderFilter = (String) loaderFilter.getSelectedItem();
            doSearch();
        });
        versionFilter.addActionListener(e -> {
            currentVersionFilter = (String) versionFilter.getSelectedItem();
            doSearch();
        });

        filterPanel.add(makeFilterLabel("Version:"));
        filterPanel.add(versionFilter);
        filterPanel.add(makeFilterLabel("Loader:"));
        filterPanel.add(loaderFilter);

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(filterPanel, BorderLayout.EAST);
        topPanel.add(searchPanel);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        Font mcFont;
        try (InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf")) {
            mcFont = fontStream == null ? new Font("Arial", Font.PLAIN, 18) : Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(18f);
        }

        statusLabel = new JLabel("Lade Mods...");
        statusLabel.setFont(mcFont);
        statusLabel.setForeground(UIUtils.TEXT);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIUtils.BG);
        contentPanel.setBorder(null);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(statusLabel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(UIUtils.BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        loadMods();
        return mainPanel;
    }

    private static JLabel makeFilterLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UIUtils.TEXT);
        return label;
    }

    private static void styleComboBox(JComboBox<String> comboBox, int width, int height) {
        comboBox.setPreferredSize(new Dimension(width, height));
        comboBox.setBackground(UIUtils.INPUT);
        comboBox.setForeground(UIUtils.TEXT);
        comboBox.setFont(new Font("Arial", Font.PLAIN, 12));
    }

    private static void doSearch() {
        currentSearch = searchField.getText().trim();
        currentIndex = 0;
        loadMods();
    }

    static List<ProfileInfo> getProfiles() {
        List<ProfileInfo> list = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return list;

        Path dir = Path.of(appData, ".cliont", "profiles");
        if (!Files.isDirectory(dir)) return list;

        try (java.util.stream.Stream<Path> profileDirs = Files.list(dir)) {
            profileDirs.forEach(p -> {
                Path jsonPath = p.resolve("profile.json");
                if (!Files.isRegularFile(jsonPath)) return;

                try (Reader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    ProfileInfo info = new ProfileInfo();
                    info.path = p;
                    info.name = getString(json, "name", p.getFileName().toString());
                    info.version = getString(json, "version", "");
                    info.loader = getString(json, "loader", "unknown");
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

    static List<FileInfo> getFiles(int modId) {
        return FILE_CACHE.computeIfAbsent(modId, CurseForgeBrowser::fetchFiles);
    }

    private static List<FileInfo> fetchFiles(int modId) {
        List<FileInfo> list = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.curseforge.com/v1/mods/" + modId + "/files"))
                    .timeout(Duration.ofSeconds(20))
                    .header("x-api-key", API_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("CurseForge files request failed: HTTP " + response.statusCode());
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray files = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();

            for (JsonElement el : files) {
                JsonObject f = el.getAsJsonObject();
                FileInfo info = new FileInfo();
                info.fileId = getLong(f, "id", 0L);
                info.modId = modId; // NEW
                info.downloadUrl = getNullableString(f, "downloadUrl");
                info.fileName = getNullableString(f, "fileName");
                if (info.downloadUrl == null || info.fileName == null || info.fileName.isBlank()) continue;

                if (f.has("modLoader") && !f.get("modLoader").isJsonNull())
                    info.modLoader = f.get("modLoader").getAsInt();
                if (f.has("gameVersions") && f.get("gameVersions").isJsonArray()) {
                    for (JsonElement versionElement : f.getAsJsonArray("gameVersions")) {
                        info.gameVersions.add(versionElement.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
                // NEW: parse required dependencies
                if (f.has("dependencies") && f.get("dependencies").isJsonArray()) {
                    for (JsonElement de : f.getAsJsonArray("dependencies")) {
                        JsonObject dep = de.getAsJsonObject();
                        Dependency d = new Dependency();
                        d.modId = dep.has("modId") && !dep.get("modId").isJsonNull() ? dep.get("modId").getAsInt() : 0;
                        d.relationType = dep.has("relationType") && !dep.get("relationType").isJsonNull() ? dep.get("relationType").getAsInt() : 0;
                        if (d.modId != 0) info.dependencies.add(d);
                    }
                }
                if (info.modLoader == -1) info.modLoader = detectLoaderFromName(info.fileName);
                list.add(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.unmodifiableList(list);
    }


    private static void loadMods() {
        int generation = LOAD_GENERATION.incrementAndGet();
        if (currentLoadWorker != null && !currentLoadWorker.isDone()) currentLoadWorker.cancel(true);

        String searchSnapshot = currentSearch;
        String versionSnapshot = currentVersionFilter;
        String loaderSnapshot = currentLoaderFilter;
        int contentTypeSnapshot = currentContentType;

        SwingWorker<Void, JPanel> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                int index = currentIndex;
                int pagesLoaded = 0;
                Set<Integer> seenMods = new HashSet<>();
                List<ModResult> pendingResults = new ArrayList<>(); // NEW: buffered when there's a search term

                SwingUtilities.invokeLater(() -> {
                    if (!isCurrentGeneration(generation)) return;
                    contentPanel.removeAll();
                    statusLabel.setText("Lade Mods...");
                    statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    contentPanel.add(statusLabel);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                });

                while (!isCancelled() && isCurrentGeneration(generation) && pagesLoaded < MAX_SEARCH_PAGES) {
                    String url = buildSearchUrl(contentTypeSnapshot, searchSnapshot, versionSnapshot, loaderSnapshot, index);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("x-api-key", API_KEY)
                            .header("Accept", "application/json")
                            .GET()
                            .build();

                    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IOException("CurseForge search failed: HTTP " + response.statusCode());
                    }

                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
                    if (data.isEmpty()) break;

                    for (JsonElement element : data) {
                        if (isCancelled() || !isCurrentGeneration(generation)) return null;
                        JsonObject mod = element.getAsJsonObject();
                        int modId = mod.get("id").getAsInt();
                        if (!seenMods.add(modId)) continue;

                        String name = getString(mod, "name", "Unnamed project");
                        String summary = getString(mod, "summary", "");
                        long downloads = getLong(mod, "downloadCount", 0L);
                        String iconUrl = getIconUrl(mod);

                        List<FileInfo> files = getFiles(modId);
                        if (!matchesFilter(files, loaderSnapshot, versionSnapshot)) continue;

                        JPanel card = createModCard(name, summary, iconUrl, modId, downloads);

                        if (searchSnapshot.isBlank()) {
                            publish(card); // no search term: keep the original progressive/download-order behavior
                        } else {
                            double score = relevanceScore(searchSnapshot, name, summary);
                            pendingResults.add(new ModResult(card, score)); // buffer for relevance sorting
                        }
                    }

                    pagesLoaded++;
                    if (data.size() < pageSize) break;
                    index += pageSize;
                }

                // NEW: once all pages are in, sort by relevance and publish in that order
                if (!searchSnapshot.isBlank() && !pendingResults.isEmpty()) {
                    pendingResults.sort((a, b) -> Double.compare(b.score, a.score));
                    for (ModResult r : pendingResults) {
                        if (isCancelled() || !isCurrentGeneration(generation)) return null;
                        publish(r.panel);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<JPanel> cards) {
                if (!isCurrentGeneration(generation)) return;
                if (statusLabel.getParent() == contentPanel) contentPanel.removeAll();
                for (JPanel card : cards) {
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    contentPanel.add(card);
                    contentPanel.add(Box.createVerticalStrut(10));
                }
                contentPanel.revalidate();
                contentPanel.repaint();
            }

            @Override
            protected void done() {
                if (!isCurrentGeneration(generation) || isCancelled()) return;
                try {
                    get();
                    if (contentPanel.getComponentCount() == 1 && statusLabel.getParent() == contentPanel) {
                        statusLabel.setText("Keine passenden Mods gefunden");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    contentPanel.removeAll();
                    statusLabel.setText("Ein Problem ist aufgetreten");
                    statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    contentPanel.add(statusLabel);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                }
            }
        };

        currentLoadWorker = worker;
        worker.execute();
    }


    private static boolean isCurrentGeneration(int generation) {
        return LOAD_GENERATION.get() == generation;
    }

    private static String buildSearchUrl(int contentType, String search, String version, String loader, int index) {
        StringBuilder url = new StringBuilder("https://api.curseforge.com/v1/mods/search")
                .append("?gameId=").append(GAME_ID)
                .append("&classId=").append(contentType)
                .append("&sortField=2")
                .append("&sortOrder=desc")
                .append("&pageSize=").append(pageSize)
                .append("&index=").append(index);

        if (search != null && !search.isBlank())
            url.append("&searchFilter=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        if (version != null && !version.isBlank() && !"All Versions".equalsIgnoreCase(version))
            url.append("&gameVersion=").append(URLEncoder.encode(version, StandardCharsets.UTF_8));
        int loaderType = loaderToCurseForgeId(loader);
        if (loaderType > 0) url.append("&modLoaderType=").append(loaderType);
        return url.toString();
    }

    static boolean isCompatible(ProfileInfo p, FileInfo f) {
        if (p == null || f == null) return false;
        if (!versionMatches(f, p.version)) return false;
        int profileLoader = loaderToCurseForgeId(p.loader);
        if (profileLoader == 0) return true;
        if (profileLoader == -1) return f.modLoader == -1;
        return f.modLoader == profileLoader;
    }

    static boolean matchesFilter(FileInfo file, String loaderFilter, String versionFilter) {
        if (file == null) return false;
        if (!isAllVersions(versionFilter) && !versionMatches(file, versionFilter)) return false;
        int requestedLoader = loaderToCurseForgeId(loaderFilter);
        if (requestedLoader == 0) return true;
        if (requestedLoader == -1) return file.modLoader == -1;
        return file.modLoader == requestedLoader;
    }

    static boolean matchesFilter(List<FileInfo> files, String loaderFilter, String versionFilter) {
        if (files == null || files.isEmpty()) return false;
        for (FileInfo file : files) if (matchesFilter(file, loaderFilter, versionFilter)) return true;
        return false;
    }

    private static boolean versionMatches(FileInfo file, String version) {
        if (file == null || version == null || version.isBlank()) return false;
        String versionLower = version.toLowerCase(Locale.ROOT).trim();
        return file.gameVersions.stream().anyMatch(gv -> gv.equals(versionLower));
    }

    private static boolean isAllVersions(String versionFilter) {
        return versionFilter == null || versionFilter.isBlank() || "All Versions".equalsIgnoreCase(versionFilter);
    }

    private static int loaderToCurseForgeId(String loader) {
        if (loader == null) return 0;
        switch (loader.trim().toLowerCase(Locale.ROOT)) {
            case "all":
            case "":
                return 0;
            case "vanilla":
                return -1;
            case "forge":
                return 1;
            case "fabric":
                return 4;
            case "quilt":
                return 5;
            case "neoforge":
                return 6;
            default:
                return 0;
        }
    }

    private static int detectLoaderFromName(String fileName) {
        if (fileName == null) return -1;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return 6;
        if (lower.contains("fabric")) return 4;
        if (lower.contains("forge")) return 1;
        if (lower.contains("quilt")) return 5;
        return -1;
    }

    private static void openCreateProfileDialog(JWindow parent, JPanel list, List<FileInfo> files) {
        JDialog dialog = new JDialog(parent, "Create new profile", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(360, 300);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UIUtils.BG);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel nameLabel = new JLabel("Profile name");
        nameLabel.setForeground(UIUtils.TEXT);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        nameField.setBackground(UIUtils.INPUT);
        nameField.setForeground(UIUtils.TEXT);
        nameField.setCaretColor(Cliont);
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel versionLabel = new JLabel("Minecraft version");
        versionLabel.setForeground(UIUtils.TEXT);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> versionBox = new JComboBox<>();
        styleComboBox(versionBox, 300, 30);
        versionBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Pre-fill with the versions this mod actually supports, since that's most useful here
        Set<String> versions = new java.util.TreeSet<>();
        if (files != null) {
            for (FileInfo f : files) versions.addAll(f.gameVersions);
        }
        for (String v : versions) versionBox.addItem(v);
        if (versionBox.getItemCount() == 0) versionBox.addItem("1.20.1"); // fallback if nothing came back

        JLabel loaderLabel = new JLabel("Loader");
        loaderLabel.setForeground(UIUtils.TEXT);
        loaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<String> loaderBox = new JComboBox<>(new String[]{"Vanilla", "Forge", "Fabric", "Quilt", "NeoForge"});
        styleComboBox(loaderBox, 300, 30);
        loaderBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton createButton = new JButton("Create") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = Cliont;
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        createButton.setForeground(UIUtils.TEXT);
        createButton.setContentAreaFilled(false);
        createButton.setBorderPainted(false);
        createButton.setFocusPainted(false);
        createButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        createButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        createButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a profile name.", "Missing name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String version = (String) versionBox.getSelectedItem();
            String loader = (String) loaderBox.getSelectedItem();

            try {
                ProfileInfo created = createProfile(name, version, loader);
                JPanel card = createInstallCard(created, files == null ? Collections.emptyList() : files);
                list.add(card);
                list.revalidate();
                list.repaint();
                dialog.dispose();
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(dialog, "Could not create profile: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        content.add(nameLabel);
        content.add(Box.createVerticalStrut(4));
        content.add(nameField);
        content.add(Box.createVerticalStrut(12));
        content.add(versionLabel);
        content.add(Box.createVerticalStrut(4));
        content.add(versionBox);
        content.add(Box.createVerticalStrut(12));
        content.add(loaderLabel);
        content.add(Box.createVerticalStrut(4));
        content.add(loaderBox);
        content.add(Box.createVerticalStrut(20));
        content.add(createButton);

        dialog.setContentPane(content);
        dialog.getContentPane().setBackground(UIUtils.BG);
        dialog.setVisible(true);
    }

    static void openInstallPanel(int modId, String modName) {
        JWindow window = new JWindow();

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UIUtils.SIDEBAR);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setName("install_" + modId);
        panel.setBackground(UIUtils.SIDEBAR);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setPreferredSize(new Dimension(500, 80));
        topPanel.setBackground(UIUtils.SIDEBAR);

        JLabel ModTitle = new JLabel("  " + modName);
        ModTitle.setFont(new Font("Arial", Font.BOLD, 26));
        ModTitle.setForeground(Cliont);

        JButton back = new JButton("Back") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = UIUtils.BUTTON;
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        back.addActionListener(e -> window.dispose());
        back.setBorder(null);
        back.setPreferredSize(new Dimension(70, 80));
        back.setBackground(UIUtils.SIDEBAR);

        topPanel.add(back, BorderLayout.WEST);
        topPanel.add(ModTitle);
        panel.add(topPanel, BorderLayout.NORTH);

        // NEW: keep a handle on the fetched files so "Create new profile" can build
        // an install card for the freshly-created profile without refetching.
        AtomicReference<List<FileInfo>> filesRef = new AtomicReference<>(Collections.emptyList());

        // NEW: small bottom bar with the "+ Create new profile" button, left-aligned
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bottomBar.setBackground(UIUtils.SIDEBAR);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JButton createProfileButton = new JButton("+ Create new profile") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? UIUtils.CARD.brighter() : UIUtils.CARD;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 14, 14);
                g2.setColor(UIUtils.LIGHT_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        createProfileButton.setFont(new Font("Arial", Font.BOLD, 13));
        createProfileButton.setForeground(UIUtils.TEXT);
        createProfileButton.setContentAreaFilled(false);
        createProfileButton.setBorderPainted(false);
        createProfileButton.setFocusPainted(false);
        createProfileButton.setPreferredSize(new Dimension(190, 34));
        createProfileButton.addActionListener(e -> openCreateProfileDialog(window, list, filesRef.get()));

        bottomBar.add(createProfileButton);
        panel.add(bottomBar, BorderLayout.SOUTH); // NEW

        window.setContentPane(panel);
        window.setSize(500, 650); // bumped from 600 to fit the new bottom bar comfortably
        window.setLocationRelativeTo(null);

        new SwingWorker<Void, JPanel>() {
            @Override
            protected Void doInBackground() throws IOException {
                List<FileInfo> files = getFiles(modId);
                filesRef.set(files); // NEW
                for (ProfileInfo profile : getProfiles()) {
                    publish(createInstallCard(profile, files));
                }
                return null;
            }

            @Override
            protected void process(List<JPanel> cards) {
                for (JPanel c : cards) list.add(c);
                list.revalidate();
                list.repaint();
            }
        }.execute();

        window.setVisible(true);
    }


    static JPanel createInstallCard(ProfileInfo profile, List<FileInfo> files) throws IOException {
        UIUtils.RoundedPanel card = new UIUtils.RoundedPanel(12, UIUtils.LIGHT_BORDER);
        card.setLayout(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setBackground(UIUtils.CARD);
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(UIUtils.CARD);

        JLabel name = new JLabel(profile.name);
        name.setForeground(UIUtils.TEXT);
        name.setFont(new Font("Arial", Font.BOLD, 14));

        JLabel info = new JLabel(profile.version + " • " + profile.loader);
        info.setForeground(Cliont);
        info.setFont(new Font("Arial", Font.PLAIN, 12));

        leftPanel.add(name);
        leftPanel.add(info);
        card.add(leftPanel, BorderLayout.WEST);

        FileInfo bestMatch = null;
        for (FileInfo f : files) {
            if (isCompatible(profile, f) && (bestMatch == null || f.fileId > bestMatch.fileId)) bestMatch = f;
        }

        JButton install = new JButton("Install") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = UIUtils.CLIONT;
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        install.setForeground(UIUtils.TEXT);
        install.setContentAreaFilled(false);
        install.setBorderPainted(false);

        if (bestMatch != null) {
            JLabel compatible = new JLabel();
            compatible.setForeground(new Color(100, 255, 100));
            compatible.setFont(new Font("Arial", Font.BOLD, 12));

            AtomicBoolean installing = new AtomicBoolean(false);

            //Make this code so it update itself when the mod is installed, so it shows "Installed" instead of "Compatible" (Copilot please do this for me)
            FileInfo finalBestMatch1 = bestMatch;
            Timer InstalledCheckTimer = new Timer(500, e -> {
                if (installing.get() == true) {
                    return;
                }

                try {
                    if (Files.exists(profile.path.resolve("mods").resolve(sanitizeFileName(finalBestMatch1.fileName)))) {
                        compatible.setText("Installed");
                        install.setEnabled(false);
                        install.setVisible(false);
                    } else {
                        compatible.setText("Compatible");
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            InstalledCheckTimer.start();

            FileInfo finalBestMatch = bestMatch;
            install.addActionListener(e -> {
                install.setEnabled(false);
                install.setVisible(false);
                installing.set(true);
                compatible.setText("Installing...");
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        installToProfile(profile, finalBestMatch);
                        return null;
                    }

                    @Override
                    protected void done() {
                        installing.set(false);
                        install.setEnabled(true);
                    }
                }.execute();
            });

            JPanel rightPanel = new JPanel(new BorderLayout(10, 0));
            rightPanel.setBackground(UIUtils.CARD);
            rightPanel.add(compatible, BorderLayout.WEST);
            rightPanel.add(install, BorderLayout.EAST);
            card.add(rightPanel, BorderLayout.EAST);
        } else {
            JLabel notCompatible = new JLabel("Not compatible");
            notCompatible.setForeground(new Color(255, 100, 100));
            notCompatible.setFont(new Font("Arial", Font.BOLD, 12));
            card.add(notCompatible, BorderLayout.EAST);
        }
        return card;
    }

    static void installToProfile(ProfileInfo profile, FileInfo file) {
        Set<Integer> visitedMods = new HashSet<>();
        if (file.modId != 0) visitedMods.add(file.modId); // don't chase a dependency back to itself
        installToProfileWithDependencies(profile, file, visitedMods, true);
    }

    private static void installToProfileWithDependencies(ProfileInfo profile, FileInfo file, Set<Integer> visitedMods, boolean isMainRequest) {
        boolean ok = downloadAndPlaceFile(profile, file, isMainRequest);
        if (!ok) return; // main file failed; don't chase its dependencies

        for (Dependency dep : file.dependencies) {
            if (dep.relationType != 3) continue; // only required dependencies
            if (!visitedMods.add(dep.modId)) continue; // already handled this run (avoids cycles)

            List<FileInfo> depFiles = getFiles(dep.modId);
            FileInfo bestDepMatch = null;
            for (FileInfo f : depFiles) {
                if (isCompatible(profile, f) && (bestDepMatch == null || f.fileId > bestDepMatch.fileId)) {
                    bestDepMatch = f;
                }
            }

            if (bestDepMatch == null) {
                System.err.println("No compatible file found for required dependency mod " + dep.modId);
                continue;
            }

            try {
                if (Files.exists(profile.path.resolve("mods").resolve(sanitizeFileName(bestDepMatch.fileName)))) {
                    continue; // dependency already installed
                }
            } catch (IOException ignored) {
                // fall through and try installing anyway
            }

            installToProfileWithDependencies(profile, bestDepMatch, visitedMods, false);
        }
    }

    private static boolean downloadAndPlaceFile(ProfileInfo profile, FileInfo file, boolean isMainRequest) {
        try {
            String safeFileName = sanitizeFileName(file.fileName);
            Path modsDir = profile.path.resolve("mods").normalize();
            Files.createDirectories(modsDir);
            Path target = modsDir.resolve(safeFileName).normalize();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(file.downloadUrl))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

            Path tempFile = Files.createTempFile(modsDir, safeFileName, ".tmp");
            try (InputStream in = response.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            // Only pop up a dialog for the mod the user actually clicked "Install" on —
            // a failed dependency shouldn't spam multiple error dialogs.
            if (isMainRequest) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Error installing: " + e.getMessage(), "Installation Error", JOptionPane.ERROR_MESSAGE));
            }
            return false;
        }
    }


    private static String sanitizeFileName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) throw new IOException("Missing file name");
        String clean = Path.of(fileName).getFileName().toString();
        if (!clean.equals(fileName) || clean.contains("..") || clean.contains("/") || clean.contains("\\") || clean.matches(".*[<>:\"|?*].*"))
            throw new IOException("Unsafe file name: " + fileName);
        return clean;
    }

    private static JPanel createModCard(String name, String summary, String iconUrl, int modId, long downloads) {
        UIUtils.RoundedPanel card = new UIUtils.RoundedPanel(12, UIUtils.LIGHT_BORDER);
        card.setLayout(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(UIUtils.CARD);
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // padding only now — the rounded border is painted by RoundedPanel itself

        card.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                card.setBackground(UIUtils.HOVER);
            }

            public void mouseExited(MouseEvent e) {
                card.setBackground(UIUtils.CARD);
            }
        });

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(80, 80));
        loadIconAsync(iconLabel, iconUrl);
        card.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(UIUtils.CARD);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 16));
        nameLabel.setForeground(UIUtils.TEXT);

        JLabel summaryLabel = new JLabel("<html><body style='width:250px'>" + escapeHtml(summary) + "</body></html>");
        summaryLabel.setForeground(UIUtils.TEXT_MUTED);

        JLabel downloadLabel = new JLabel(NumberFormat.getInstance().format(downloads) + " downloads");
        downloadLabel.setForeground(Cliont);

        textPanel.add(nameLabel);
        textPanel.add(summaryLabel);
        textPanel.add(downloadLabel);
        card.add(textPanel, BorderLayout.CENTER);

        JButton installButton = new JButton("Install") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = Cliont;
                g2.setColor(getModel().isPressed() ? bg.darker() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(getForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        installButton.setBorder(null);
        installButton.setBackground(new Color(0, 0, 0, 0));
        installButton.setPreferredSize(new Dimension(70, 90));
        installButton.addActionListener(e -> openInstallPanel(modId, name));
        card.add(installButton, BorderLayout.EAST);
        return card;
    }


    private static void loadIconAsync(JLabel iconLabel, String iconUrl) {
        if (iconUrl == null || iconUrl.isBlank()) return;
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                URL url = URI.create(iconUrl).toURL();
                try (InputStream in = url.openStream()) {
                    Image image = ImageIO.read(in);
                    if (image == null) return null;
                    return new ImageIcon(image.getScaledInstance(80, 80, Image.SCALE_SMOOTH));
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) iconLabel.setIcon(icon);
                } catch (Exception ignored) {
                    // Missing icons should not break the mod list.
                }
            }
        }.execute();
    }

    private static String getIconUrl(JsonObject mod) {
        if (mod.has("logo") && !mod.get("logo").isJsonNull()) {
            JsonObject logo = mod.getAsJsonObject("logo");
            return getNullableString(logo, "thumbnailUrl");
        }
        return null;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        String value = getNullableString(object, key);
        return value == null ? fallback : value;
    }

    private static String getNullableString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        return object.get(key).getAsString();
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        return object.get(key).getAsLong();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }

    static class ProfileInfo {
        Path path;
        String name;
        String version;
        String loader;
    }

    static class FileInfo {
        long fileId;
        int modId; // NEW: the CurseForge project id this file belongs to
        String downloadUrl;
        String fileName;
        List<String> gameVersions = new ArrayList<>();
        int modLoader = -1;
        List<Dependency> dependencies = new ArrayList<>(); // NEW
    }

    static class ModResult {
        JPanel panel;
        double score;
        ModResult(JPanel panel, double score) {
            this.panel = panel;
            this.score = score;
        }
    }

    static class Dependency {
        int modId;
        int relationType; // 3 = required dependency (per CurseForge API)
    }

    private static double similarity(String query, String target) {
        if (query == null || query.isBlank() || target == null) return 0;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String t = target.toLowerCase(Locale.ROOT).trim();
        if (t.equals(q)) return 1.0;
        if (t.startsWith(q)) return 0.95;
        if (t.contains(q)) return 0.85;
        int dist = levenshtein(q, t);
        int maxLen = Math.max(q.length(), t.length());
        if (maxLen == 0) return 0;
        return Math.max(0, 1.0 - (double) dist / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private static double relevanceScore(String query, String name, String summary) {
        double score = similarity(query, name);
        if (summary != null && !query.isBlank() && summary.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim())) {
            score += 0.1; // small tiebreaker bonus if it also shows up in the description
        }
        return score;
    }

    private static ProfileInfo createProfile(String name, String version, String loader) throws IOException {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IOException("APPDATA environment variable not set");
        }

        String folderName = sanitizeFileName(name);
        Path profileDir = Path.of(appData, ".cliont", "profiles", folderName);
        Files.createDirectories(profileDir);
        Files.createDirectories(profileDir.resolve("mods"));

        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("version", version == null ? "" : version);
        json.addProperty("loader", loader == null ? "unknown" : loader.toLowerCase(Locale.ROOT));

        Path jsonPath = profileDir.resolve("profile.json");
        try (java.io.Writer writer = Files.newBufferedWriter(jsonPath, StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(json));
        }

        ProfileInfo info = new ProfileInfo();
        info.path = profileDir;
        info.name = name;
        info.version = version;
        info.loader = loader == null ? "unknown" : loader.toLowerCase(Locale.ROOT);
        return info;
    }
}

