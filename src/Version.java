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

    static JPanel createProfileView(Path profilePath) {

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
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                MinecraftLauncher.launch();
            } catch (Exception ex) {
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
        JPanel modsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modsPanel.setBackground(bg);

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
        card.setPreferredSize(new Dimension(120, 140));
        card.setBackground(new Color(70, 70, 70));

        // 🔥 Fake Icon (später aus .jar lesen möglich)
        ImageIcon icon = new ImageIcon(Version.class.getResource("/Icon.png"));
        JLabel img = new JLabel(new ImageIcon(
                icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH)
        ));

        JLabel name = new JLabel(modPath.getFileName().toString(), SwingConstants.CENTER);
        name.setForeground(Color.WHITE);

        card.add(img, BorderLayout.NORTH);
        card.add(name, BorderLayout.SOUTH);

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

        JButton add = new JButton("+");
        add.setFont(mcFont);
        add.setSize(10, 10);


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
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                MinecraftLauncher.launch();
            } catch (Exception ex) {
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
                }
                else if (loader.equals("Fabric")) {
                    downloadFabric(version, profileDir);
                }

                layout.show(mainPanel, "profiles");
                loadProfiles();

            } catch (Exception ex) {
                ex.printStackTrace();
            }

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

        for (JsonElement libElement : libs) {

            JsonObject lib = libElement.getAsJsonObject();

            if (!lib.has("downloads")) continue;

            JsonObject downloads = lib.getAsJsonObject("downloads");

            // ===== NORMALER ARTIFACT =====
            if (downloads.has("artifact")) {

                JsonObject artifact = downloads.getAsJsonObject("artifact");

                String url = artifact.get("url").getAsString();
                String path = artifact.get("path").getAsString();

                Path libPath = mcDir.resolve("libraries").resolve(path);

                if (!Files.exists(libPath)) {
                    Files.createDirectories(libPath.getParent());

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .build();

                    HttpResponse<InputStream> response =
                            client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    Files.copy(response.body(), libPath, StandardCopyOption.REPLACE_EXISTING);

                    System.out.println("Downloaded library: " + path);
                }
            }

            // ===== 🔥 NATIVES (DAS HAT GEFehlt) =====
            if (lib.has("natives") && downloads.has("classifiers")) {

                String nativeKey = MinecraftLauncher.getNativeClassifierKey(lib);

                if (nativeKey != null) {
                    nativeKey = MinecraftLauncher.resolveClassifierKey(nativeKey);

                    JsonObject classifiers = downloads.getAsJsonObject("classifiers");

                    if (classifiers.has(nativeKey)) {

                        JsonObject classifier = classifiers.getAsJsonObject(nativeKey);

                        String url = classifier.get("url").getAsString();
                        String path = classifier.get("path").getAsString();

                        Path libPath = mcDir.resolve("libraries").resolve(path);

                        if (!Files.exists(libPath)) {
                            Files.createDirectories(libPath.getParent());

                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .build();

                            HttpResponse<InputStream> response =
                                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                            Files.copy(response.body(), libPath, StandardCopyOption.REPLACE_EXISTING);

                            System.out.println("🔥 Downloaded native: " + path);
                        }

                    } else {
                        System.out.println("Native classifier fehlt: " + nativeKey);
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

        Files.copy(
                jarResponse.body(),
                profileDir.resolve("client.jar"),
                StandardCopyOption.REPLACE_EXISTING
        );
        downloadLibraries(versionJson, profileDir);
    }

    static void downloadFabric(String version, Path profileDir) throws Exception {

        SSLFix.apply();
        // 1. Vanilla als Basis
        downloadVanilla(version, profileDir);

        // 2. Loader Version holen
        String loaderVersion = getLatestFabricLoader();

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
    }}