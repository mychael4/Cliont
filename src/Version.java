import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Version {

    static JPanel mainPanel;
    static JPanel profileGrid;
    static CardLayout layout;
    private static final Color Cliont = Main.cliont;

    public static JPanel create() throws IOException, FontFormatException {


        layout = new CardLayout();
        mainPanel = new JPanel(layout);

        mainPanel.add(createProfilesPanel(), "profiles");
        mainPanel.add(createCreatePanel(), "create");

        layout.show(mainPanel, "profiles");

        return mainPanel;
    }

    static JPanel createProfileView(Path profilePath) {

        Color bg = UIUtils.BG;

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
        UIUtils.RoundedPanel header = new UIUtils.RoundedPanel(14, UIUtils.LIGHT_BORDER);
        header.setLayout(new BorderLayout());
        header.setBackground(UIUtils.CARD);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ICON
        ImageIcon icon = new ImageIcon(Version.class.getResource("/Icon-rounded.png"));
        JLabel img = new JLabel(new ImageIcon(
                icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH)
        ));

        // NAME
        JLabel name = new JLabel(profilePath.getFileName().toString());
        name.setForeground(UIUtils.TEXT);
        name.setFont(new Font("Arial", Font.BOLD, 18));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.setBackground(header.getBackground());
        left.add(back); // GANZ LINKS vor dem Icon
        left.add(img);
        left.add(name);

        // START BUTTON
        JButton start = new JButton("Start");
        start.setSize(50, 20);
        Timer loginButtonCheckTimer = new Timer(500, e -> {
            if (Main.loginData == null) {
                start.setBackground(UIUtils.BUTTON);
                start.setEnabled(false);
            } else {
                start.setBackground(Cliont);
                start.setEnabled(true);
            }
        });
        loginButtonCheckTimer.start();

        start.addActionListener(e -> {
            try {
                //Main.setAppBusy(true);
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                Process process = MinecraftLauncher.launch(Main.loginData);

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            } catch (Exception ex) {
                //Main.setAppBusy(false);
                ex.printStackTrace();
            }
        });

        header.add(left, BorderLayout.WEST);
        header.add(start, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // =========================
        // 🔍 SEARCH BAR + BUTTON
        // =========================
        UIUtils.RoundedPanel topBar = new UIUtils.RoundedPanel(10, UIUtils.LIGHT_BORDER);
        topBar.setLayout(new BorderLayout());
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

            // 🔥 GROUP MODS BY NAME (remove version suffixes)
            java.util.Map<String, Path> uniqueMods = new java.util.TreeMap<>();

            Files.list(modsDir).forEach(mod -> {
                String fileName = mod.getFileName().toString();
                // Extract mod name by removing version and file extension
                // Common patterns: "modname-1.0.jar", "modname-1.19.2.jar", "modname-fabric.jar"
                String modName = extractModBaseName(fileName);

                // Keep only the latest version of each mod
                // (or first found if we can't determine which is newer)
                if (!uniqueMods.containsKey(modName)) {
                    uniqueMods.put(modName, mod);
                }
            });

            uniqueMods.values().forEach(mod -> {
                try {
                    modsPanel.add(createModCard(mod, modsPanel, modsDir));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
                    // 🔥 GROUP MODS BY NAME (remove version suffixes)
                    java.util.Map<String, Path> uniqueMods = new java.util.TreeMap<>();

                    Files.list(modsDir).forEach(mod -> {
                        String fileName = mod.getFileName().toString();
                        String modName = extractModBaseName(fileName);

                        // Filter by search text on the base name
                        if (modName.toLowerCase().contains(text)) {
                            // Keep only the latest version of each mod
                            if (!uniqueMods.containsKey(modName)) {
                                uniqueMods.put(modName, mod);
                            }
                        }
                    });

                    uniqueMods.values().forEach(mod -> {
                        try {
                            modsPanel.add(createModCard(mod, modsPanel, modsDir));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }

                modsPanel.revalidate();
                modsPanel.repaint();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }
        });

        return root;
    }

    static JPanel createModCard(Path modPath, JPanel modsPanel, Path modsDir) throws IOException {

        UIUtils.RoundedPanel card = new UIUtils.RoundedPanel(10, UIUtils.LIGHT_BORDER);
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(10, 50));
        card.setBackground(UIUtils.CARD);
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JLabel name = new JLabel(modPath.getFileName().toString());
        name.setForeground(UIUtils.TEXT);
        name.setFont(new Font("Arial", Font.PLAIN, 14));

        java.net.URL iconURL = Version.class.getClassLoader().getResource("Icons/Delete-Icon.png");
        ImageIcon deleteIcon = null;

        if (iconURL != null) {
            // Originalbild laden
            BufferedImage original = ImageIO.read(iconURL);

            // Auf 16x16 skalieren
            BufferedImage scaled = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, 16, 16, null);
            g.dispose();

            // Neues Bild mit Farbe basierend auf Theme erzeugen
            Color iconColor = UIUtils.getCurrentTheme() == UIUtils.Theme.LIGHT ? Color.BLACK : Color.WHITE;
            int iconRGB = iconColor.getRGB();
            BufferedImage coloredIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < scaled.getHeight(); y++) {
                for (int x = 0; x < scaled.getWidth(); x++) {
                    int argb = scaled.getRGB(x, y);
                    int alpha = (argb >> 24) & 0xff;

                    if (alpha > 0) {
                        // Icon-Farbe mit ursprünglicher Transparenz
                        coloredIcon.setRGB(x, y, (alpha << 24) | (iconRGB & 0xFFFFFF));
                    }
                }
            }

            deleteIcon = new ImageIcon(coloredIcon);
        }

        JButton DeleteMod = new JButton(deleteIcon);
        DeleteMod.setVisible(true);
        DeleteMod.setOpaque(true);
        DeleteMod.setBorder(null);
        DeleteMod.setPreferredSize(new Dimension(28, 28));
        DeleteMod.setContentAreaFilled(false);
        DeleteMod.setBorderPainted(false);
        DeleteMod.setFocusPainted(false);
        DeleteMod.setOpaque(false);
        DeleteMod.addActionListener(e -> {
            try {
                DeleteMod.setEnabled(false);
                DeleteMod.setVisible(false);
                DeleteMod.setText("Deleting...");

                Files.deleteIfExists(modPath);

                refreshMods(modsPanel, modsDir, null);

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        card.add(name, BorderLayout.WEST);
        card.add(DeleteMod, BorderLayout.EAST);

        return card;
    }

    static void refreshMods(JPanel modsPanel, Path modsDir, String filter) {
        modsPanel.removeAll();

        try {
            java.util.Map<String, Path> uniqueMods = new java.util.TreeMap<>();

            Files.list(modsDir).forEach(mod -> {
                String modName = extractModBaseName(mod.getFileName().toString());

                if (filter == null || modName.toLowerCase().contains(filter.toLowerCase())) {
                    uniqueMods.putIfAbsent(modName, mod);
                }
            });

            uniqueMods.values().forEach(mod -> {
                try {
                    modsPanel.add(createModCard(mod, modsPanel, modsDir));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }

        modsPanel.revalidate();
        modsPanel.repaint();
    }

    static void openProfileView(Path profilePath) {

        JPanel panel = createProfileView(profilePath);

        mainPanel.add(panel, "profile_" + profilePath.getFileName());
        layout.show(mainPanel, "profile_" + profilePath.getFileName());
    }

    // ================================
    // EXTRACT MOD BASE NAME
    // ================================
    static String extractModBaseName(String fileName) {
        // Remove .jar extension
        String name = fileName;
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }

        // Remove version patterns: -1.0, -1.19.2, etc.
        // Common patterns: "modname-1.0", "modname-1.19.2-fabric", "modname-fabric"
        name = name.replaceAll("-\\d+\\.\\d+(\\.\\d+)?.*$", ""); // Remove -version onwards

        // If the name still contains hyphens with numbers, remove them
        // This handles cases like "modname-1.0beta"
        name = name.replaceAll("-.*$", "");

        return name;
    }

    // ================================
    // PROFILE PANEL
    // ================================
    static JPanel createProfilesPanel() throws IOException, FontFormatException {

        InputStream fontStream = Content.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIUtils.BG);

        Color Cliont = new Color(188, 19, 254);

        // TOP BAR
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(UIUtils.BG);

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
                    g2.setColor(UIUtils.BUTTON.darker()); // Dunkler beim Klicken
                } else if (getModel().isRollover()) {
                    g2.setColor(UIUtils.BUTTON.brighter()); // Heller beim Hover
                } else {
                    g2.setColor(UIUtils.BUTTON); // Standardfarbe
                }

                // Zeichne den Kreis
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);

                // Text zeichnen
                g2.setColor(UIUtils.TEXT);
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

        profileGrid = new JPanel(new WrapLayout(WrapLayout.LEFT, 20, 20));
        profileGrid.setBackground(UIUtils.BG);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UIUtils.BG);
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

        UIUtils.RoundedPanel card = new UIUtils.RoundedPanel(16, UIUtils.LIGHT_BORDER);
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(180, 240));
        card.setBackground(UIUtils.CARD);
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 🔥 ICON LADEN & ABRUNDEN
        ImageIcon icon = new ImageIcon(
                Version.class.getResource("/Profiles-Icon.png")
        );

        Image scaled = icon.getImage().getScaledInstance(160, 160, Image.SCALE_SMOOTH);

        // Abgerundetes Icon mit eigener Komponente
        JLabel iconLabel = new JLabel(new ImageIcon(scaled)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getIcon() != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    ImageIcon imgIcon = (ImageIcon) getIcon();
                    int width = imgIcon.getIconWidth();
                    int height = imgIcon.getIconHeight();
                    int radius = 0;

                    BufferedImage buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = buffImg.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Abgerundetes Rechteck
                    g2d.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, width, height, radius, radius));
                    g2d.drawImage(imgIcon.getImage(), 0, 0, null);
                    g2d.dispose();

                    g2.drawImage(buffImg, 0, 0, this);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };

        JLabel name = new JLabel(profilePath.getFileName().toString(), SwingConstants.CENTER);
        name.setForeground(UIUtils.TEXT);

        JButton start = new JButton("Start");
        start.setBackground(Cliont);

        start.addActionListener(e -> {
            try {
                //Main.setAppBusy(true);
                MinecraftLauncher.setVersion(profilePath.getFileName().toString());
                Process process = MinecraftLauncher.launch(Main.loginData);

                new Thread(() -> {
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                    }
                }).start();
            } catch (Exception ex) {
                //Main.setAppBusy(false);
                ex.printStackTrace();
            }
        });

        // keep existing card behavior
        card.add(iconLabel, BorderLayout.NORTH);
        card.add(name, BorderLayout.CENTER);
        card.add(start, BorderLayout.SOUTH);

        // Create layered pane to place a round hover button on top-right of the card
        JLayeredPane layered = new JLayeredPane();
        layered.setPreferredSize(new Dimension(180, 240));

        card.setBounds(0, 0, 180, 240);
        layered.add(card, JLayeredPane.DEFAULT_LAYER);

        int btnSize = 28;

