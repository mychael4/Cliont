import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class Main {

    static final String CLIENT_ID = "4a121855-38a1-415a-95e5-54b27b294e92";
    static final String REDIRECT_URI =
            "http://localhost:8247";
    static final String SCOPE =
            "XboxLive.signin offline_access";
    public static MinecraftAuth.LoginData loginData;
    static JFrame frame;

    static JFrame mainFrame;

    static JButton loginButton;

    static Version.ProfilePanel profilePanel;

    static String refreshToken;

    static String minecraftToken;

    public static Color cliont = new Color(188, 19, 254);
    private static final Color TITLE_BAR_BG = new Color(250, 250, 250);
    private static final Color TITLE_BAR_TEXT = new Color(22, 22, 22);
    private static final Color TITLE_BAR_MUTED = new Color(112, 112, 112);
    private static final Color RUNNING_GREEN = new Color(26, 170, 92);
    private static JLabel titleStatusLabel;
    private static JPanel titleBarPanel;
    private static JPanel titleControlsPanel;
    private static JPanel titleLeftGroup;
    private static String activeProfileName;
    private static final Set<String> runningProfiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final String UPDATE_CHECK_URL = "http://192.168.1.72:25565/version.json";
// ^ adjust this path to whatever the real API endpoint is - the #/updates
//   route is client-side (SPA), so it won't return version data over plain HTTP.

    private static String CURRENT_LAUNCHER_VERSION = "0.0.0"; // fallback if file missing

    private static Image loadAppImage(String resourcePath) {
        URL url = Main.class.getResource(resourcePath);
        if (url == null) return null;
        return Toolkit.getDefaultToolkit().getImage(url);
    }

    public static boolean isProfileRunning(String profileName) {
        return profileName != null && runningProfiles.contains(profileName.trim());
    }

    public static void refreshTitleBarTheme() {
        if (titleBarPanel == null || titleLeftGroup == null || titleControlsPanel == null || titleStatusLabel == null) {
            return;
        }
        Color bg = UIUtils.CARD;
        Color text = UIUtils.TEXT;
        Color muted = UIUtils.TEXT_MUTED;
        Color buttonBg = UIUtils.BG;
        titleBarPanel.setBackground(bg);
        titleLeftGroup.setBackground(bg);
        titleControlsPanel.setBackground(bg);
        titleStatusLabel.setBackground(buttonBg);
        titleStatusLabel.setForeground(isProfileRunning(activeProfileName) ? RUNNING_GREEN : muted);
        for (Component c : titleLeftGroup.getComponents()) {
            if (c instanceof JLabel) c.setForeground(text);
        }
        titleBarPanel.repaint();
    }

    private static void updateTitleStatusLabel() {
        if (titleStatusLabel == null) return;
        SwingUtilities.invokeLater(() -> {
            if (activeProfileName != null && !activeProfileName.isBlank()) {
                titleStatusLabel.setText("• Running: " + activeProfileName);
                titleStatusLabel.setForeground(RUNNING_GREEN);
            } else {
                titleStatusLabel.setText("Ready");
                titleStatusLabel.setForeground(UIUtils.TEXT_MUTED);
            }
            titleStatusLabel.repaint();
            refreshTitleBarTheme();
        });
    }

    public static void setRunningProfile(String profileName, Process process) {
        if (profileName == null || profileName.isBlank()) return;
        String trimmed = profileName.trim();
        if (process != null) {
            runningProfiles.add(trimmed);
        } else {
            runningProfiles.remove(trimmed);
        }
        activeProfileName = trimmed;
        updateTitleStatusLabel();

        if (process == null) return;
        new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                runningProfiles.remove(trimmed);
                activeProfileName = null;
                updateTitleStatusLabel();
            }
        }, "profile-monitor").start();
    }

    public static void configureProfileButtonState(JButton button, String profileName, boolean allowWhenLoggedOut) {
        if (button == null || profileName == null || profileName.isBlank()) {
            return;
        }

        boolean running = isProfileRunning(profileName);
        boolean loginReady = allowWhenLoggedOut || Main.loginData != null;
        button.setEnabled(loginReady && !running);
        if (button.getBackground() != null) {
            button.setBackground(running || !loginReady ? UIUtils.BUTTON : Main.cliont);
        }
        if (button.getForeground() != null && !button.getForeground().equals(Color.WHITE)) {
            button.setForeground(Color.WHITE);
        }
        button.setToolTipText(running ? "Profile is already running" : null);
    }

    private static JButton createWindowControlButton(String symbol, Color hoverColor, Runnable action) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hovered = getModel().isRollover();
                boolean pressed = getModel().isPressed();
                Color bg = pressed ? new Color(230, 230, 230) : (hovered ? hoverColor : new Color(245, 245, 245));
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(pressed ? Color.WHITE : ("■".equals(symbol) ? new Color(210, 45, 45) : new Color(55, 55, 55)));
                g2.setStroke(new BasicStroke(1.9f));

                if ("—".equals(symbol)) {
                    g2.drawLine(6, getHeight() / 2, getWidth() - 6, getHeight() / 2);
                } else if ("□".equals(symbol)) {
                    g2.drawRoundRect(7, 7, getWidth() - 14, getHeight() - 14, 3, 3);
                } else {
                    g2.drawLine(7, 7, getWidth() - 7, getHeight() - 7);
                    g2.drawLine(getWidth() - 7, 7, 7, getHeight() - 7);
                }
                g2.dispose();
            }
        };
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(26, 26));
        button.setMinimumSize(new Dimension(26, 26));
        button.setMaximumSize(new Dimension(26, 26));
        button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static void applyWindowShape(JFrame frame) {
        int state = frame.getExtendedState();
        if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            frame.setShape(null);
        } else {
            frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 22, 22));
        }
    }

    private static void installCustomTitleBar(JFrame frame, JPanel mainContent) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(245, 245, 245));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBarPanel = titleBar;
        titleBar.setBackground(TITLE_BAR_BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 12));
        titleBar.setPreferredSize(new Dimension(0, 52));

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLeftGroup = leftGroup;
        leftGroup.setOpaque(false);

        Image iconImage = loadAppImage("/Icon-rounded.png");
        JLabel appIcon = iconImage == null
                ? new JLabel("C")
                : new JLabel(new ImageIcon(iconImage.getScaledInstance(22, 22, Image.SCALE_SMOOTH)));
        appIcon.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel appTitle = new JLabel("Cliont");
        appTitle.setForeground(TITLE_BAR_TEXT);
        appTitle.setFont(new Font("Arial", Font.BOLD, 14));

        titleStatusLabel = new JLabel("Ready");
        titleStatusLabel.setForeground(TITLE_BAR_MUTED);
        titleStatusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        titleStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        titleStatusLabel.setOpaque(true);
        titleStatusLabel.setBackground(new Color(244, 244, 244));
        titleStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        leftGroup.add(appIcon);
        leftGroup.add(appTitle);
        leftGroup.add(titleStatusLabel);
        titleBar.add(leftGroup, BorderLayout.WEST);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        titleControlsPanel = controlsPanel;
        controlsPanel.setOpaque(false);

        JButton minimizeButton = createWindowControlButton("—", new Color(70, 70, 70), () -> frame.setState(Frame.ICONIFIED));
        JButton maximizeButton = createWindowControlButton("□", new Color(70, 70, 70), () -> {
            int state = frame.getExtendedState();
            if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                frame.setExtendedState(Frame.NORMAL);
            } else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
        });
        JButton closeButton = createWindowControlButton("✕", new Color(232, 83, 83), () -> System.exit(0));

        controlsPanel.add(minimizeButton);
        controlsPanel.add(maximizeButton);
        controlsPanel.add(closeButton);
        titleBar.add(controlsPanel, BorderLayout.EAST);

        Point[] dragAnchor = {null};
        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragAnchor[0] = e.getPoint();
            }
        });
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragAnchor[0] == null) return;
                Point current = MouseInfo.getPointerInfo().getLocation();
                frame.setLocation(current.x - dragAnchor[0].x, current.y - dragAnchor[0].y);
            }
        });

        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyWindowShape(frame);
            }
        });

        root.add(titleBar, BorderLayout.NORTH);
        root.add(mainContent, BorderLayout.CENTER);
        frame.setContentPane(root);
        applyWindowShape(frame);
        updateTitleStatusLabel();
    }

    private static String loadCurrentVersion() {
        try (InputStream in = Main.class.getResourceAsStream("/version.json")) {
            if (in == null) {
                System.err.println("version.json not found in resources, using fallback version.");
                return CURRENT_LAUNCHER_VERSION;
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return json.get("version").getAsString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return CURRENT_LAUNCHER_VERSION;
        }
    }

    private static void checkForUpdateAsync(String currentVersion, JButton button) {
        new Thread(() -> {
            try {
                String latestVersion = fetchLatestVersion();
                boolean updateAvailable = isNewerVersion(latestVersion, currentVersion);

                SwingUtilities.invokeLater(() -> button.setVisible(updateAvailable));
            } catch (Exception ex) {
                ex.printStackTrace();
                // Fail silently (keep button hidden) rather than showing on error
            }
        }, "update-check").start();
    }

    private static String fetchLatestVersion() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPDATE_CHECK_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Adjust this to match whatever shape the real endpoint returns.
        // Assuming JSON like: {"latestVersion": "1.4.2"}
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("latestVersion").getAsString();
    }

    private static boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int l = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int c = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    static void main(String[] args)
            throws Exception {


        SSLFix.apply();

        CURRENT_LAUNCHER_VERSION = loadCurrentVersion();

        loadSavedAccount();

        SwingUtilities.invokeLater(() -> {

            try {

                frame = new JFrame("Cliont");
                frame.setUndecorated(true);
                frame.setSize(900, 500);
                Image windowIcon = loadAppImage("/Icon-rounded.png");
                if (windowIcon != null) {
                    frame.setIconImage(windowIcon);
                }
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLocationRelativeTo(null);

                JPanel centerPanel = new JPanel(new CardLayout());
                JPanel appLayout = new JPanel(new BorderLayout());
                appLayout.setBackground(UIUtils.BG);

                JPanel content =
                        Content.create();

                JPanel version =
                        Version.create();

                JPanel mods =
                        Mods.create();

                JPanel settings =
                        Settings.create();

                Account accountPanel =
                        new Account();
                JPanel account =
                        accountPanel.getPanel();

                Color sideBarColor =
                        UIUtils.SIDEBAR;

                Color buttonColor =
                        UIUtils.BUTTON;

                InputStream fontStream =
                        Main.class.getResourceAsStream(
                                "/Minecraft.ttf"
                        );

                Font mcFont =
                        Font.createFont(
                                Font.TRUETYPE_FONT,
                                fontStream
                        );

                JPanel sidebar = new JPanel();

                sidebar.setPreferredSize(
                        new Dimension(200, 0)
                );

                sidebar.setBackground(sideBarColor);

                sidebar.setLayout(
                        new BoxLayout(
                                sidebar,
                                BoxLayout.Y_AXIS
                        )
                );

                JLabel title =
                        new JLabel("Cliont");

                title.setFont(
                        mcFont.deriveFont(42f)
                );

                title.setForeground(cliont);

                title.setAlignmentX(
                        Component.CENTER_ALIGNMENT
                );

                sidebar.add(Box.createVerticalStrut(20));

                sidebar.add(title);

                sidebar.add(Box.createVerticalStrut(30));

                JButton homebutton =
                        createSidebarButton(
                                "Home",
                                mcFont,
                                buttonColor,
                                new Dimension(200, 30)
                        );
                homebutton.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton modsbutton =
                        createSidebarButton(
                                "Mods",
                                mcFont,
                                buttonColor,
                                new Dimension(200, 30)
                        );
                modsbutton.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton versionsbutton =
                        createSidebarButton(
                                "Profiles",
                                mcFont,
                                buttonColor,
                                new Dimension(200, 30)
                        );
                versionsbutton.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton accountButton =
                        createSidebarButton(
                                "Account",
                                mcFont,
                                buttonColor,
                                new Dimension(200, 30)
                        );
                accountButton.setAlignmentX(Component.CENTER_ALIGNMENT);

                JButton settingsButton =
                        createSidebarButton(
                                "Settings",
                                mcFont,
                                buttonColor,
                                new Dimension(200, 30)
                        );
                settingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);

                homebutton.addActionListener(e -> {

                    content.setVisible(true);

                    version.setVisible(false);

                    mods.setVisible(false);

                    settings.setVisible(false);

                    account.setVisible(false);

                });

                modsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(true);

                    settings.setVisible(false);

                    account.setVisible(false);
                });

                versionsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(true);

                    mods.setVisible(false);

                    settings.setVisible(false);

                    account.setVisible(false);
                });

                settingsButton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(false);

                    settings.setVisible(true);

                    account.setVisible(false);
                });

                accountButton.addActionListener(e -> {
                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(false);

                    settings.setVisible(false);

                    account.setVisible(true);
                });

                JPanel FunctionButtonsPanel = new JPanel();
                FunctionButtonsPanel.setBackground(null);
                FunctionButtonsPanel.setLayout(new BoxLayout(FunctionButtonsPanel, BoxLayout.Y_AXIS));

                FunctionButtonsPanel.add(homebutton);

                FunctionButtonsPanel.add(Box.createVerticalStrut(10));

                FunctionButtonsPanel.add(modsbutton);

                FunctionButtonsPanel.add(Box.createVerticalStrut(10));

                FunctionButtonsPanel.add(versionsbutton);

                sidebar.add(FunctionButtonsPanel);

                sidebar.add(Box.createVerticalGlue());
                // NEW: absorbs all leftover space, pushing everything after it to the bottom

                sidebar.add(accountButton);

                sidebar.add(Box.createVerticalStrut(10)); // Optional: gap between account and settings

                sidebar.add(settingsButton);

                sidebar.add(Box.createVerticalStrut(20)); // Optional: gap at the very bottom

                JPanel rightSideBar =
                        new JPanel();

                rightSideBar.setPreferredSize(
                        new Dimension(150, 0)
                );

                rightSideBar.setBackground(
                        sideBarColor
                );

                // Wenn bereits eingeloggt, zeige Version.ProfilePanel, sonst zeige Login Button
                if (loginData != null) {
                    profilePanel = new Version.ProfilePanel(
                            loginData.username,
                            loginData.uuid,
                            () -> logout()
                    );
                    rightSideBar.add(profilePanel);
                } else {
                    loginButton =
                            createSidebarButton(
                                    "Login",
                                    mcFont,
                                    buttonColor,
                                    new Dimension(120, 40)
                            );

                    loginButton.addActionListener(e -> {

                        new Thread(() -> {

                            try {

                                //setAppBusy(true);

                                if (refreshToken != null) {

                                    loginWithRefreshToken();

                                } else {

                                    login();

                                }

                            } catch (Exception ex) {

                                ex.printStackTrace();

                                JOptionPane.showMessageDialog(
                                        frame,
                                        ex.getMessage()
                                );

                            }

                        }).start();

                    });

                    JButton updateAvailableButton = createSidebarButton(
                            "Update Available",
                            mcFont,
                            cliont,
                            new Dimension(120, 40)
                    );
                    updateAvailableButton.setFont(mcFont.deriveFont(14f));
                    updateAvailableButton.setForeground(Color.WHITE); // sits on the purple accent, stays white in every theme
                    updateAvailableButton.putClientProperty("cliont.themeExempt", true);
                    updateAvailableButton.addActionListener(e -> {
                        try {
                            Desktop.getDesktop().browse(new URI("http://192.168.1.72:25565/#/updates"));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });

                    updateAvailableButton.setVisible(false);
                    checkForUpdateAsync(CURRENT_LAUNCHER_VERSION, updateAvailableButton);

                    rightSideBar.add(loginButton, BorderLayout.NORTH);
                    rightSideBar.add(updateAvailableButton, BorderLayout.SOUTH); // was capitalized wrong before
                }

                centerPanel.add(content);

                centerPanel.add(version);

                centerPanel.add(mods);

                centerPanel.add(settings);

                centerPanel.add(account);

                appLayout.add(
                        sidebar,
                        BorderLayout.WEST
                );

                appLayout.add(
                        rightSideBar,
                        BorderLayout.EAST
                );

                appLayout.add(
                        centerPanel,
                        BorderLayout.CENTER
                );

                installCustomTitleBar(frame, appLayout);
                frame.setVisible(true);

                mainFrame = frame;

                initializeBusyGlassPane(frame);

                if (refreshToken != null) {

                    new Thread(() -> {

                        try {

                            loginWithRefreshToken();

                        } catch (Exception ex) {

                            ex.printStackTrace();

                        }

                    }).start();

                }

            } catch (Exception ex) {

                ex.printStackTrace();

            }

        });

    }


    static void login() throws Exception {

        CountDownLatch latch =
                new CountDownLatch(1);

        final String[] authCode =
                new String[1];

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(8247),
                        0
                );

        server.createContext("/", exchange -> {

            String query =
                    exchange.getRequestURI()
                            .getQuery();

            if (query != null) {

                String[] params =
                        query.split("&");

                for (String param : params) {

                    if (param.startsWith("code=")) {

                        authCode[0] =
                                URLDecoder.decode(
                                        param.substring(5),
                                        StandardCharsets.UTF_8
                                );

                    }

                }

                String response = """
                        <html>
                        <body>
                        <h2>Login erfolgreich</h2>
                        Du kannst dieses Fenster schliessen.
                        </body>
                        </html>
                        """;

                exchange.sendResponseHeaders(
                        200,
                        response.getBytes().length
                );

                OutputStream os =
                        exchange.getResponseBody();

                os.write(response.getBytes());

                os.close();

                latch.countDown();

            }

        });

        server.start();

        String loginUrl =
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
                        + "?client_id=" + CLIENT_ID
                        + "&response_type=code"
                        + "&redirect_uri="
                        + URLEncoder.encode(
                        REDIRECT_URI,
                        StandardCharsets.UTF_8
                )
                        + "&scope="
                        + URLEncoder.encode(
                        SCOPE,
                        StandardCharsets.UTF_8
                );

        openLoginWindow(loginUrl);

        latch.await();

        server.stop(0);

        String tokenResponse = post(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",

                "client_id=" + CLIENT_ID
                        + "&code=" + authCode[0]
                        + "&grant_type=authorization_code"
                        + "&redirect_uri="
                        + URLEncoder.encode(
                        REDIRECT_URI,
                        StandardCharsets.UTF_8
                )
        );
        System.out.println("token RESPONSE:");
        System.out.println(tokenResponse);

        JsonObject tokenJson =
                JsonParser.parseString(
                        tokenResponse
                ).getAsJsonObject();

        String accessToken =
                tokenJson.get("access_token")
                        .getAsString();

        refreshToken =
                tokenJson.get("refresh_token")
                        .getAsString();

        loginMinecraft(accessToken);

    }

    static void loginWithRefreshToken()
            throws Exception {

        String tokenResponse = post(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",

                "client_id=" + CLIENT_ID
                        + "&refresh_token="
                        + refreshToken
                        + "&grant_type=refresh_token"
                        + "&redirect_uri="
                        + URLEncoder.encode(
                        REDIRECT_URI,
                        StandardCharsets.UTF_8
                )
        );

        JsonObject tokenJson =
                JsonParser.parseString(
                        tokenResponse
                ).getAsJsonObject();

        String accessToken =
                tokenJson.get("access_token")
                        .getAsString();

        refreshToken =
                tokenJson.get("refresh_token")
                        .getAsString();

        loginMinecraft(accessToken);

    }

    static void loginMinecraft(
            String microsoftToken
    ) throws Exception {

        String xboxResponse = postJson(

                "https://user.auth.xboxlive.com/user/authenticate",

                """
                        {
                          "Properties": {
                            "AuthMethod": "RPS",
                            "SiteName": "user.auth.xboxlive.com",
                            "RpsTicket": "d=%s"
                          },
                          "RelyingParty": "http://auth.xboxlive.com",
                          "TokenType": "JWT"
                        }
                        """.formatted(microsoftToken)
        );

        JsonObject xboxJson =
                JsonParser.parseString(
                        xboxResponse
                ).getAsJsonObject();

        String xboxToken =
                xboxJson.get("Token")
                        .getAsString();

        String uhs = xboxJson
                .getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0)
                .getAsJsonObject()
                .get("uhs")
                .getAsString();

        String xstsResponse = postJson(

                "https://xsts.auth.xboxlive.com/xsts/authorize",

                """
                        {
                          "Properties": {
                            "SandboxId": "RETAIL",
                            "UserTokens": ["%s"]
                          },
                          "RelyingParty": "rp://api.minecraftservices.com/",
                          "TokenType": "JWT"
                        }
                        """.formatted(xboxToken)
        );

        JsonObject xstsJson =
                JsonParser.parseString(
                        xstsResponse
                ).getAsJsonObject();

        String xstsToken =
                xstsJson.get("Token")
                        .getAsString();

        String mcLoginResponse = postJson(

                "https://api.minecraftservices.com/authentication/login_with_xbox",

                """
                        {
                          "identityToken": "XBL3.0 x=%s;%s"
                        }
                        """.formatted(uhs, xstsToken)
        );

        JsonObject mcLoginJson =
                JsonParser.parseString(
                        mcLoginResponse
                ).getAsJsonObject();

        minecraftToken =
                mcLoginJson.get("access_token")
                        .getAsString();

        URL profileUrl = new URL(
                "https://api.minecraftservices.com/minecraft/profile"
        );

        HttpURLConnection connection =
                (HttpURLConnection)
                        profileUrl.openConnection();

        connection.setRequestProperty(
                "Authorization",
                "Bearer " + minecraftToken
        );

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream()
                        )
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {

            response.append(line);

        }

        JsonObject profileJson =
                JsonParser.parseString(
                        response.toString()
                ).getAsJsonObject();

        String username =
                profileJson.get("name")
                        .getAsString();

        String uuid =
                profileJson.get("id")
                        .getAsString();

        MinecraftAuth.LoginData data =
                new MinecraftAuth.LoginData();

        data.username = username;
        data.uuid = uuid;
        data.minecraftAccessToken = minecraftToken;

        loginData = data;

        saveAccount(
                username,
                uuid,
                refreshToken
        );

        SwingUtilities.invokeLater(() -> {

            // Ersetze Login Button mit Version.ProfilePanel
            if (loginButton != null) {
                JPanel rightSideBar = (JPanel) loginButton.getParent();
                rightSideBar.remove(loginButton);
                profilePanel = new Version.ProfilePanel(
                        username,
                        uuid,
                        () -> logout()
                );
                rightSideBar.add(profilePanel);
                rightSideBar.revalidate();
                rightSideBar.repaint();
            } else if (profilePanel != null) {
                profilePanel.updateProfile(username, uuid);
            }

        });

    }

    static void openLoginWindow(String url) {
        try {
            // Versuche, den Standard-Browser zu öffnen. Fallback auf cmd start unter Windows.
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            }

            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                    "Der Standardbrowser wurde zum Login geöffnet. Schließe das Browserfenster nach erfolgreichem Login."));
        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                    "Fehler beim Öffnen des Browsers: " + ex.getMessage()));
        }
    }

    static String post(
            String url,
            String params
    ) throws Exception {

        byte[] postData =
                params.getBytes(
                        StandardCharsets.UTF_8
                );

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(url)
                                .openConnection();

        connection.setRequestMethod("POST");

        connection.setDoOutput(true);

        connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded"
        );

        connection.getOutputStream()
                .write(postData);

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream()
                        )
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {

            response.append(line);

        }

        return response.toString();

    }

    static String postJson(
            String url,
            String json
    ) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(url)
                                .openConnection();

        connection.setRequestMethod("POST");

        connection.setDoOutput(true);

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.getOutputStream().write(
                json.getBytes(StandardCharsets.UTF_8)
        );

        int code = connection.getResponseCode();

        InputStream stream;

        if (code >= 400) {
            stream = connection.getErrorStream();
        } else {
            stream = connection.getInputStream();
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(stream)
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        System.out.println("HTTP " + code);
        System.out.println(response);

        return response.toString();
    }

    static void saveAccount(
            String username,
            String uuid,
            String refreshToken
    ) {

        try {

            File dir = new File(
                    System.getProperty("user.home"),
                    ".cliont"
            );

            dir.mkdirs();

            File file =
                    new File(dir, "account.json");

            JsonObject json =
                    new JsonObject();

            json.addProperty(
                    "username",
                    username
            );

            json.addProperty(
                    "uuid",
                    uuid
            );

            json.addProperty(
                    "refresh_token",
                    refreshToken
            );

            FileWriter writer =
                    new FileWriter(file);

            writer.write(
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .create()
                            .toJson(json)
            );

            writer.close();

        } catch (Exception ex) {

            ex.printStackTrace();

        }

    }

    static void loadSavedAccount() {

        try {

            File file = new File(
                    System.getProperty("user.home")
                            + "/.cliont/account.json"
            );

            if (!file.exists()) {
                return;
            }

            BufferedReader reader =
                    new BufferedReader(
                            new FileReader(file)
                    );

            JsonObject json =
                    JsonParser.parseReader(reader)
                            .getAsJsonObject();

            refreshToken =
                    json.get("refresh_token")
                            .getAsString();

        } catch (Exception ex) {

            ex.printStackTrace();

        }

    }

    private static void initializeBusyGlassPane(
            JFrame frame
    ) {

        JPanel glassPanel =
                new JPanel();

        glassPanel.setOpaque(true);

        glassPanel.setBackground(
                new Color(0, 0, 0, 120)
        );

        glassPanel.addMouseListener(
                new java.awt.event.MouseAdapter() {
                }
        );

        glassPanel.addMouseMotionListener(
                new java.awt.event.MouseMotionAdapter() {
                }
        );

        glassPanel.setVisible(false);

        frame.setGlassPane(glassPanel);

    }

    //public static void setAppBusy(boolean busy) {

    //    if (mainFrame == null) {
    //        return;
    //    }

    //    mainFrame.getGlassPane()
    //            .setVisible(busy);

    //    setComponentEnabled(
    //            mainFrame.getContentPane(),
    //            !busy
    //    );

    //}

    private static void setComponentEnabled(
            Component component,
            boolean enabled
    ) {

        component.setEnabled(enabled);

        if (component instanceof Container container) {

            for (Component child
                    : container.getComponents()) {

                setComponentEnabled(
                        child,
                        enabled
                );

            }

        }

    }

    private static void logout() {
        loginData = null;
        refreshToken = null;
        minecraftToken = null;

        try {
            File file = new File(
                    System.getProperty("user.home") + "/.cliont/account.json"
            );
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            if (profilePanel != null && profilePanel.getParent() != null) {
                JPanel rightSideBar = (JPanel) profilePanel.getParent();
                rightSideBar.remove(profilePanel);
                profilePanel = null;

                InputStream fontStream = Main.class.getResourceAsStream("/Minecraft.ttf");
                Font mcFont = null;
                try {
                    mcFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    mcFont = new Font("Arial", Font.PLAIN, 18);
                }

                Color buttonColor = UIUtils.BUTTON;
                loginButton = createSidebarButton(
                        "Login",
                        mcFont,
                        buttonColor,
                        new Dimension(120, 40)
                );

                loginButton.addActionListener(e -> {
                    new Thread(() -> {
                        try {
                            if (refreshToken != null) {
                                loginWithRefreshToken();
                            } else {
                                login();
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(
                                    frame,
                                    ex.getMessage()
                            );
                        }
                    }).start();
                });

                rightSideBar.add(loginButton);
                rightSideBar.revalidate();
                rightSideBar.repaint();
            }
        });
    }

    private static JButton createSidebarButton(
            String text,
            Font font,
            Color bg,
            Dimension size
    ) {

        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getBackground();
                Color bgColor = getModel().isPressed()
                        ? UIUtils.alpha(base.darker(), 160)
                        : (getModel().isRollover() ? UIUtils.alpha(base.brighter(), 180) : UIUtils.alpha(base, 120));
                g2.setColor(bgColor);
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

        button.setFont(font.deriveFont(18f));
        button.setMaximumSize(size);
        button.setPreferredSize(size);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBackground(UIUtils.alpha(bg, 120));

        button.setForeground(UIUtils.TEXT);

        return button;

    }
}