import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.io.*;
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

    private static final String UPDATE_CHECK_URL = "http://192.168.1.72:25565/version.json";
// ^ adjust this path to whatever the real API endpoint is - the #/updates
//   route is client-side (SPA), so it won't return version data over plain HTTP.

    private static String CURRENT_LAUNCHER_VERSION = "0.0.0"; // fallback if file missing

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

                frame.setSize(900, 500);
                frame.setIconImage(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/Icon-rounded.png")));

                frame.setDefaultCloseOperation(
                        JFrame.EXIT_ON_CLOSE
                );

                frame.setLocationRelativeTo(null);

                frame.setLayout(new BorderLayout());

                JPanel centerPanel =
                        new JPanel(new CardLayout());

                JPanel content =
                        Content.create();

                JPanel version =
                        Version.create();

                JPanel mods =
                        Mods.create();

                JPanel settings =
                        Settings.create();

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

                });

                modsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(true);

                    settings.setVisible(false);

                });

                versionsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(true);

                    mods.setVisible(false);

                    settings.setVisible(false);

                });

                settingsButton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(false);

                    settings.setVisible(true);

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

                sidebar.add(Box.createVerticalGlue()); // NEW: absorbs all leftover space, pushing everything after it to the bottom

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

                frame.add(
                        sidebar,
                        BorderLayout.WEST
                );

                frame.add(
                        rightSideBar,
                        BorderLayout.EAST
                );

                frame.add(
                        centerPanel,
                        BorderLayout.CENTER
                );

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

        JFrame loginFrame =
                new JFrame("Microsoft Login");

        loginFrame.setSize(500, 700);

        loginFrame.setLocationRelativeTo(null);

        JFXPanel fxPanel =
                new JFXPanel();

        loginFrame.add(fxPanel);

        loginFrame.setVisible(true);

        Platform.runLater(() -> {

            WebView webView =
                    new WebView();

            webView.getEngine().load(url);

            fxPanel.setScene(
                    new Scene(webView)
            );

        });

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
                Color bgColor = getModel().isPressed() ? base.darker() : (getModel().isRollover() ? base.brighter() : base);
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
        button.setBackground(bg);

        button.setForeground(UIUtils.TEXT);

        return button;

    }
}