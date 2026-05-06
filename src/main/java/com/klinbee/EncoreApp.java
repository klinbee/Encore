package com.klinbee;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EncoreApp extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        MainController controller = new MainController();
        Scene scene = new Scene(controller.build(stage), 900, 600);
        stage.setTitle("Encore");
        stage.setScene(scene);
        stage.show();
    }
}