import java.nio.file.*;

public class check {

    public static void main(String[] args) {

        Path[] folders = {
                Paths.get(System.getProperty("user.home"), "AppData\\Roaming\\.cliont"),
                Paths.get(System.getProperty("user.home"), "AppData\\Roaming\\.cliont\\versions"),
                Paths.get(System.getProperty("user.home"), "AppData\\Roaming\\.cliont\\libraries")
        };

        for (Path p : folders) {
            if (Files.exists(p)) {
                System.out.println("✔ Gefunden: " + p);
            } else {
                System.out.println("✖ Nicht gefunden: " + p);
            }
        }
    }
}
