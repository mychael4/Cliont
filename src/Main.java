import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

import com.google.gson.*;

import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class Main {

    static final String CLIENT_ID = "4a121855-38a1-415a-95e5-54b27b294e92";

    static final String REDIRECT_URI =
            "http://localhost:8247";

    static final String SCOPE =
            "XboxLive.signin offline_access";

    static JFrame frame;

    static JFrame mainFrame;

    static JButton loginButton;

    static String refreshToken;

    static String minecraftToken;

    public static void main(String[] args)
            throws Exception {

        SSLFix.apply();

        loadSavedAccount();

        SwingUtilities.invokeLater(() -> {

            try {

                frame = new JFrame("Cliont");

                frame.setSize(900, 500);

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

                Color cliont =
                        new Color(188, 19, 254);

                Color sideBarColor =
                        new Color(34,34,34);

                Color buttonColor =
                        new Color(89,89,89);

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
                        new Dimension(200,0)
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
                                new Dimension(200,30)
                        );

                JButton modsbutton =
                        createSidebarButton(
                                "Mods",
                                mcFont,
                                buttonColor,
                                new Dimension(200,30)
                        );

                JButton versionsbutton =
                        createSidebarButton(
                                "Profiles",
                                mcFont,
                                buttonColor,
                                new Dimension(200,30)
                        );

                homebutton.addActionListener(e -> {

                    content.setVisible(true);

                    version.setVisible(false);

                    mods.setVisible(false);

                });

                modsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(false);

                    mods.setVisible(true);

                });

                versionsbutton.addActionListener(e -> {

                    content.setVisible(false);

                    version.setVisible(true);

                    mods.setVisible(false);

                });

                sidebar.add(homebutton);

                sidebar.add(Box.createVerticalStrut(10));

                sidebar.add(modsbutton);

                sidebar.add(Box.createVerticalStrut(10));

                sidebar.add(versionsbutton);

                JPanel accountbar =
                        new JPanel();

                accountbar.setPreferredSize(
                        new Dimension(150,0)
                );

                accountbar.setBackground(
                        sideBarColor
                );

                loginButton =
                        createSidebarButton(
                                "Login",
                                mcFont,
                                buttonColor,
                                new Dimension(120,40)
                        );

                loginButton.addActionListener(e -> {

                    new Thread(() -> {

                        try {

                            setAppBusy(true);

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

                        } finally {

                            setAppBusy(false);

                        }

                    }).start();

                });

                accountbar.add(loginButton);

                centerPanel.add(content);

                centerPanel.add(version);

                centerPanel.add(mods);

                frame.add(
                        sidebar,
                        BorderLayout.WEST
                );

                frame.add(
                        accountbar,
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
        System.out.println("XBOX RESPONSE:");
        System.out.println(xboxResponse);

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
        System.out.println("xsets RESPONSE:");
        System.out.println(xstsResponse);

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
        System.out.println("MC LOGIN RESPONSE:");
        System.out.println(mcLoginResponse);

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

        saveAccount(
                username,
                uuid,
                refreshToken
        );

        SwingUtilities.invokeLater(() -> {

            loginButton.setText(username);

        });

    }

    static void openLoginWindow(String url) {

        JFrame loginFrame =
                new JFrame("Microsoft Login");

        loginFrame.setSize(500,700);

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

        connection.getOutputStream()
                .write(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
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
                    new File(dir,"account.json");

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
                new Color(0,0,0,120)
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

    public static void setAppBusy(boolean busy) {

        if (mainFrame == null) {
            return;
        }

        mainFrame.getGlassPane()
                .setVisible(busy);

        setComponentEnabled(
                mainFrame.getContentPane(),
                !busy
        );

    }

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

    private static JButton createSidebarButton(
            String text,
            Font font,
            Color bg,
            Dimension size
    ) {

        JButton button =
                new JButton(text);

        button.setFont(
                font.deriveFont(18f)
        );

        button.setMaximumSize(size);

        button.setPreferredSize(size);

        button.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        button.setFocusPainted(false);

        button.setBorderPainted(false);

        button.setBackground(bg);

        button.setForeground(Color.WHITE);

        return button;

    }

}