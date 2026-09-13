package com.finsight.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Application entry point.
 *
 * <p>This class is intentionally minimal for the project-bootstrap milestone: it only proves
 * that the JavaFX toolkit starts and a window can be shown. No business logic belongs here —
 * UI code stays a thin layer over the application/service layer once that layer exists.
 */
public class FinSightApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane(new Label("FinSight"));
        primaryStage.setTitle("FinSight");
        primaryStage.setScene(new Scene(root, 640, 480));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
