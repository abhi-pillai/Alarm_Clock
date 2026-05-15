package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/example/main.fxml"));
        Scene scene = new Scene(loader.load(), 420, 520);

        // Grab the controller so we can call shutdown() on close
        MainController controller = loader.getController();

        // Clean up background threads when window is closed
        stage.setOnCloseRequest(e -> controller.shutdown());

        stage.setTitle("Alarm Clock");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}