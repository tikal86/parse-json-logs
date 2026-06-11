package nl.tikal.logs.parser.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class StatusPanel extends VBox {
    private static final StatusPanel INSTANCE = new StatusPanel();
    private final StringProperty status;
    private final Label statusLabel;

    private StatusPanel() {
        statusLabel = new Label("Ready. Click 'Load Log File' to start.");
        statusLabel.setPadding(new Insets(5));
        status = new SimpleStringProperty();
        status.addListener((ob, oldValue, newValue) -> statusLabel.setText(newValue));
    }


    public static void changeStatus(String newStatus) {
        INSTANCE.status.setValue(newStatus);
    }

    public static Label getInstance() {
        return INSTANCE.getStatusLabel();
    }

    private Label getStatusLabel() {
        return statusLabel;
    }
}
