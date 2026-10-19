import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

/**
 * Account panel for the Cliont desktop launcher.
 * Handles Cliont website login (email + password), skin preview, and custom cape management (upload, equip, disable).
 * Shows capes from both the server API and the local .cliont/capes/ directory.
 */
public class Account {

    private static final Color CLIONT_PURPLE = new Color(188, 19, 254);
    private static final String API_BASE = "http://localhost:8000";
    private static final Path CAPES_DIR = Paths.get(System.getenv("APPDATA"), ".cliont", "capes");
    private static final Path LOGIN_DATA_FILE = Paths.get(System.getenv("APPDATA"), ".cliont", "login.json");

    private final JPanel root;
    private final JLabel usernameLabel;
    private final JLabel emailLabel;
    private final JLabel skinImageLabel;
    private final JPanel capeGrid;
    private final JLabel emptyLabel;
    private final JPanel loginForm;
    private final JPanel loggedInView;

    private String cliontEmail;
    private String cliontUsername;
    private JTextField emailInput;
    private JPasswordField passwordInput;
    private JCheckBox rememberMeCheckbox;
    private JButton loginBtn;
    private JButton logoutBtn;

    public Account() {
        root = new JPanel(new BorderLayout(20, 20));
        root.setBorder(new EmptyBorder(30, 40, 30, 40));
        root.setBackground(UIUtils.Theme.LIGHT.bg);

        // =====================================================================
        // LOGIN FORM (shown when not logged in)
        // =====================================================================
        loginForm = new JPanel();
        loginForm.setLayout(new BoxLayout(loginForm, BoxLayout.Y_AXIS));
        loginForm.setOpaque(false);
        loginForm.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginForm.setAlignmentY(Component.CENTER_ALIGNMENT);
        loginForm.setMaximumSize(new Dimension(400, 300));

        JLabel loginTitle = new JLabel("Cliont Account Login");
        loginTitle.setFont(new Font("Arial", Font.BOLD, 24));
        loginTitle.setForeground(Color.DARK_GRAY);
        loginTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel loginSub = new JLabel("Sign in with your Cliont website account to manage capes");
        loginSub.setFont(new Font("Arial", Font.PLAIN, 13));
        loginSub.setForeground(Color.GRAY);
        loginSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel emailFieldPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        emailFieldPanel.setOpaque(false);
        emailFieldPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel emailLabelField = new JLabel("Email:");
        emailLabelField.setFont(new Font("Arial", Font.PLAIN, 13));
        emailInput = new JTextField(25);
        emailInput.setFont(new Font("Arial", Font.PLAIN, 13));
        emailInput.setMaximumSize(new Dimension(300, 30));
        emailFieldPanel.add(emailLabelField);
        emailFieldPanel.add(Box.createHorizontalStrut(8));
        emailFieldPanel.add(emailInput);

        JPanel passFieldPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        passFieldPanel.setOpaque(false);
        passFieldPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel passLabelField = new JLabel("Password:");
        passLabelField.setFont(new Font("Arial", Font.PLAIN, 13));
        passwordInput = new JPasswordField(25);
        passwordInput.setFont(new Font("Arial", Font.PLAIN, 13));
        passwordInput.setMaximumSize(new Dimension(300, 30));
        passFieldPanel.add(passLabelField);
        passFieldPanel.add(Box.createHorizontalStrut(8));
        passFieldPanel.add(passwordInput);

        JPanel rememberPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        rememberPanel.setOpaque(false);
        rememberPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rememberMeCheckbox = new JCheckBox("Remember me");
        rememberMeCheckbox.setFont(new Font("Arial", Font.PLAIN, 12));
        rememberMeCheckbox.setForeground(Color.DARK_GRAY);
        rememberMeCheckbox.setOpaque(false);
        rememberMeCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
        rememberPanel.add(rememberMeCheckbox);

        loginBtn = new JButton("Login");
        loginBtn.setFont(new Font("Arial", Font.BOLD, 14));
        loginBtn.setBackground(CLIONT_PURPLE);
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorderPainted(false);
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.setMaximumSize(new Dimension(120, 36));
        loginBtn.addActionListener(e -> doLogin());

        JLabel registerHint = new JLabel("Don't have an account? Register on the Cliont website first.");
        registerHint.setFont(new Font("Arial", Font.PLAIN, 11));
        registerHint.setForeground(Color.GRAY);
        registerHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        loginForm.add(Box.createVerticalGlue());
        loginForm.add(loginTitle);
        loginForm.add(Box.createVerticalStrut(8));
        loginForm.add(loginSub);
        loginForm.add(Box.createVerticalStrut(24));
        loginForm.add(emailFieldPanel);
        loginForm.add(Box.createVerticalStrut(10));
        loginForm.add(passFieldPanel);
        loginForm.add(rememberPanel);
        loginForm.add(Box.createVerticalStrut(18));
        loginForm.add(loginBtn);
        loginForm.add(Box.createVerticalStrut(12));
        loginForm.add(registerHint);
        loginForm.add(Box.createVerticalGlue());

        // =====================================================================
        // LOGGED-IN VIEW (shown after successful login)
        // =====================================================================
        loggedInView = new JPanel(new BorderLayout(20, 20));
        loggedInView.setOpaque(false);
        loggedInView.setVisible(false);

        // Header with user info
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        usernameLabel = new JLabel("Player");
        usernameLabel.setFont(new Font("Arial", Font.BOLD, 24));
        usernameLabel.setForeground(Color.DARK_GRAY);

        emailLabel = new JLabel("Not signed in");
        emailLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        emailLabel.setForeground(Color.GRAY);

        infoPanel.add(usernameLabel);
        infoPanel.add(Box.createVerticalStrut(6));
        infoPanel.add(emailLabel);

        header.add(infoPanel, BorderLayout.WEST);

        // Logout button
        logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        logoutBtn.setBackground(Color.LIGHT_GRAY);
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setBorderPainted(false);
        logoutBtn.addActionListener(e -> doLogout());
        header.add(logoutBtn, BorderLayout.EAST);

        loggedInView.add(header, BorderLayout.NORTH);

        // Content split: skin + cape section
        JPanel content = new JPanel(new BorderLayout(20, 0));
        content.setOpaque(false);

        // Left: Skin preview
        JPanel skinPanel = new JPanel(new BorderLayout());
        skinPanel.setOpaque(false);
        skinPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY), "Skin",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14),
                Color.DARK_GRAY));

        skinImageLabel = new JLabel();
        skinImageLabel.setPreferredSize(new Dimension(120, 120));
        skinImageLabel.setMinimumSize(new Dimension(120, 120));
        skinImageLabel.setMaximumSize(new Dimension(120, 120));
        skinImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        skinImageLabel.setVerticalAlignment(SwingConstants.CENTER);
        skinImageLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        skinImageLabel.setBackground(Color.WHITE);
        skinImageLabel.setOpaque(true);
        skinImageLabel.setText("No skin");

        JPanel skinWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        skinWrapper.setOpaque(false);
        skinWrapper.add(skinImageLabel);

        skinPanel.add(skinWrapper, BorderLayout.CENTER);

        // Right: Cape management
        JPanel capePanel = new JPanel(new BorderLayout(10, 10));
        capePanel.setOpaque(false);
        capePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY), "Custom Capes",
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14),
                Color.DARK_GRAY));

        // Upload box
        JPanel uploadBox = new JPanel();
        uploadBox.setLayout(new BoxLayout(uploadBox, BoxLayout.Y_AXIS));
        uploadBox.setOpaque(false);
        uploadBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
                new EmptyBorder(20, 20, 20, 20)));

        JLabel uploadIcon = new JLabel("+");
        uploadIcon.setFont(new Font("Arial", Font.BOLD, 32));
        uploadIcon.setForeground(CLIONT_PURPLE);
        uploadIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel uploadTitle = new JLabel("Upload Cape");
        uploadTitle.setFont(new Font("Arial", Font.BOLD, 14));
        uploadTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel uploadSub = new JLabel("PNG or APNG (64×32 or 128×64)");
        uploadSub.setFont(new Font("Arial", Font.PLAIN, 11));
        uploadSub.setForeground(Color.GRAY);
        uploadSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton uploadBtn = new JButton("Choose File");
        uploadBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        uploadBtn.setBackground(CLIONT_PURPLE);
        uploadBtn.setForeground(Color.WHITE);
        uploadBtn.setFocusPainted(false);
        uploadBtn.setBorderPainted(false);
        uploadBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        uploadBtn.addActionListener(e -> chooseAndUploadCape());

        uploadBox.add(uploadIcon);
        uploadBox.add(Box.createVerticalStrut(8));
        uploadBox.add(uploadTitle);
        uploadBox.add(Box.createVerticalStrut(4));
        uploadBox.add(uploadSub);
        uploadBox.add(Box.createVerticalStrut(16));
        uploadBox.add(uploadBtn);

        capePanel.add(uploadBox, BorderLayout.NORTH);

        // Cape grid
        capeGrid = new JPanel();
        capeGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        capeGrid.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(capeGrid);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        emptyLabel = new JLabel("No capes yet. Upload your first custom cape to equip it in-game.");
        emptyLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel emptyWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        emptyWrapper.setOpaque(false);
        emptyWrapper.add(emptyLabel);

        capePanel.add(scrollPane, BorderLayout.CENTER);
        capePanel.add(emptyWrapper, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, skinPanel, capePanel);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerSize(2);
        splitPane.setDividerLocation(350);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);

        content.add(splitPane, BorderLayout.CENTER);

        loggedInView.add(content, BorderLayout.CENTER);

        // =====================================================================
        // ROOT LAYOUT
        // =====================================================================
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(loginForm);
        centerWrapper.add(loggedInView);

        root.add(centerWrapper, BorderLayout.CENTER);

        loadSavedLogin();
    }

    public JPanel getPanel() {
        return root;
    }

    /* =========================================================================
       LOGIN / LOGOUT
       ========================================================================= */

    private void doLogin() {
        String email = emailInput.getText().trim();
        char[] pw = passwordInput.getPassword();
        String password = new String(pw);
        Arrays.fill(pw, ' ');

        if (email.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Please enter email and password.", "Missing fields",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginBtn.setEnabled(false);
        loginBtn.setText("Logging in...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    URL url = new URL(API_BASE + "/api/login");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    JsonObject body = new JsonObject();
                    body.addProperty("email", email);
                    body.addProperty("password", password);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes("UTF-8"));
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            JsonObject data = JsonParser.parseString(sb.toString()).getAsJsonObject();
                            if (data.has("user")) {
                                JsonObject user = data.getAsJsonObject("user");
                                cliontEmail = user.get("email").getAsString();
                                cliontUsername = user.has("username") && !user.get("username").isJsonNull()
                                        ? user.get("username").getAsString()
                                        : cliontEmail.split("@")[0];

                                saveLoginData(email, password, rememberMeCheckbox.isSelected());

                                SwingUtilities.invokeLater(() -> {
                                    loginForm.setVisible(false);
                                    loggedInView.setVisible(true);
                                    usernameLabel.setText(cliontUsername);
                                    emailLabel.setText("Logged in as: " + cliontEmail);
                                    emailLabel.setForeground(new Color(26, 170, 92));
                                    loadSkin(cliontUsername);
                                    loadCapes(cliontEmail);
                                });
                                return null;
                            }
                        }
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(root, "Unexpected server response.", "Login failed",
                                        JOptionPane.ERROR_MESSAGE));
                    } else if (code == 401) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(root, "Incorrect email or password.", "Login failed",
                                        JOptionPane.ERROR_MESSAGE));
                    } else {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(root, "Server error: " + code, "Login failed",
                                        JOptionPane.ERROR_MESSAGE));
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(root, "Failed to connect: " + ex.getMessage(), "Login failed",
                                    JOptionPane.ERROR_MESSAGE));
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Login");
                    });
                }
                return null;
            }
        };
        worker.execute();
    }

    private void doLogout() {
        cliontEmail = null;
        cliontUsername = null;
        loggedInView.setVisible(false);
        loginForm.setVisible(true);
        emailInput.setText("");
        passwordInput.setText("");
        rememberMeCheckbox.setSelected(false);
        usernameLabel.setText("Player");
        emailLabel.setText("Not signed in");
        emailLabel.setForeground(Color.GRAY);
        skinImageLabel.setIcon(null);
        skinImageLabel.setText("No skin");
        capeGrid.removeAll();
        emptyLabel.setText("No capes yet. Upload your first custom cape to equip it in-game.");
        emptyLabel.setVisible(true);
        capeGrid.setVisible(false);
        capeGrid.revalidate();
        capeGrid.repaint();
    }

    /* =========================================================================
       LOGIN PERSISTENCE
       ========================================================================= */

    private void loadSavedLogin() {
        try {
            if (Files.exists(LOGIN_DATA_FILE)) {
                String content = Files.readString(LOGIN_DATA_FILE);
                JsonObject data = JsonParser.parseString(content).getAsJsonObject();
                String savedEmail = data.has("email") ? data.get("email").getAsString() : "";
                String savedPassword = data.has("password") ? data.get("password").getAsString() : "";
                boolean remember = data.has("remember") && data.get("remember").getAsBoolean();

                if (!savedEmail.isEmpty()) {
                    emailInput.setText(savedEmail);
                }
                if (remember && !savedPassword.isEmpty()) {
                    passwordInput.setText(savedPassword);
                    rememberMeCheckbox.setSelected(true);
                }
            }
        } catch (Exception e) {
            System.err.println("[Cliont Account] Failed to load saved login: " + e.getMessage());
        }
    }

    private void saveLoginData(String email, String password, boolean rememberMe) {
        try {
            Files.createDirectories(LOGIN_DATA_FILE.getParent());
            JsonObject data = new JsonObject();
            data.addProperty("email", email);
            if (rememberMe) {
                data.addProperty("password", password);
            }
            data.addProperty("remember", rememberMe);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(LOGIN_DATA_FILE, gson.toJson(data));
        } catch (Exception e) {
            System.err.println("[Cliont Account] Failed to save login data: " + e.getMessage());
        }
    }

    /* =========================================================================
       SKIN
       ========================================================================= */

    private void loadSkin(String username) {
        if (username == null || username.isEmpty()) return;
        SwingWorker<Image, Void> worker = new SwingWorker<Image, Void>() {
            @Override
            protected Image doInBackground() throws Exception {
                String skinUrl = "https://crafatar.com/avatars/" + username + "?size=120&overlay";
                Image img = Toolkit.getDefaultToolkit().getImage(new URL(skinUrl));
                MediaTracker tracker = new MediaTracker(skinImageLabel);
                tracker.addImage(img, 0);
                tracker.waitForAll();
                return img.getScaledInstance(120, 120, Image.SCALE_SMOOTH);
            }

            @Override
            protected void done() {
                try {
                    Image img = get();
                    if (img != null) {
                        skinImageLabel.setIcon(new ImageIcon(img));
                        skinImageLabel.setText("");
                    } else {
                        skinImageLabel.setIcon(null);
                        skinImageLabel.setText("No skin");
                    }
                } catch (Exception e) {
                    skinImageLabel.setIcon(null);
                    skinImageLabel.setText("No skin");
                }
            }
        };
        worker.execute();
    }

    /* =========================================================================
       CAPES
       ========================================================================= */

    private void loadCapes(String email) {
        if (email == null || email.isEmpty()) return;
        SwingWorker<List<CapeInfo>, Void> worker = new SwingWorker<List<CapeInfo>, Void>() {
            @Override
            protected List<CapeInfo> doInBackground() throws Exception {
                List<CapeInfo> allCapes = new ArrayList<>();
                Set<String> seenFilenames = new HashSet<>();

                // 1. Load capes from server API
                try {
                    URL listUrl = new URL(API_BASE + "/api/cape/list?email=" + URLEncoder.encode(email, "UTF-8"));
                    HttpURLConnection conn = (HttpURLConnection) listUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }

                    JsonObject data = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonArray capes = data.getAsJsonArray("capes");

                    // Load active cape
                    URL activeUrl = new URL(API_BASE + "/api/cape/active?email=" + URLEncoder.encode(email, "UTF-8"));
                    HttpURLConnection activeConn = (HttpURLConnection) activeUrl.openConnection();
                    activeConn.setRequestMethod("GET");
                    activeConn.setConnectTimeout(5000);
                    activeConn.setReadTimeout(5000);

                    StringBuilder activeResponse = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeConn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) activeResponse.append(line);
                    }
                    JsonObject activeData = JsonParser.parseString(activeResponse.toString()).getAsJsonObject();
                    String activeId = activeData.has("activeCape") && !activeData.get("activeCape").isJsonNull()
                            ? activeData.getAsJsonObject("activeCape").get("id").getAsString()
                            : null;

                    if (capes != null) {
                        for (JsonElement el : capes) {
                            JsonObject cape = el.getAsJsonObject();
                            String id = cape.get("id").getAsString();
                            String name = cape.get("name").getAsString();
                            String filename = cape.get("filename").getAsString();
                            String url = cape.get("url").getAsString();
                            boolean isActive = id.equals(activeId);
                            seenFilenames.add(filename);
                            allCapes.add(new CapeInfo(id, name, filename, url, isActive, "server"));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Cliont Cape] Failed to load server capes: " + e.getMessage());
                }

                // 2. Scan local capes directory
                try {
                    if (CAPES_DIR != null && CAPES_DIR.toFile().exists()) {
                        File[] files = CAPES_DIR.toFile().listFiles((dir, name) ->
                                name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".apng"));
                        if (files != null) {
                            for (File file : files) {
                                String filename = file.getName();
                                if (seenFilenames.contains(filename)) continue;
                                seenFilenames.add(filename);
                                String id = "local-" + filename;
                                String name = filename;
                                String url = API_BASE + "/api/cape/serve/" + filename;
                                boolean isActive = false;
                                allCapes.add(new CapeInfo(id, name, filename, url, isActive, "local"));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Cliont Cape] Failed to scan local capes: " + e.getMessage());
                }

                return allCapes;
            }

            @Override
            protected void done() {
                try {
                    List<CapeInfo> allCapes = get();
                    SwingUtilities.invokeLater(() -> {
                        capeGrid.removeAll();
                        if (allCapes == null || allCapes.isEmpty()) {
                            emptyLabel.setVisible(true);
                            capeGrid.setVisible(false);
                            return;
                        }
                        emptyLabel.setVisible(false);
                        capeGrid.setVisible(true);

                        for (CapeInfo cape : allCapes) {
                            JPanel card = createCapeCard(cape.id, cape.name, cape.filename, cape.url, cape.isActive);
                            capeGrid.add(card);
                        }
                        capeGrid.revalidate();
                        capeGrid.repaint();
                    });
                } catch (Exception e) {
                    System.err.println("[Cliont Cape] Failed to load capes: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        capeGrid.removeAll();
                        emptyLabel.setText("Failed to load capes.");
                        emptyLabel.setVisible(true);
                        capeGrid.setVisible(false);
                    });
                }
            }
        };
        worker.execute();
    }

    private JPanel createCapeCard(String id, String name, String filename, String url, boolean isActive) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isActive ? CLIONT_PURPLE : Color.LIGHT_GRAY, isActive ? 2 : 1),
                new EmptyBorder(10, 10, 10, 10)));
        card.setBackground(isActive ? new Color(230, 220, 255) : Color.WHITE);
        card.setPreferredSize(new Dimension(200, 180));
        card.setMaximumSize(new Dimension(200, 180));

        // Thumbnail
        JLabel thumb = new JLabel();
        thumb.setPreferredSize(new Dimension(180, 60));
        thumb.setMaximumSize(new Dimension(180, 60));
        thumb.setHorizontalAlignment(SwingConstants.CENTER);
        thumb.setVerticalAlignment(SwingConstants.CENTER);
        thumb.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        thumb.setBackground(Color.WHITE);
        thumb.setOpaque(true);

        try {
            Image img = Toolkit.getDefaultToolkit().getImage(new URL(url));
            MediaTracker tracker = new MediaTracker(thumb);
            tracker.addImage(img, 0);
            tracker.waitForAll();
            Image scaled = img.getScaledInstance(180, 60, Image.SCALE_SMOOTH);
            thumb.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            thumb.setText("No preview");
        }

        if (isActive) {
            JLabel badge = new JLabel("EQUIPPED");
            badge.setFont(new Font("Arial", Font.BOLD, 10));
            badge.setForeground(Color.WHITE);
            badge.setBackground(CLIONT_PURPLE);
            badge.setOpaque(true);
            badge.setBorder(new EmptyBorder(2, 6, 2, 6));
            thumb.setLayout(null);
            badge.setBounds(180 - 60, 5, 55, 18);
            thumb.add(badge);
        }

        card.add(thumb, BorderLayout.NORTH);

        // Info
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        nameLabel.setForeground(Color.DARK_GRAY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fileLabel = new JLabel(filename);
        fileLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        fileLabel.setForeground(Color.GRAY);
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(nameLabel);
        info.add(Box.createVerticalStrut(2));
        info.add(fileLabel);

        card.add(info, BorderLayout.CENTER);

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);

        if (isActive) {
            JButton disableBtn = new JButton("Disable");
            disableBtn.setFont(new Font("Arial", Font.PLAIN, 11));
            disableBtn.setBackground(Color.LIGHT_GRAY);
            disableBtn.setForeground(Color.WHITE);
            disableBtn.setFocusPainted(false);
            disableBtn.setBorderPainted(false);
            disableBtn.setPreferredSize(new Dimension(70, 24));
            disableBtn.addActionListener(e -> equipCape(id, false));
            actions.add(disableBtn);
        } else {
            JButton equipBtn = new JButton("Equip");
            equipBtn.setFont(new Font("Arial", Font.PLAIN, 11));
            equipBtn.setBackground(CLIONT_PURPLE);
            equipBtn.setForeground(Color.WHITE);
            equipBtn.setFocusPainted(false);
            equipBtn.setBorderPainted(false);
            equipBtn.setPreferredSize(new Dimension(70, 24));
            equipBtn.addActionListener(e -> equipCape(id, true));
            actions.add(equipBtn);
        }

        card.add(actions, BorderLayout.SOUTH);

        return card;
    }

    private void chooseAndUploadCape() {
        if (cliontEmail == null || cliontEmail.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Please log in first.", "Not logged in",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser(CAPES_DIR.toFile());
        chooser.setDialogTitle("Select Cape File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "PNG or APNG images", "png", "apng"));

        int result = chooser.showOpenDialog(root);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        String email = cliontEmail;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    URL url = new URL(API_BASE + "/api/cape/upload");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);

                    String boundary = "---------------------------" + System.currentTimeMillis();
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    try (OutputStream os = conn.getOutputStream();
                         PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {

                        // File part
                        writer.append("--" + boundary).append("\r\n");
                        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"").append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n");
                        writer.append("\r\n");
                        writer.flush();

                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                        os.flush();
                        writer.append("\r\n").flush();

                        // capeName part
                        writer.append("--" + boundary).append("\r\n");
                        writer.append("Content-Disposition: form-data; name=\"capeName\"").append("\r\n");
                        writer.append("\r\n");
                        writer.append(file.getName()).append("\r\n").flush();

                        // email part
                        writer.append("--" + boundary).append("\r\n");
                        writer.append("Content-Disposition: form-data; name=\"email\"").append("\r\n");
                        writer.append("\r\n");
                        writer.append(email).append("\r\n").flush();

                        writer.append("--" + boundary + "--").append("\r\n");
                        writer.flush();
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            throw new IOException("Upload failed: " + sb.toString());
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(root, "Cape uploaded successfully!", "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                        loadCapes(email);
                    });

                } catch (Exception e) {
                    System.err.println("[Cliont Cape] Upload failed: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(root, "Upload failed: " + e.getMessage(), "Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
                return null;
            }
        };
        worker.execute();
    }

    private void equipCape(String capeId, boolean equip) {
        if (cliontEmail == null || cliontEmail.isEmpty()) {
            JOptionPane.showMessageDialog(root, "Please log in first.", "Not logged in",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String email = cliontEmail;

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    URL url = new URL(API_BASE + "/api/cape/equip");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    JsonObject body = new JsonObject();
                    body.addProperty("email", email);
                    body.addProperty("capeId", capeId);
                    body.addProperty("equip", equip);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.toString().getBytes("UTF-8"));
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        throw new IOException("Server returned: " + responseCode);
                    }

                    SwingUtilities.invokeLater(() -> {
                        loadCapes(email);
                        JOptionPane.showMessageDialog(root, "Cape " + (equip ? "equipped" : "disabled") + " successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception e) {
                    System.err.println("[Cliont Cape] Equip failed: " + e.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(root, "Failed to " + (equip ? "equip" : "disable") + " cape: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
                return null;
            }
        };
        worker.execute();
    }

    private static class CapeInfo {
        final String id;
        final String name;
        final String filename;
        final String url;
        final boolean isActive;
        final String source;

        CapeInfo(String id, String name, String filename, String url, boolean isActive, String source) {
            this.id = id;
            this.name = name;
            this.filename = filename;
            this.url = url;
            this.isActive = isActive;
            this.source = source;
        }
    }
}
