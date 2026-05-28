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
        Scene scene = new Scene(loader.load(), 460, 580);

        MainController controller = loader.getController();
        stage.setOnCloseRequest(e -> controller.shutdown());

        stage.setTitle("Alarm Clock");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}