import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.hansolo.medusa.Gauge;
import eu.hansolo.medusa.Gauge.SkinType;
import eu.hansolo.medusa.GaugeBuilder;
import eu.hansolo.medusa.Section;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ControllerGeneral {
    private Stage stage;
    private Scene scene;
    private Gauge gauge;

    @FXML private AnchorPane generalPane;
    @FXML private HBox batteryBox;
    @FXML private GridPane cellPane, profilePane;
    @FXML private Label maxVLabel, minVLabel, delVLabel, sumVLabel, avgVLabel, maxTLabel, avgTLabel;
    @FXML private Pane errorPane;
    @FXML private Label typeLabel, numCellLabel, ratioLabel, chargeLabel, drainLabel, capacityLabel;

    final private String[] screen = {"General", "Detail", "Profile"};
    private String currentScreen;

    private Excel excel;

    int numCell;
    private Hashtable<String, String> characteristics;

    public void initialize() {
        currentScreen = screen[0];

        initCharacteristics();
        updateCharacteristics();

        // Add SoC gauge on screenGeneral
        gauge = GaugeBuilder.create()
        .skinType(SkinType.BATTERY)
        .animated(true)
        .sectionsVisible(true)
        .sections(new Section(0, 10, Color.RED),
                    new Section(10, 20, Color.rgb(255,235,59)), //YELLOW
                    new Section(20, 100, Color.GREEN))
        .build();
        batteryBox.getChildren().add(gauge);

        // TODO: get original USB
        DataReader reader = new DataReader(this, Controller.getUSB());

        // Start reading data in separate thread
        Thread thread = new Thread(reader);
        thread.start();

        // Create excel
        file = new Excel();
    }

    public void back(ActionEvent e) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("ScreenMain.fxml"));
        stage = (Stage)((Node) e.getSource()).getScene().getWindow();
        scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    public void general(ActionEvent e) {
        currentScreen = screen[0];

        for (Node node : findNodesByClass(generalPane, "general")) {
            node.setVisible(true);
        }

        cellPane.setVisible(false);

        errorPane.setVisible(true);

        profilePane.setVisible(false);
    }

    public void detail(ActionEvent e) {
        currentScreen = screen[1];

        for (Node node : findNodesByClass(generalPane, "general")) {
            node.setVisible(false);  // Hide nodes on screenGeneral
        }

        cellPane.setVisible(true);

        errorPane.setVisible(true);

        profilePane.setVisible(false);
    }

    public void profile(ActionEvent e) {
        currentScreen = screen[2];

        for (Node node : findNodesByClass(generalPane, "general")) {
            node.setVisible(false);  // Hide nodes on screenGeneral
        }

        cellPane.setVisible(false);

        errorPane.setVisible(false);

        profilePane.setVisible(true);
    }

    public void processData(JSONArray dataArray, String timestamp) {
        int numCell = dataArray.length();
        characteristics.put("numCell", String.valueOf(numCell));

        // TODO: get battery characteristics
        // characteristics.put(...);

        // Append to excel
        try {
            file.write(dataArray, timestamp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // Only process data for current screen
            if (currentScreen == screen[0]) {
                dataScreenGeneral(dataArray);
            } else if (currentScreen == screen[1]) {
                dataScreenDetail(dataArray);
            } else {
                dataScreenProfile(dataArray);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // TODO: Refactor
    // Update screenGeneral
    private void dataScreenGeneral(JSONArray dataArray) throws JSONException {
        final double finaldelV, finalavgV, finalavgT;
        
        // Reinitialize data
        double maxV = 0;
        double minV = 0;
        double sumV = 0;
        double maxT = 0;
        double sumT = 0;

        for (int i = 0; i < numCell; i++) {
            JSONObject dataObject = dataArray.getJSONObject(i);

            double voltage = dataObject.optDouble("voltage", Double.NaN);
            double temperature = dataObject.optDouble("temperature", Double.NaN);
            
            if (i == 0) {
                // Dung luong
                int SOC = dataObject.optInt("SOC", 0);
                // Display dung luong
                Platform.runLater(() -> gauge.setValue(SOC));

                // System.out.println("Battery Level: " + SOC + "%");

                maxV = voltage;
                minV = voltage;
                maxT = temperature;
            }

            sumV += voltage;
            sumT += temperature;

            if (voltage > maxV) {
                maxV = voltage;
            } else if (voltage < minV) {
                minV = voltage;
            }

            if (temperature > maxT) {
                maxT = temperature;
            }
        }

        final double finalmaxV = maxV;
        final double finalminV = minV;
        final double finalsumV = sumV;
        finaldelV = maxV - minV;
        finalavgV = sumV / numCell;

        final double finalmaxT = maxT;
        finalavgT = sumT / numCell;

        // Display voltage and temperature data in ScreenGeneral
        Platform.runLater(() -> {
            maxVLabel.setText(String.format("%.2f", finalmaxV) + "V");
            minVLabel.setText(String.format("%.2f", finalminV) + "V");
            delVLabel.setText(String.format("%.2f", finaldelV) + "V");
            sumVLabel.setText(String.format("%.2f", finalsumV) + "V");
            avgVLabel.setText(String.format("%.2f", finalavgV) + "V");
            avgVLabel.setText(String.format("%.2f", finalavgV) + "V");
            
            maxTLabel.setText(String.format("%.2f", finalmaxT) + "°C");
            avgTLabel.setText(String.format("%.2f", finalavgT) + "°C");
        });
    }

    // Update screenDetail
    private void dataScreenDetail(JSONArray dataArray) throws JSONException {
        double maxV = 0;
        double minV = 0;
        double maxT = 0;

        List<Label> cellLabels = findLabels((Parent) cellPane);
        List<Node> imageViewNodes = findNodesByClass(cellPane, "detailImage");
        List<Node> dataBoxes = findNodesByClass(cellPane, "detailDataBox");

        // Find max min
        for (int i = 1; i <= numCell; i++) {
            JSONObject dataObject = dataArray.getJSONObject(i-1);

            double voltage = dataObject.optDouble("voltage", Double.NaN);
            double temperature = dataObject.optDouble("temperature", Double.NaN);

            if (voltage > maxV) {
                maxV = voltage;
            } else if (voltage < minV) {
                minV = voltage;
            }

            if (temperature > maxT) {
                maxT = temperature;
            }
        }

        for (int i = 1; i <= numCell; i++) {
            String state = "normal";

            JSONObject dataObject = dataArray.getJSONObject(i-1);

            double voltage = dataObject.optDouble("voltage", Double.NaN);
            double temperature = dataObject.optDouble("temperature", Double.NaN);

            int cellNo = i;

            // Update cell number label
            Platform.runLater(() -> cellLabels.get(cellNo-1).setText("Cell " + cellNo));

            if (temperature == maxT) {
                state = "hot";
            }

            if (voltage == maxV) {
                state = "max";
            } else if (voltage == minV) {
                state = "min";
            }

            updateCellImage((ImageView) imageViewNodes.get(cellNo-1), state);

            updateDataLabels((Parent) dataBoxes.get(cellNo-1), voltage, temperature, state);

            // System.out.println("Cell " + i + ": " + "Voltage: " + voltage + ", Temperature: " + temperature);
        }
    }

    private void dataScreenProfile(JSONArray dataArray) {
        Platform.runLater(() -> numCellLabel.setText(characteristics.get("numCell")));
    }

    private List<Node> findNodesByClass(Parent root, String className) {
        List<Node> matchingNodes = new ArrayList<>();
        for (Node node : root.getChildrenUnmodifiable()) {
          if (node.getStyleClass().contains(className)) {
            matchingNodes.add(node);
          }
          if (node instanceof Parent) {
            matchingNodes.addAll(findNodesByClass((Parent) node, className));
          }
        }
        return matchingNodes;
    }

    // not recursive
    private List<Label> findLabels(Parent root) {
        List<Label> labels = new ArrayList<>();
        for (Node node : root.getChildrenUnmodifiable()) {
          if (node instanceof Label) {
            labels.add((Label) node);
          }
        }
        return labels;
    }

    // TODO: test
    private void updateCellImage(ImageView node, String state) {
        String url = "images/" + state + ".png";

        Image image = new Image(getClass().getResourceAsStream(url));
        Platform.runLater(() -> node.setImage(image));
    }

    private void updateDataLabels(Parent box, double V, double T, String state) {
        List<Label> labels = findLabels(box);

        Platform.runLater(() -> {
            // Voltage label 2 decimal places
            labels.get(0).setText(String.format("%.2f", V) + "V");

            // Temperature label 1 decimal place
            labels.get(1).setText(String.format("%.1f", T) + "°C");

            // If cell is blue, make labels white
            if (state == "min") {
                labels.get(0).setTextFill(Color.WHITE);
                labels.get(1).setTextFill(Color.WHITE);
            } else {
                labels.get(0).setTextFill(Color.BLACK);
                labels.get(1).setTextFill(Color.BLACK);
            }
        });
    }

    // TODO: make button
    private void save(ActionEvent e) {
        excel.save();
    }

    private void initCharacteristics() {
        characteristics = new Hashtable<>();
        characteristics.put("type", "Lifepo4");
        characteristics.put("ratio", "20C");
        characteristics.put("charge", "15A");
        characteristics.put("drain", "560A");
        characteristics.put("capacity", "100Ah");
    }

    // Display battery characteristics
    private void updateCharacteristics() {
        Platform.runLater(() -> {
            typeLabel.setText(characteristics.get("type"));
            ratioLabel.setText(characteristics.get("ratio"));
            chargeLabel.setText(characteristics.get("charge"));
            drainLabel.setText(characteristics.get("drain"));
            capacityLabel.setText(characteristics.get("capacity"));
        });
    }
}
