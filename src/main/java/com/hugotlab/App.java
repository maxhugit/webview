package com.hugotlab;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebErrorEvent;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;

public class App extends Application {

    private HttpProxyServer proxyServer;

    private String proxyAddress = "localhost";
    private String proxyPort = "8182";

    @Override
    public void start(Stage primaryStage) {

        // Créer une zone de texte pour afficher les messages de débogage
        TextArea debugArea = new TextArea();
        debugArea.setEditable(false);

        // Configurer le gestionnaire de cookies
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        // Démarrer le proxy local
        startProxyServer();

        // Add a shutdown hook to stop the proxy server when the application is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> proxyServer.stop()));

        // Configurer le proxy pour utiliser le proxy local
        System.setProperty("http.proxyHost", proxyAddress);
        System.setProperty("http.proxyPort", proxyPort);
        System.setProperty("https.proxyHost", proxyAddress);
        System.setProperty("https.proxyPort", proxyPort);

        // Créer un WebView
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // Définir l'agent utilisateur pour qu'il corresponde à celui de Microsoft Edge
        String edgeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.64";
        webEngine.setUserAgent(edgeUserAgent);

        // Charger une page web par défaut
        webEngine.load("https://www.google.com");

        // Créer une barre d'adresse
        TextField urlField = new TextField("https://www.google.com");
        Button goButton = new Button("Go");

        // Créer des champs pour l'adresse et le port du proxy
        TextField proxyAddressField = new TextField(proxyAddress);
        TextField proxyPortField = new TextField(proxyPort);
        Button applyProxyButton = new Button("Apply Proxy");

        

        // Ajouter un gestionnaire d'événements pour le bouton "Apply Proxy"
        applyProxyButton.setOnAction(e -> {
            String proxyAddress = proxyAddressField.getText();
            String proxyPort = proxyPortField.getText();
            System.setProperty("http.proxyHost", proxyAddress);
            System.setProperty("http.proxyPort", proxyPort);
            System.setProperty("https.proxyHost", proxyAddress);
            System.setProperty("https.proxyPort", proxyPort);
            debugArea.appendText("Proxy set to " + proxyAddress + ":" + proxyPort + "\n");
        });



        // Méthode pour charger l'URL
        Runnable loadUrl = () -> {
            String url = urlField.getText();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            webEngine.load(url);
            debugArea.appendText("Loading: " + url + "\n");
            displayCookies(debugArea, cookieManager);
        };

        // Ajouter un gestionnaire d'événements pour le bouton "Go"
        goButton.setOnAction(e -> loadUrl.run());

        // Ajouter un gestionnaire d'événements pour le TextField (touche Enter)
        urlField.setOnAction(e -> loadUrl.run());

        // Ajouter des écouteurs pour les événements de navigation
        webEngine.setOnStatusChanged((WebEvent<String> event) -> {
            debugArea.appendText("Status: " + event.getData() + "\n");
        });

        webEngine.setOnError((WebErrorEvent event) -> {
            debugArea.appendText("Error: " + event.getMessage() + "\n");
        });

        webEngine.getLoadWorker().exceptionProperty().addListener((obs, oldException, exception) -> {
            if (exception != null) {
                debugArea.appendText("Exception: " + exception.getMessage() + "\n");
            }
        });

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            switch (newState) {
                case SUCCEEDED:
                    debugArea.appendText("Page loaded successfully.\n");
                    displayCookies(debugArea, cookieManager);
                    break;
                case FAILED:
                    debugArea.appendText("Page failed to load.\n");
                    break;
                default:
                    break;
            }
        });

        // Créer une disposition pour la barre d'adresse
        HBox addressBar = new HBox(urlField, goButton);
        HBox.setHgrow(urlField, Priority.ALWAYS); // Permettre au TextField de prendre toute la largeur disponible

        // Créer une disposition pour les paramètres du proxy
        HBox proxyBar = new HBox(new Label("Proxy Address:"), proxyAddressField, new Label("Proxy Port:"), proxyPortField, applyProxyButton);
        HBox.setHgrow(proxyAddressField, Priority.ALWAYS);
        HBox.setHgrow(proxyPortField, Priority.ALWAYS);

        // Créer une disposition pour la zone de débogage et les paramètres du proxy
        VBox bottomBox = new VBox(debugArea, proxyBar);

        // Créer une disposition principale et ajouter les composants
        BorderPane root = new BorderPane();
        root.setTop(addressBar);
        root.setCenter(webView);
        root.setBottom(bottomBox);

        // Créer une scène et l'ajouter à la scène principale
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Simple JavaFX Browser");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startProxyServer() {
        try {
            HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap()
                    .withPort(Integer.parseInt(proxyPort));
            proxyServer = bootstrap.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        if (proxyServer != null) {
            proxyServer.stop();
        }
        super.stop();
    }

    private void displayCookies(TextArea debugArea, CookieManager cookieManager) {
        debugArea.appendText("Cookies:\n");
        Map<String, List<HttpCookie>> cookieStore = cookieManager.getCookieStore().getCookies().stream()
                .collect(java.util.stream.Collectors.groupingBy(HttpCookie::getDomain));
        for (Map.Entry<String, List<HttpCookie>> entry : cookieStore.entrySet()) {
            debugArea.appendText("Domain: " + entry.getKey() + "\n");
            for (HttpCookie cookie : entry.getValue()) {
                debugArea.appendText("  " + cookie + "\n");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
