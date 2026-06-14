module org.example {
    // JavaFX modules required for UI and media
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.desktop;
    requires java.logging;

    opens org.example to javafx.fxml;
    exports org.example;
}