// 1. Bild sicher über den ClassLoader laden
        java.net.URL iconURL = Version.class.getClassLoader().getResource("Icons/Delete-Icon.png");
        ImageIcon deleteIcon = null;

        if (iconURL != null) {
            try {
                ImageIcon originalIcon = new ImageIcon(iconURL);
                BufferedImage original = ImageIO.read(iconURL);
                 
                // Auf 16x16 skalieren
                BufferedImage scaled2 = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled2.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(original, 0, 0, 16, 16, null);
                g.dispose();

                // Icon-Farbe basierend auf Theme
                Color iconColor = UIUtils.getCurrentTheme() == UIUtils.Theme.LIGHT ? Color.BLACK : Color.WHITE;
                int iconRGB = iconColor.getRGB();
                BufferedImage coloredIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

                for (int y = 0; y < scaled2.getHeight(); y++) {
                    for (int x = 0; x < scaled2.getWidth(); x++) {
                        int argb = scaled2.getRGB(x, y);
                        int alpha = (argb >> 24) & 0xff;

                        if (alpha > 0) {
                            coloredIcon.setRGB(x, y, (alpha << 24) | (iconRGB & 0xFFFFFF));
                        }
                    }
                }

                deleteIcon = new ImageIcon(coloredIcon);
            } catch (Exception e) {
                e.printStackTrace();
                deleteIcon = null;
            }
        }

        JButton DeleteBtn = new JButton(deleteIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Roten Kreis-Hintergrund zeichnen
                g2.setColor(new Color(255, 0, 0, 255));
                g2.fillOval(0, 0, getWidth(), getHeight());

                // Weißen Rand zeichnen
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);

                // 2. Das Icon manuell zentriert zeichnen
                if (getIcon() != null) {
                    int iconWidth = getIcon().getIconWidth();
                    int iconHeight = getIcon().getIconHeight();
                    // Mitte berechnen
                    int x = (getWidth() - iconWidth) / 2;
                    int y = (getHeight() - iconHeight) / 2;
                    getIcon().paintIcon(this, g2, x, y);
                }

                g2.dispose();
            }
        };


        DeleteBtn.setPreferredSize(new Dimension(btnSize, btnSize));
        DeleteBtn.setContentAreaFilled(false); // Verhindert das Zeichnen des eckigen Standard-Hintergrunds
        DeleteBtn.setBorderPainted(false);     // Verhindert das Zeichnen des Standard-Randes
        DeleteBtn.setFocusPainted(false);      // Entfernt den hässlichen Fokus-Rahmen bei Klick


        DeleteBtn.setOpaque(false);
        DeleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        DeleteBtn.setVisible(false);

        //Delete Profile Window
        DeleteBtn.addActionListener(e -> {

            JWindow DeleteProfile = new JWindow();
            DeleteProfile.setSize(300, 100);
            DeleteProfile.setAlwaysOnTop(true);
            DeleteProfile.setLocationRelativeTo(null);

            JPanel MainDeletePanel = new JPanel(new BorderLayout());
            MainDeletePanel.setBackground(UIUtils.CARD);

            JPanel DeleteBottomPart = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            DeleteBottomPart.setBackground(UIUtils.CARD);

            JPanel DeletePanel = new JPanel();
            DeletePanel.setBackground(UIUtils.CARD);
            DeletePanel.setForeground(UIUtils.TEXT);

            JButton Delete = new JButton("Delete") {
                //Abgerundete ecken
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = getModel().isPressed() ? new Color(200, 50, 50) : new Color(255, 30, 0);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(getText())) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 4;
                    g2.drawString(getText(), x, y);
                    g2.dispose();
                }
            };
            JButton Cancel = new JButton("Cancel") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = getModel().isPressed() ? Cliont.darker() : Cliont;
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(UIUtils.TEXT);
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(getText())) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 4;
                    g2.drawString(getText(), x, y);
                    g2.dispose();
                }
            };
            Delete.setPreferredSize(new Dimension(70, 70));
            Cancel.setPreferredSize(new Dimension(70, 70));
            Delete.setBorder(null);
            Cancel.setBorder(null);
            Delete.setBackground(new Color(255, 30, 0));
            Cancel.setBackground(Cliont);

            Delete.addActionListener(e2 -> {
                try {
                    Files.walk(profilePath)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    DeleteProfile.dispose();
                    loadProfiles();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            Cancel.addActionListener(e1 -> {
                DeleteProfile.dispose();
            });

            JLabel label = new JLabel("Are you sure you want to delete this profile?");
            label.setForeground(UIUtils.TEXT);


            label.setHorizontalAlignment(SwingConstants.CENTER);
            DeleteBottomPart.add(Cancel);
            DeleteBottomPart.add(Delete);

            MainDeletePanel.add(label, BorderLayout.NORTH);
            MainDeletePanel.add(DeleteBottomPart, BorderLayout.SOUTH);

            DeleteProfile.add(MainDeletePanel);
            DeleteProfile.setVisible(true);
        });

        int btnX = 180 - btnSize - 12; // padding from right
        int btnY = 12; // padding from top
        DeleteBtn.setBounds(btnX, btnY, btnSize, btnSize);
        layered.add(DeleteBtn, JLayeredPane.PALETTE_LAYER);

        // Hover tracking to show/hide the round button
        MouseAdapter hoverTracker = new MouseAdapter() {
            int hoverCount = 0;

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hoverCount++;
                DeleteBtn.setVisible(true);
                layered.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                // small delay to avoid flicker when moving between button and card
                SwingUtilities.invokeLater(() -> {
                    hoverCount = Math.max(0, hoverCount - 1);
                    if (hoverCount == 0) {
                        DeleteBtn.setVisible(false);
                        layered.repaint();
                    }
                });
            }
        };

        // attach to layered, card and button so hover stays when moving between them
        // (attach also to child components because mouse events are delivered to the deepest component)
        layered.addMouseListener(hoverTracker);
        card.addMouseListener(hoverTracker);
        DeleteBtn.addMouseListener(hoverTracker);
        iconLabel.addMouseListener(hoverTracker);
        name.addMouseListener(hoverTracker);
        start.addMouseListener(hoverTracker);

        // Keep original click behavior on the whole card (open profile view).
        // Swing sends mouse events to the deepest child component, so the listener
        // must also be attached to the visible children.
        MouseAdapter openProfileClick = new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                openProfileView(profilePath);
            }
        };
        layered.addMouseListener(openProfileClick);
        card.addMouseListener(openProfileClick);
        iconLabel.addMouseListener(openProfileClick);
        name.addMouseListener(openProfileClick);

        // Wrap layered pane into a JPanel to return (so caller expecting JPanel works)
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setPreferredSize(new Dimension(180, 240));
        wrapper.add(layered, BorderLayout.CENTER);

        return wrapper;
    }

    // ================================
    // CREATE PROFILE PANEL
    // ================================
    static JPanel createCreatePanel() throws IOException, FontFormatException {

        InputStream fontStream = Main.class.getResourceAsStream("/Minecraft.ttf");
        Font mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(24f);

        Color Cliont = new Color(188, 19, 254);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIUtils.BG);

        // ===== TOP BAR =====
        UIUtils.RoundedPanel top = new UIUtils.RoundedPanel(12, UIUtils.LIGHT_BORDER);
        top.setLayout(new BorderLayout());
        top.setBackground(UIUtils.BG);
        top.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("Create Profile");
        title.setForeground(Cliont);
        title.setFont(mcFont);

        JButton close = new JButton("✕") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? new Color(200, 50, 50) : new Color(150, 40, 40);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setFont(mcFont.deriveFont(20f));
        close.setPreferredSize(new Dimension(40, 40));
        close.addActionListener(e -> layout.show(mainPanel, "profiles"));

        top.add(title, BorderLayout.WEST);
        top.add(close, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);

        // ===== CENTER PANEL =====
        UIUtils.RoundedPanel panel = new UIUtils.RoundedPanel(14, UIUtils.LIGHT_BORDER);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIUtils.CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        panel.add(Box.createVerticalStrut(10));

        // Profile Name
        JLabel nameLabel = new JLabel("Profile Name");
        nameLabel.setForeground(UIUtils.CLIONT);
        nameLabel.setFont(mcFont.deriveFont(16f));

        JTextField nameField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        nameField.setMaximumSize(new Dimension(300, 32));
        nameField.setPreferredSize(new Dimension(300, 32));
        nameField.setBackground(UIUtils.INPUT);
        nameField.setForeground(UIUtils.TEXT);
        nameField.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        nameField.setFont(new Font("Arial", Font.PLAIN, 14));

        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(20));

        // Minecraft Version
        JLabel versionLabel = new JLabel("Minecraft Version");
        versionLabel.setForeground(UIUtils.CLIONT);
        versionLabel.setFont(mcFont.deriveFont(16f));

        JComboBox<String> versionBox = new JComboBox<String>() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        versionBox.setMaximumSize(new Dimension(300, 32));
        versionBox.setPreferredSize(new Dimension(300, 32));
        versionBox.setBackground(UIUtils.INPUT);
        versionBox.setForeground(UIUtils.TEXT);
        versionBox.setFont(new Font("Arial", Font.PLAIN, 14));

        panel.add(versionLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(versionBox);
        panel.add(Box.createVerticalStrut(20));

        // Loader
        JLabel loaderLabel = new JLabel("Loader");
        loaderLabel.setForeground(UIUtils.CLIONT);
        loaderLabel.setFont(mcFont.deriveFont(16f));

        JComboBox<String> loaderBox = new JComboBox<String>() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        loaderBox.setMaximumSize(new Dimension(300, 32));
        loaderBox.setPreferredSize(new Dimension(300, 32));
        loaderBox.setBackground(UIUtils.INPUT);
        loaderBox.setForeground(UIUtils.TEXT);
        loaderBox.setFont(new Font("Arial", Font.PLAIN, 14));

        loaderBox.addItem("Vanilla");
        loaderBox.addItem("Fabric");
        loaderBox.addItem("Forge");
        loaderBox.addItem("NeoForge");

        panel.add(loaderLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(loaderBox);
        panel.add(Box.createVerticalStrut(30));

        // Create Button
        JButton create = new JButton("Create Profile") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? Cliont.darker() : Cliont;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(UIUtils.TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        create.setMaximumSize(new Dimension(300, 40));
        create.setPreferredSize(new Dimension(300, 40));
        create.setContentAreaFilled(false);
        create.setBorderPainted(false);
        create.setFocusPainted(false);
        create.setFont(mcFont.deriveFont(14f));
        create.setForeground(UIUtils.TEXT);

        panel.add(create);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(UIUtils.BG);
        root.add(scrollPane, BorderLayout.CENTER);

        // ===== VERSIONEN LADEN =====
        new Thread(() -> {

            try {

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

                    // Show installation progress window
                    InstallationProgressWindow progressWindow = new InstallationProgressWindow(name);
                    progressWindow.showWindow();

                    //Aktuell nur Vanilla und Fabric
                    if (loader.equals("Vanilla")) {
                        downloadVanilla(version, profileDir, progressWindow);
                        System.out.println("Downloading Vanilla");
                    } else if (loader.equals("Fabric")) {
                        downloadFabric(version, profileDir, progressWindow);
                        System.out.println("Downloading Fabric");
                    }

                    // Complete the progress window
                    progressWindow.complete();

                    // Show completion window
                    CompletionWindow completionWindow = new CompletionWindow(name);
                    completionWindow.showWindow();

                    // Aktualisiere die Profil-Dropdown-Liste
                    Content.refreshProfileList();

                    // 🔥 UI Update auf EDT!
                    SwingUtilities.invokeLater(() -> {
                        layout.show(mainPanel, "profiles");
                        loadProfiles();
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
        downloadLibraries(versionJson, mcDir, null);
    }

    static void downloadLibraries(JsonObject versionJson, Path mcDir, InstallationProgressWindow progressWindow) throws Exception {

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

        int count = 0;
        int total = libs.size();

        for (JsonElement libElement : libs) {

            JsonObject lib = libElement.getAsJsonObject();

            if (!MinecraftLauncher.isLibraryAllowed(lib)) {
                if (lib.has("name")) {
                    System.out.println("Skipping disallowed library: " + lib.get("name").getAsString());
                }
                count++;
                if (progressWindow != null) {
                    progressWindow.setProgress(0.35f + (0.25f * count / total));
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
                count++;
                if (progressWindow != null) {
                    progressWindow.setProgress(0.35f + (0.25f * count / total));
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

            count++;
            if (progressWindow != null) {
                progressWindow.setProgress(0.35f + (0.25f * count / total));
            }
        }
    }

    // ================================
    // DOWNLOAD VANILLA
    // ================================
    static void downloadVanilla(String version, Path profileDir, InstallationProgressWindow progressWindow) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .build();

        HttpRequest manifestRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                .build();

        System.out.println("Downloading version metadata for " + version);
        progressWindow.setProgress(0.05f);

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
        progressWindow.setProgress(0.10f);

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
        progressWindow.setProgress(0.20f);

        Files.copy(
                jarResponse.body(),
                profileDir.resolve("client.jar"),
                StandardCopyOption.REPLACE_EXISTING
        );
        progressWindow.setProgress(0.35f);

        downloadLibraries(versionJson, profileDir, progressWindow);
        progressWindow.setProgress(0.60f);

        getJavaRuntime(versionJson);
        progressWindow.setProgress(0.75f);

        downloadAssets(versionJson, profileDir);
        progressWindow.setProgress(0.85f);

        downloadLogging(versionJson, profileDir);

        Path nativesDir = profileDir.resolve("natives");
        extractAllNatives(profileDir.resolve("libraries"), nativesDir);
        progressWindow.setProgress(0.95f);
    }

    static void downloadFabric(String version, Path profileDir, InstallationProgressWindow progressWindow) throws Exception {

        SSLFix.apply();
        // 1. Vanilla als Basis
        downloadVanilla(version, profileDir, progressWindow);

        // 2. Loader Version holen
        String loaderVersion = getLatestFabricLoader();

        System.out.println("Downloading Fabric loader: " + loaderVersion);
        progressWindow.setProgress(0.60f);

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
        progressWindow.setProgress(0.70f);

        FabricApiInstaller.installFabricApi(version, profileDir);
        progressWindow.setProgress(0.80f);

        // speichern
        try (FileWriter writer = new FileWriter(
                profileDir.resolve("fabric.json").toFile())) {

            new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(fabricJson, writer);
        }

        // Libraries laden (Fabric + zusätzliche)
        downloadLibraries(fabricJson, profileDir, progressWindow);
        progressWindow.setProgress(0.90f);
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
        Path javaExe = findJavaExecutable(runtimeDir);

        if (javaExe != null && isJavaRuntimeCompatible(javaExe, javaVersion)) {
            return javaExe;
        }

        if (javaExe != null) {
            System.out.println("Installed Java runtime is not compatible, downloading Java " + javaVersion + "...");
        }

        String url = "https://api.adoptium.net/v3/binary/latest/" + javaVersion + "/ga/windows/x64/jre/hotspot/normal/eclipse";

        Files.createDirectories(runtimeDir);
        clearDirectory(runtimeDir);

        Path zip = runtimeDir.resolve("java.zip");

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Downloading Java " + javaVersion + " runtime...");

        MinecraftLauncher.unzip(zip, runtimeDir);
        Files.delete(zip);

        javaExe = findJavaExecutable(runtimeDir);
        if (javaExe != null && isJavaRuntimeCompatible(javaExe, javaVersion)) {
            return javaExe;
        }

        throw new RuntimeException("Java runtime not found after extraction");
    }

    private static Path findJavaExecutable(Path runtimeDir) throws IOException {
        Path javaExe = runtimeDir.resolve("bin").resolve("java.exe");
        if (Files.exists(javaExe)) {
            return javaExe;
        }

        if (!Files.exists(runtimeDir)) {
            return null;
        }

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(runtimeDir)) {
            for (Path dir : dirs) {
                javaExe = dir.resolve("bin").resolve("java.exe");
                if (Files.exists(javaExe)) {
                    return javaExe;
                }
            }
        }

        return null;
    }

    private static boolean isJavaRuntimeCompatible(Path javaExe, int requiredVersion) {
        try {
            Process process = new ProcessBuilder(javaExe.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes());
            }

            process.waitFor();
            int installedVersion = parseJavaMajorVersion(output);

            return installedVersion >= requiredVersion;
        } catch (Exception e) {
            return false;
        }
    }

    private static int parseJavaMajorVersion(String versionOutput) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("version \"([^\"]+)\"")
                .matcher(versionOutput);

        if (!matcher.find()) {
            return -1;
        }

        String version = matcher.group(1);
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }

        int dotIndex = version.indexOf('.');
        String major = dotIndex == -1 ? version : version.substring(0, dotIndex);
        return Integer.parseInt(major);
    }

    private static void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // Moved Version.ProfilePanel from separate file into Version.java to simplify project layout.
    static class ProfilePanel extends JPanel {

        private static final int AVATAR_SIZE = 40;
        private static final int BUTTON_HEIGHT = 50;
        private static final int DROPDOWN_HEIGHT = BUTTON_HEIGHT;
        private static final int ANIMATION_DURATION = 100; // ms
        private final JPanel profileButton;
        private final JPanel dropdownPanel;
        private final int hoverBtnSize = 16;
        private final Runnable onLogout;
        private String username;
        private String uuid;
        private BufferedImage skinHead;
        private boolean isExpanded = false;
        private JButton logoutButton;
        private JButton hoverCircleButton;
        private int hoverCount = 0;
        private Font minecraftFont;
        private int currentDropdownHeight = 0;
        private int targetDropdownHeight = 0;
        private Timer animationTimer;

        public ProfilePanel(String username, String uuid, Runnable onLogout) {
            this.username = username;
            this.uuid = uuid;
            this.onLogout = onLogout;

            // Lade Minecraft-Schrift
            loadMinecraftFont();

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(UIUtils.SIDEBAR);
            setPreferredSize(new Dimension(150, BUTTON_HEIGHT));

            // Lade Spielerkopf
            loadSkinHead();

            // Profile Button erstellen
            profileButton = createProfileButton();
            add(profileButton);

            // Dropdown Panel erstellen
            dropdownPanel = createDropdownPanel();
            dropdownPanel.setVisible(false);
            add(dropdownPanel);
        }

        private void loadMinecraftFont() {
            try {
                InputStream fontStream = ProfilePanel.class.getResourceAsStream("/Minecraft.ttf");
                if (fontStream != null) {
                    minecraftFont = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(18f);
                } else {
                    minecraftFont = new Font("Arial", Font.PLAIN, 18);
                }
            } catch (Exception e) {
                System.err.println("Fehler beim Laden der Minecraft-Schrift: " + e.getMessage());
                minecraftFont = new Font("Arial", Font.PLAIN, 18);
            }
        }

        private void loadSkinHead() {
            new Thread(() -> {
                try {
                    // Verwende MC-Heads API um den Spielerkopf zu bekommen
                    String headUrl = "https://mc-heads.net/avatar/" + uuid + "/32";
                    URL url = new URL(headUrl);
                    skinHead = ImageIO.read(url);
                    SwingUtilities.invokeLater(() -> profileButton.repaint());
                } catch (Exception e) {
                    System.err.println("Fehler beim Laden des Spielerkopfes: " + e.getMessage());
                }
            }).start();
        }

        private JPanel createProfileButton() {
            JPanel button = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int padding = 10;

                    // Zeichne Hintergrund
                    g2.setColor(UIUtils.CARD);
                    g2.fillRoundRect(padding, 5, getWidth() - 2 * padding, AVATAR_SIZE, 10, 10);

                    // Zeichne Spielerkopf
                    if (skinHead != null) {
                        int x = padding + 5;
                        int y = 10;

                        // Schneide den Kopf in einem Kreis aus
                        BufferedImage roundedHead = new BufferedImage(AVATAR_SIZE - 10, AVATAR_SIZE - 10, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D headG2 = roundedHead.createGraphics();
                        headG2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        headG2.setClip(new RoundRectangle2D.Float(0, 0, AVATAR_SIZE - 10, AVATAR_SIZE - 10, 5, 5));
                        headG2.drawImage(skinHead, 0, 0, AVATAR_SIZE - 10, AVATAR_SIZE - 10, null);
                        headG2.dispose();

                        g2.drawImage(roundedHead, x, y, null);
                    } else {
                        // Placeholder wenn Bild noch nicht geladen
                        g2.setColor(UIUtils.INPUT);
                        g2.fillRoundRect(padding + 5, 10, AVATAR_SIZE - 10, AVATAR_SIZE - 10, 5, 5);
                    }

                    // Zeichne Username mit Minecraft-Schrift
                    g2.setColor(UIUtils.TEXT);
                    g2.setFont(minecraftFont);
                    String displayUsername = username.length() > 10 ? username.substring(0, 10) + "..." : username;
                    FontMetrics fm = g2.getFontMetrics();
                    int textX = padding + AVATAR_SIZE + 5;
                    int textY = BUTTON_HEIGHT / 2 + fm.getAscent() / 2 - 2;
                    g2.drawString(displayUsername, textX, textY);

                    // Zeichne Dropdown Pfeil
                    g2.setColor(UIUtils.TEXT_MUTED);
                    int arrowX = getWidth() - padding - 8;
                    int arrowY = BUTTON_HEIGHT / 2 - 4;
                    if (isExpanded) {
                        g2.fillPolygon(
                                new int[]{arrowX - 3, arrowX + 3, arrowX},
                                new int[]{arrowY + 3, arrowY + 3, arrowY},
                                3
                        );
                    } else {
                        g2.fillPolygon(
                                new int[]{arrowX - 3, arrowX + 3, arrowX},
                                new int[]{arrowY, arrowY, arrowY + 3},
                                3
                        );
                    }

                    g2.dispose();
                }
            };

            // create the round hover button (does nothing yet)
            hoverCircleButton = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // semi-transparent dark circle with white border
                    g2.setColor(new Color(0, 0, 0, 170));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
                    g2.dispose();
                }
            };

            hoverCircleButton.setOpaque(false);
            hoverCircleButton.setContentAreaFilled(false);
            hoverCircleButton.setBorderPainted(false);
            hoverCircleButton.setFocusPainted(false);
            hoverCircleButton.setVisible(false);
            hoverCircleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // no action yet
            hoverCircleButton.addActionListener(e -> {
                // placeholder: no action
            });

            // add to button (null layout) and position later on resize
            button.add(hoverCircleButton);
            button.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    positionHoverButton(button);
                }

                @Override
                public void componentShown(ComponentEvent e) {
                    positionHoverButton(button);
                }
            });

            // keep hover visible while mouse is over either the profile button or the circle
            MouseAdapter hoverTracker = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hoverCount++;
                    hoverCircleButton.setVisible(true);
                    button.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // decrement after short delay to avoid flicker when moving between components
                    SwingUtilities.invokeLater(() -> {
                        hoverCount = Math.max(0, hoverCount - 1);
                        if (hoverCount == 0) {
                            hoverCircleButton.setVisible(false);
                            button.repaint();
                        }
                    });
                }
            };

            button.addMouseListener(hoverTracker);
            hoverCircleButton.addMouseListener(hoverTracker);

            button.setLayout(null);
            button.setBackground(UIUtils.SIDEBAR);
            button.setPreferredSize(new Dimension(150, BUTTON_HEIGHT));
            button.setMaximumSize(new Dimension(150, BUTTON_HEIGHT));
            button.setMinimumSize(new Dimension(150, BUTTON_HEIGHT));
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleDropdown();
                }
            });

            return button;
        }

        // Position the small circular hover button so it sits at the top-right of the avatar image
        private void positionHoverButton(JPanel button) {
            if (hoverCircleButton == null) return;
            int padding = 10;
            int avatarX = padding + 5;
            int avatarY = 10;
            int avatarW = AVATAR_SIZE - 10;
            int btnSize = hoverBtnSize;
            int btnX = avatarX + avatarW - btnSize / 2;
            int btnY = avatarY - btnSize / 2;
            // ensure values are non-negative
            btnX = Math.max(0, btnX);
            btnY = Math.max(0, btnY);
            hoverCircleButton.setBounds(btnX, btnY, btnSize, btnSize);
        }

        private JPanel createDropdownPanel() {
            JPanel dropdown = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int padding = 10;

                    // Zeichne Hintergrund
                    g2.setColor(UIUtils.CARD);
                    g2.fillRoundRect(padding, 2, getWidth() - 2 * padding, getHeight() - 4, 10, 0);

                    g2.dispose();
                }
            };

            dropdown.setLayout(null);
            dropdown.setBackground(UIUtils.SIDEBAR);
            dropdown.setMaximumSize(new Dimension(100, DROPDOWN_HEIGHT));
            dropdown.setPreferredSize(new Dimension(100, DROPDOWN_HEIGHT));

            // Logout button will be same width/height as the profile button and flush (no gap)
            logoutButton = new JButton("Logout") {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Zeichne Hintergrund
                    Color bgColor = getModel().isPressed() ? UIUtils.BUTTON.darker() : (getModel().isRollover() ? UIUtils.BUTTON.brighter() : UIUtils.BUTTON);
                    g2.setColor(bgColor);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                    // Zeichne Text
                    g2.setColor(getForeground());
                    g2.setFont(minecraftFont);
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(getText())) / 2;
                    int y = (getHeight() + fm.getAscent()) / 2 - 2;
                    g2.drawString(getText(), x, y);

                    g2.dispose();
                }
            };

            // place directly adjacent to the profile button, full width, no spacing
            logoutButton.setBounds(0, 0, 100, DROPDOWN_HEIGHT);
            logoutButton.setFocusPainted(false);
            logoutButton.setBorderPainted(false);
            logoutButton.setContentAreaFilled(false);
            logoutButton.setForeground(UIUtils.TEXT);

            logoutButton.addActionListener(e -> {
                if (onLogout != null) {
                    onLogout.run();
                }
            });

            dropdown.add(logoutButton);

            return dropdown;
        }

        private void toggleDropdown() {
            isExpanded = !isExpanded;
            if (isExpanded) {
                showDropdownAnimated();
            } else {
                hideDropdownAnimated();
            }
        }

        private void showDropdownAnimated() {
            dropdownPanel.setVisible(true);
            targetDropdownHeight = DROPDOWN_HEIGHT;
            startAnimation();
        }

        private void hideDropdownAnimated() {
            targetDropdownHeight = 0;
            startAnimation();
        }

        private void startAnimation() {
            if (animationTimer != null) {
                animationTimer.stop();
            }

            long startTime = System.currentTimeMillis();

            animationTimer = new Timer(16, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min(1.0f, (float) elapsed / ANIMATION_DURATION);

                if (isExpanded) {
                    currentDropdownHeight = (int) (targetDropdownHeight * progress);
                } else {
                    currentDropdownHeight = (int) (DROPDOWN_HEIGHT * (1.0f - progress));
                }

                // resize this panel and the dropdown so the logout button slides open/closed
                setPreferredSize(new Dimension(150, BUTTON_HEIGHT + currentDropdownHeight));
                setMaximumSize(new Dimension(150, BUTTON_HEIGHT + currentDropdownHeight));

                // adjust dropdown preferred size and position of logout button to be flush
                dropdownPanel.setPreferredSize(new Dimension(130, Math.max(0, currentDropdownHeight)));
                dropdownPanel.setMaximumSize(new Dimension(130, Math.max(0, currentDropdownHeight)));
                logoutButton.setBounds(0, 0, 130, Math.max(0, currentDropdownHeight));

                Container parent = getParent();
                if (parent != null) {
                    parent.revalidate();
                }
                dropdownPanel.revalidate();
                repaint();

                if (progress >= 1.0f) {
                    ((Timer) e.getSource()).stop();
                    if (!isExpanded) {
                        dropdownPanel.setVisible(false);
                        currentDropdownHeight = 0;
                    }
                    profileButton.repaint();
                }
            });

            animationTimer.start();
        }

        public void updateProfile(String newUsername, String newUuid) {
            this.username = newUsername;
            this.uuid = newUuid;
            this.skinHead = null;
            isExpanded = false;
            currentDropdownHeight = 0;
            targetDropdownHeight = 0;
            dropdownPanel.setVisible(false);
            setPreferredSize(new Dimension(150, BUTTON_HEIGHT));
            setMaximumSize(new Dimension(150, BUTTON_HEIGHT));
            loadSkinHead();
            profileButton.repaint();
        }

    }
}

