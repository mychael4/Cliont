import java.io.File;
import java.lang.reflect.Method;

public class CustomClientLauncher {

    public static void main(String[] args) {
        try {
            System.out.println("[Client] Starte LaunchWrapper...");

            File emoteModJar = new File("Resources/Emotes/mod/emotecraft-fabric-for-MC1.21.11-3.2.0-b.build.159.jar");

            if (emoteModJar.exists()) {
                // Unter Java 9+ nutzt man diese Methode für den System ClassPath:
                java.lang.instrument.Instrumentation inst = getInstrumentation();
                if (inst != null) {
                    inst.appendToSystemClassLoaderSearch(new java.util.jar.JarFile(emoteModJar));
                    System.out.println("[Client] Emote-Mod erfolgreich geladen!");
                }
            }

            // Minecraft Main-Klasse aufrufen
            Class<?> vanillaMainClass = Class.forName("net.minecraft.client.main.Main");
            Method mainMethod = vanillaMainClass.getMethod("main", String[].class);

            mainMethod.invoke(null, (Object) args);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static java.lang.instrument.Instrumentation getInstrumentation() {
        // Falls kein Agent geladen ist, nutzen wir den Fallback über Java Start-Argumente
        return null;
    }
}