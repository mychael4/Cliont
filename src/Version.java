import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.net.ssl.SSLContext;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Version {

    static JPanel mainPanel;
    static JPanel profileGrid;
    static CardLayout layout;

    public static JPanel create() throws IOException, FontFormatException {


        layout = new CardLayout();
        mainPanel = new JPanel(layout);

        mainPanel.add(createProfilesPanel(), "profiles");
        mainPanel.add(createCreatePanel(), "create");

        layout.show(mainPanel, "profiles");

        return mainPanel;
    }

    static JPanel ProfileView(Path profilePath) {

        Color bg = new Color(43, 43, 43);
        Color Cliont = new Color(188, 19, 254);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(bg);

        JButton back = new JButton("←");
        back.setBackground(Cliont);

        back.addActionListener(e -> {
            layout.show(mainPanel, "profiles");
        });

        // =========================
        // 🔥 HEADER (wie CurseForge)
        // =========================
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(60, 60, 60));
        header.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // ICON
        ImageIcon icon = new ImageIcon(Version.class.getResource("/Icon.png"));
        JLabel img = new JLabel(new ImageIcon(
                icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH)
        ));

        // NAME
        JLabel name = new JLabel(profilePath.getFileName().toString());
        name.setForeground(Color.WHITE);
        name.setFont(new Font("Arial", Font.BOLD, 18));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.setBackground(header.getBackground());
        left.add(back); // GANZ LINKS vor dem Icon
        left.add(img);
        left.add(name);

        // START BUTTON
        JButton start = new JButton("Start");
        start.setSize(50, 20);
        start.setBackground(Cliont);

        start.addActionListener(e -> {
            try {
                Main.setAppBusy(true);
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                Process process = MinecraftLauncher.launch();

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    } finally {
                        Main.setAppBusy(false);
                    }
                }).start();
            } catch (Exception ex) {
                Main.setAppBusy(false);
                ex.printStackTrace();
            }
        });

        header.add(left, BorderLayout.WEST);
        header.add(start, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // =========================
        // 🔍 SEARCH BAR + BUTTON
        // =========================
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(bg);

        JTextField search = new JTextField();
        JButton addMod = new JButton("+");

        topBar.add(search, BorderLayout.CENTER);
        topBar.add(addMod, BorderLayout.EAST);

        root.add(topBar, BorderLayout.SOUTH);

        // =========================
        // 📦 MOD LIST
        // =========================
        JPanel modsPanel = new JPanel();
        modsPanel.setLayout(new BoxLayout(modsPanel, BoxLayout.Y_AXIS));
        modsPanel.setBackground(bg);
        modsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(modsPanel);
        root.add(scroll, BorderLayout.CENTER);

        // =========================
        // 🔥 MODS LADEN
        // =========================
        Path modsDir = profilePath.resolve("mods");

        try {
            if (!Files.exists(modsDir))
                Files.createDirectories(modsDir);

            Files.list(modsDir).forEach(mod -> {

                modsPanel.add(createModCard(mod));

            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        // =========================
        // 🔍 SEARCH FILTER
        // =========================
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {

            void filter() {
                String text = search.getText().toLowerCase();

                modsPanel.removeAll();

                try {
                    Files.list(modsDir).forEach(mod -> {
                        if (mod.getFileName().toString().toLowerCase().contains(text)) {
                            modsPanel.add(createModCard(mod));
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }

                modsPanel.revalidate();
                modsPanel.repaint();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });

        return root;
    }

    static JPanel createModCard(Path modPath) {

        JPanel card = new JPanel(new BorderLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        card.setBackground(new Color(70, 70, 70));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JLabel name = new JLabel(modPath.getFileName().toString());
        name.setForeground(Color.WHITE);
        name.setFont(new Font("Arial", Font.PLAIN, 14));

        card.add(name, BorderLayout.WEST);

        return card;
    }

    static void openProfileView(Path profilePath) {

        JPanel panel = createProfileView(profilePath);

        mainPanel.add(panel, "profile_" + profilePath.getFileName());
        layout.show(mainPanel, "profile_" + profilePath.getFileName());
    }

    // ================================
    // PROFILE PANEL
    // ================================
    static JPanel createProfilesPanel() throws IOException, FontFormatException {

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(43, 43, 43));

        Color Cliont = new Color(188, 19, 254);

        // TOP BAR
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(43, 43, 43));

        JLabel title = new JLabel("Profiles");
        title.setForeground(Cliont);
        title.setFont(mcFont);

        JButton add = new JButton("+") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Hintergrundfarbe basierend auf Status (Hover/Klick)
                if (getModel().isArmed()) {
                    g2.setColor(new Color(55, 55, 55)); // Dunkler beim Klicken
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(70, 70, 70)); // Heller beim Hover
                } else {
                    g2.setColor(new Color(70, 70, 70)); // Deine Standardfarbe (Cliont)
                }

                // Zeichne den Kreis
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);

                // Text zeichnen
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(getText(), x, y);

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(40, 40); // Hier stellst du die Größe ein
            }

            @Override
            public boolean contains(int x, int y) {
                // Macht nur den Kreis anklickbar, nicht die unsichtbaren Ecken
                double radius = getWidth() / 2.0;
                return Math.pow(x - radius, 2) + Math.pow(y - radius, 2) <= Math.pow(radius, 2);
            }
        };

        add.setContentAreaFilled(false); // Verhindert das Zeichnen des Standard-Rechtecks
        add.setBorderPainted(false);
        add.setFocusPainted(false);
        add.setFont(mcFont);

        // ABSTAND ZUM RAND (GridBagConstraints)
        GridBagConstraints gbcBtn = new GridBagConstraints();
        gbcBtn.gridx = 0;
        gbcBtn.gridy = 1; // Unter dem Text (oder 1 für rechts daneben)
        gbcBtn.insets = new Insets(20, 20, 20, 20); // 20 Pixel Sicherheitsabstand zu allen Seiten
        gbcBtn.anchor = GridBagConstraints.CENTER;

        add.addActionListener(e -> layout.show(mainPanel, "create"));

        top.add(title, BorderLayout.WEST);
        top.add(add, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);

        profileGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        profileGrid.setBackground(new Color(43, 43, 43));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(43, 43, 43));
        wrapper.add(profileGrid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);

        root.add(scroll, BorderLayout.CENTER);

        loadProfiles();

        return root;
    }

    // ================================
    // LOAD PROFILES
    // ================================
    static void loadProfiles() {

        profileGrid.removeAll();

        Path profilesDir = Paths.get(System.getenv("APPDATA"), ".cliont", "profiles");

        try {

            if (!Files.exists(profilesDir))
                Files.createDirectories(profilesDir);

            Files.list(profilesDir).forEach(p -> {

                profileGrid.add(createProfileCard(p));

            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        profileGrid.revalidate();
        profileGrid.repaint();
    }

    // ================================
    // PROFILE CARD (3:4)
    // ================================
    static JPanel createProfileCard(Path profilePath) {

        Color Cliont = new Color(188, 19, 254);

        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(180, 240));
        card.setBackground(new Color(60, 60, 60));
        card.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));

        // 🔥 ICON LADEN
        ImageIcon icon = new ImageIcon(
                Version.class.getResource("/Icon.png")
        );

        Image scaled = icon.getImage().getScaledInstance(160, 160, Image.SCALE_SMOOTH);
        JLabel iconLabel = new JLabel(new ImageIcon(scaled));

        JLabel name = new JLabel(profilePath.getFileName().toString(), SwingConstants.CENTER);
        name.setForeground(Color.WHITE);

        JButton start = new JButton("Start");
        start.setBackground(Cliont);

        start.addActionListener(e -> {
            try {
                Main.setAppBusy(true);
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                Process process = MinecraftLauncher.launch();

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    } finally {
                        Main.setAppBusy(false);
                    }
                }).start();
            } catch (Exception ex) {
                Main.setAppBusy(false);
                ex.printStackTrace();
            }
        });

        // 🔥 CLICK → OPEN PROFILE VIEW
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                openProfileView(profilePath);
            }
        });

        card.add(iconLabel, BorderLayout.NORTH);
        card.add(name, BorderLayout.CENTER);
        card.add(start, BorderLayout.SOUTH);

        return card;
    }

    // ================================
    // CREATE PROFILE PANEL
    // ================================
    static JPanel createCreatePanel() throws IOException, FontFormatException {

        InputStream fontStream = Main.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        Color Cliont = new Color(188, 19, 254);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(43, 43, 43));

        // ===== TOP BAR =====
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(43, 43, 43));

        JLabel title = new JLabel("Create Profile");
        title.setForeground(Cliont);
        title.setFont(mcFont);

        JButton close = new JButton("x");
        close.setFont(mcFont);
        close.setSize(10, 10);

        close.addActionListener(e -> layout.show(mainPanel, "profiles"));

        top.add(title, BorderLayout.WEST);
        top.add(close, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);

        // ===== CENTER PANEL =====
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(43, 43, 43));

        panel.add(Box.createVerticalStrut(20));

        JLabel nameLabel = new JLabel("Profile Name");
        nameLabel.setForeground(Color.WHITE);

        JTextField nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(200, 25));

        JLabel versionLabel = new JLabel("Minecraft Version");
        versionLabel.setForeground(Color.WHITE);

        JComboBox<String> versionBox = new JComboBox<>();
        versionBox.setMaximumSize(new Dimension(200, 25));

        JLabel loaderLabel = new JLabel("Loader");
        loaderLabel.setForeground(Color.WHITE);

        JComboBox<String> loaderBox = new JComboBox<>();
        loaderBox.setMaximumSize(new Dimension(200, 25));

        loaderBox.addItem("Vanilla");
        loaderBox.addItem("Fabric");
        loaderBox.addItem("Forge");
        loaderBox.addItem("NeoForge");

        JButton create = new JButton("Create");
        create.setAlignmentX(Component.LEFT_ALIGNMENT);
        create.setBackground(Cliont);

        panel.add(nameLabel);
        panel.add(nameField);

        panel.add(Box.createVerticalStrut(10));

        panel.add(versionLabel);
        panel.add(versionBox);

        panel.add(Box.createVerticalStrut(10));

        panel.add(loaderLabel);
        panel.add(loaderBox);

        panel.add(Box.createVerticalStrut(20));

        panel.add(create);

        root.add(panel, BorderLayout.CENTER);

        // ===== VERSIONEN LADEN =====
        new Thread(() -> {

            try {

                URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

                HttpClient client = HttpClient.newBuilder()
                        .sslContext(SSLContext.getDefault())
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                        .build();

                HttpResponse<InputStream> response =
                        client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                JsonObject manifest = JsonParser.parseReader(
                                new InputStreamReader(response.body()))
                        .getAsJsonObject();

                JsonArray versions = manifest.getAsJsonArray("versions");

                for (JsonElement v : versions) {

                    String id = v.getAsJsonObject().get("id").getAsString();

                    SwingUtilities.invokeLater(() -> versionBox.addItem(id));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();

        // ===== CREATE BUTTON =====
        create.addActionListener(e -> {

            String name = nameField.getText();
            String version = (String) versionBox.getSelectedItem();
            String loader = (String) loaderBox.getSelectedItem();

            if (name.isEmpty()) return;

            // 🔥 Lange Operation in Background-Thread!
            new Thread(() -> {
                try {

                    Path profileDir = Paths.get(System.getenv("APPDATA"),
                            ".cliont", "profiles", name);

                    Files.createDirectories(profileDir);

                    JsonObject profileJson = new JsonObject();
                    profileJson.addProperty("name", name);
                    profileJson.addProperty("version", version);
                    profileJson.addProperty("loader", loader);

                    try (FileWriter writer = new FileWriter(profileDir.resolve("profile.json").toFile())) {
                        new GsonBuilder().setPrettyPrinting().create().toJson(profileJson, writer);
                    }

                    //Aktuell nur Vanilla und Fabric
                    if (loader.equals("Vanilla")) {
                        downloadVanilla(version, profileDir);
                        System.out.println("Downloading Vanilla");
                    }
                    else if (loader.equals("Fabric")) {
                        downloadFabric(version, profileDir);
                        System.out.println("Downloading Fabric");
                    }

                    // 🔥 UI Update auf EDT!
                    SwingUtilities.invokeLater(() -> {
                        layout.show(mainPanel, "profiles");
                        loadProfiles();
                        JOptionPane.showMessageDialog(null,
                                "Profile '" + name + "' created successfully!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                "Error creating profile: " + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();

        });

        return root;
    }

    // ================================
    // DOWNLOAD LIBRARIES
    // ================================

    static void downloadLibraries(JsonObject versionJson, Path mcDir) throws Exception {

        JsonArray libs = versionJson.getAsJsonArray("libraries");

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .build();

        String os = System.getProperty("os.name").toLowerCase();
        String osKey;

        if (os.contains("win")) {
            osKey = "windows";
        } else if (os.contains("mac")) {
            osKey = "macos";
        } else {
            osKey = "linux";
        }

        for (JsonElement libElement : libs) {

            JsonObject lib = libElement.getAsJsonObject();

            if (!MinecraftLauncher.isLibraryAllowed(lib)) {
                if (lib.has("name")) {
                    System.out.println("Skipping disallowed library: " + lib.get("name").getAsString());
                }
                continue;
            }

            String url = null;
            String path = null;

            // 1. Versuche artifact path (Standard)
            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");

                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    url = artifact.get("url").getAsString();
                    path = artifact.get("path").getAsString();
                }
            }

            // 2. Fallback: Maven path (für Loader wie Fabric, Forge, etc.)
            if (path == null && lib.has("name")) {
                String maven = lib.get("name").getAsString();
                String[] parts = maven.split(":");

                if (parts.length >= 3) {
                    String group = parts[0].replace('.', '/');
                    String artifact = parts[1];
                    String version = parts[2];
                    path = group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
                    
                    // Repository basierend auf group:artifact auswählen
                    if (maven.contains("net.fabricmc")) {
                        url = "https://maven.fabricmc.net/" + path;
                    } else if (maven.contains("org.ow2.asm") || maven.contains("org.spongepowered")) {
                        // ASM und Sponge/Mixin aus Maven Central
                        url = "https://repo1.maven.org/maven2/" + path;
                    } else {
                        url = "https://libraries.minecraft.net/" + path;
                    }
                    System.out.println("[FALLBACK] Downloading: " + maven + " from " + url);
                }
            }

            if (path == null || url == null) {
                if (lib.has("name")) {
                    System.out.println("[SKIP] Could not resolve: " + lib.get("name").getAsString());
                }
                continue;
            }

            // =========================
            // NORMALE LIBRARY
            // =========================
            Path libPath = mcDir.resolve("libraries").resolve(path);

            if (!Files.exists(libPath)) {

                Files.createDirectories(libPath.getParent());

                System.out.println("Downloading library: " + path);

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .build();

                    HttpResponse<InputStream> response =
                            client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    Files.copy(response.body(), libPath,
                            StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[SUCCESS] Downloaded: " + path);
                } catch (Exception e) {
                    System.out.println("Failed to download: " + url);
                    e.printStackTrace();
                }
            }

            // =========================
            // NATIVES (NUR RICHTIGES OS)
            // =========================
            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");
                
                if (downloads.has("classifiers")) {
                    JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                    String nativeKey = null;

                    for (String key : classifiers.keySet()) {
                        if (key.contains(osKey)) {
                            nativeKey = key;
                            break;
                        }
                    }

                    if (nativeKey != null) {
                        JsonObject nativeObj = classifiers.getAsJsonObject(nativeKey);

                        String nativeUrl = nativeObj.get("url").getAsString();
                        String nativePath = nativeObj.get("path").getAsString();

                        Path nativeLibPath = mcDir.resolve("libraries").resolve(nativePath);

                        if (!Files.exists(nativeLibPath)) {
                            Files.createDirectories(nativeLibPath.getParent());

                            System.out.println("Downloading native: " + nativePath);

                            try {
                                HttpRequest nativeRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(nativeUrl))
                                        .build();

                                HttpResponse<InputStream> nativeResponse =
                                        client.send(nativeRequest, HttpResponse.BodyHandlers.ofInputStream());

                                Files.copy(nativeResponse.body(), nativeLibPath,
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (Exception e) {
                                System.out.println("Failed to download native: " + nativeUrl);
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    // ================================
    // DOWNLOAD VANILLA
    // ================================
    static void downloadVanilla(String version, Path profileDir) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest manifestRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                .build();

        System.out.println("Downloading version metadata for " + version);

        HttpResponse<InputStream> manifestResponse =
                client.send(manifestRequest, HttpResponse.BodyHandlers.ofInputStream());

        JsonObject manifest = JsonParser.parseReader(
                new InputStreamReader(manifestResponse.body())
        ).getAsJsonObject();

        JsonArray versions = manifest.getAsJsonArray("versions");

        String versionURL = null;

        for (JsonElement v : versions) {

            JsonObject obj = v.getAsJsonObject();

            if (obj.get("id").getAsString().equals(version)) {
                versionURL = obj.get("url").getAsString();
                break;
            }
        }

        if (versionURL == null)
            throw new RuntimeException("Version not found");

        HttpRequest versionRequest = HttpRequest.newBuilder()
                .uri(URI.create(versionURL))
                .build();

        HttpResponse<InputStream> versionResponse =
                client.send(versionRequest, HttpResponse.BodyHandlers.ofInputStream());

        JsonReader versionReader = new JsonReader(new InputStreamReader(versionResponse.body()));
        versionReader.setLenient(true);  // <- HIER

        JsonObject versionJson = JsonParser.parseReader(versionReader).getAsJsonObject();

        try (FileWriter writer = new FileWriter(
                profileDir.resolve("version.json").toFile())) {

            new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(versionJson, writer);
        }

        String jarURL = versionJson
                .getAsJsonObject("downloads")
                .getAsJsonObject("client")
                .get("url")
                .getAsString();

        HttpRequest jarRequest = HttpRequest.newBuilder()
                .uri(URI.create(jarURL))
                .build();

        HttpResponse<InputStream> jarResponse =
                client.send(jarRequest, HttpResponse.BodyHandlers.ofInputStream());

        System.out.println("Downloading client.jar...");

        Files.copy(
                jarResponse.body(),
                profileDir.resolve("client.jar"),
                StandardCopyOption.REPLACE_EXISTING
        );
        downloadLibraries(versionJson, profileDir);

        getJavaRuntime(versionJson);

        downloadAssets(versionJson, profileDir);
        downloadLogging(versionJson, profileDir);

        Path nativesDir = profileDir.resolve("natives");
        extractAllNatives(profileDir.resolve("libraries"), nativesDir);
    }

    static void downloadFabric(String version, Path profileDir) throws Exception {

        SSLFix.apply();
        // 1. Vanilla als Basis
        downloadVanilla(version, profileDir);

        // 2. Loader Version holen
        String loaderVersion = getLatestFabricLoader();

        System.out.println("Downloading Fabric loader: " + loaderVersion);

        // 3. Fabric JSON laden
        String url = "https://meta.fabricmc.net/v2/versions/loader/"
                + version + "/" + loaderVersion + "/profile/json";

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        JsonReader fabricReader = new JsonReader(new InputStreamReader(response.body()));
        fabricReader.setLenient(true);  // <- HIER

        JsonObject fabricJson = JsonParser.parseReader(fabricReader).getAsJsonObject();

        // speichern
        try (FileWriter writer = new FileWriter(
                profileDir.resolve("fabric.json").toFile())) {

            new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(fabricJson, writer);
        }

        // Libraries laden (Fabric + zusätzliche)
        downloadLibraries(fabricJson, profileDir);
    }

    static String getLatestFabricLoader() throws Exception {

        String url = "https://meta.fabricmc.net/v2/versions/loader";

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        JsonArray arr = JsonParser.parseReader(
                new InputStreamReader(response.body())
        ).getAsJsonArray();

        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();

            if (obj.has("stable") && obj.get("stable").getAsBoolean()) {
                return obj.get("version").getAsString();
            }
        }

        return arr.get(0).getAsJsonObject().get("version").getAsString();
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

            System.out.println("Downloading asset: " + name);

            try (InputStream in = new URL(assetUrl).openStream()) {
                Files.copy(in, assetFile, StandardCopyOption.REPLACE_EXISTING);
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

        System.out.println("Downloading logging config: " + id);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, logFile, StandardCopyOption.REPLACE_EXISTING);
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

    static Path getJavaRuntime(JsonObject versionJson) throws Exception {

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

        System.out.println("Downloading Java " + javaVersion + " runtime...");

        MinecraftLauncher.unzip(zip, runtimeDir);
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

    public static void installFabricApi(String minecraftVersion, Path profileDir) throws Exception {
        // mods-Ordner erstellen
        Path modsDir = profileDir.resolve("mods");
        Files.createDirectories(modsDir);

        // CurseForge API-Key (muss in deinem Programm hinterlegt sein)
        String apiKey = "$2a$10$8ZIpAo3NEB6uw9njXQP.Ge3cA2uG9HLaktlDbEEH3YpfNdFwGd/pq";

        // Fabric API Projekt-ID auf CurseForge
        // https://www.curseforge.com/minecraft/mc-mods/fabric-api
        int projectId = 306612;

        HttpClient client = HttpClient.newHttpClient();

        // Alle Dateien des Projekts abrufen
        String apiUrl = "https://api.curseforge.com/v1/mods/" + projectId + "/files";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("x-api-key", apiKey)
                .header("User-Agent", "Cliont Launcher")
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray files = root.getAsJsonArray("data");

        JsonObject selectedFile = null;

        // Neueste passende Datei finden
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();

            // Minecraft-Versionen prüfen
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
                break; // Die Liste ist normalerweise absteigend sortiert (neueste zuerst)
            }
        }

        if (selectedFile == null) {
            throw new RuntimeException(
                    "Keine kompatible Fabric API für Minecraft " + minecraftVersion + " gefunden.");
        }

        String downloadUrl = selectedFile.get("downloadUrl").getAsString();
        String fileName = selectedFile.get("fileName").getAsString();

        if (downloadUrl == null || downloadUrl.equals("null")) {
            throw new RuntimeException("CurseForge hat keine Download-URL zurückgegeben.");
        }

        Path target = modsDir.resolve(fileName);

        // Bereits vorhanden?
        if (Files.exists(target)) {
            System.out.println("Fabric API ist bereits installiert.");
            return;
        }

        // Datei herunterladen
        HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Cliont Launcher")
                .build();

        client.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(target));

        System.out.println("Fabric API erfolgreich installiert: " + fileName);
    }
}