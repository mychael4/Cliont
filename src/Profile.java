import java.nio.file.Path;
import java.nio.file.Paths;

public class Profile {
    public String name;
    public String versionId;
    public String loader; // Vanilla, Fabric, Forge

    public Profile(String name, String versionId, String loader) {
        this.name = name;
        this.versionId = versionId;
        this.loader = loader;
    }

    public Path getProfileDir() {
        return Paths.get(System.getenv("APPDATA"), ".cliont", "profiles", name);
    }
}