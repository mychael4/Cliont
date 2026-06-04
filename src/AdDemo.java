import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class AdDemo extends Application {
    @Override
    public void start(Stage primaryStage) {
        // 1. WebView für die AdsJumbo-Anzeige erstellen
        WebView webView = new WebView();

        // 2. Größe exakt auf das 160x600 Skyscraper-Format einstellen
        webView.setPrefSize(160, 600);
        webView.setMaxSize(160, 600);
        webView.setMinSize(160, 600);

        // 3. Die direkte AdsJumbo-URL mit deiner ID und den Maßen laden
        String adUrl = "https://adsjumbo.com";
        webView.getEngine().load(adUrl);

        // 4. Layout organisieren (BorderPane eignet sich perfekt für Seitenbanner)
        BorderPane root = new BorderPane();

        // Platzhalter für deinen eigentlichen App-Inhalt in der Mitte
        StackPane appContent = new StackPane(new Label("Hier steht dein Programm-Inhalt"));
        root.setCenter(appContent);

        // Das Werbebanner wird fest am rechten Rand platziert
        root.setRight(webView);

        // 5. Fenster erstellen (Breite angepasst, damit Inhalt + 160px Banner Platz haben)
        Scene scene = new Scene(root, 800, 650);
        primaryStage.setTitle("Java App mit AdsJumbo Integration");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
