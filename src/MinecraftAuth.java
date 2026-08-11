import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MinecraftAuth {

    public static LoginData loginWithMicrosoft(String clientId, String redirectUri) throws Exception {

        String loginUrl = "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=XboxLive.signin%20offline_access";

        Desktop.getDesktop().browse(new URI(loginUrl));

        String code = javax.swing.JOptionPane.showInputDialog(
                null,
                "Füge den Code aus der URL ein"
        );

        if (code == null || code.isEmpty()) {
            throw new Exception("Login abgebrochen");
        }

        String tokenResponse = post(
                "https://login.live.com/oauth20_token.srf",
                "client_id=" + clientId
                        + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&grant_type=authorization_code"
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        );
        System.out.println("token RESPONSE:");
        System.out.println(tokenResponse);


        JsonObject tokenJson = JsonParser.parseString(tokenResponse).getAsJsonObject();

        String microsoftAccessToken = tokenJson.get("access_token").getAsString();

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
                        """.formatted(microsoftAccessToken)
        );
        System.out.println("XBOX RESPONSE:");
        System.out.println(xboxResponse);

        JsonObject xboxJson = JsonParser.parseString(xboxResponse).getAsJsonObject();

        String xboxToken = xboxJson.get("Token").getAsString();

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

        JsonObject xstsJson = JsonParser.parseString(xstsResponse).getAsJsonObject();

        String xstsToken = xstsJson.get("Token").getAsString();

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

        JsonObject mcLoginJson = JsonParser.parseString(mcLoginResponse).getAsJsonObject();

        String minecraftAccessToken = mcLoginJson.get("access_token").getAsString();

        URL profileUrl = new URL("https://api.minecraftservices.com/minecraft/profile");

        HttpURLConnection profileConnection = (HttpURLConnection) profileUrl.openConnection();

        profileConnection.setRequestProperty(
                "Authorization",
                "Bearer " + minecraftAccessToken
        );

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(profileConnection.getInputStream())
        );

        StringBuilder response = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        JsonObject profileJson = JsonParser
                .parseString(response.toString())
                .getAsJsonObject();

        LoginData data = new LoginData();

        data.username = profileJson.get("name").getAsString();
        data.uuid = profileJson.get("id").getAsString();
        data.minecraftAccessToken = minecraftAccessToken;

        return data;
    }

    static String post(String url, String params) throws Exception {

        byte[] postData = params.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        connection.getOutputStream().write(postData);

        int code = connection.getResponseCode();

        InputStream stream =
                (code >= 400)
                        ? connection.getErrorStream()
                        : connection.getInputStream();

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream));

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        return response.toString();
    }

    private static String postJson(String url, String json) throws Exception {

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.getOutputStream().write(
                json.getBytes(StandardCharsets.UTF_8)
        );

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream())
        );

        StringBuilder response = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        return response.toString();
    }

    public static class LoginData {
        public String username;
        public String uuid;
        public String minecraftAccessToken;
    }
}
