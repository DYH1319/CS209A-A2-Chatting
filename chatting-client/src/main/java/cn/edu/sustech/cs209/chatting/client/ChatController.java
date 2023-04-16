package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

public class ChatController implements Initializable {
    @FXML
    private Label currentOnlineCnt;
    @FXML
    private Label currentUsername;
    @FXML
    private ListView<Message> chatContentList;
    private String name;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    public void setSocketAndStream(String name, Socket socket, DataInputStream dis, DataOutputStream dos) {
        this.name = name;
        this.socket = socket;
        this.dis = dis;
        this.dos = dos;
        Platform.runLater(() -> currentUsername.setText("Current User:" + name));
        new Thread(new ReadFromServer()).start();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        chatContentList.setCellFactory(new MessageCellFactory());
    }
    
    private class ReadFromServer implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    int flag = dis.readInt();
                    switch (flag) {
                        // 0: Update online number
                        case 0:
                            updateOnlineCount(dis.readInt());
                            break;
                    }
                } catch (IOException e) {
                    serverOffline();
                    return;
                }
            }
        }
    }
    
    public void serverOffline() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Server is offline. Program will be exit.");
            alert.setContentText(null);
            alert.showAndWait();
            Platform.exit();
        });
    }
    
    public void updateOnlineCount(int onlineCount) {
        Platform.runLater(() -> currentOnlineCnt.setText("Online: " + onlineCount));
    }
    
    @FXML
    public void createPrivateChat() {
        AtomicReference<String> user = new AtomicReference<>();
        
        Stage stage = new Stage();
        ComboBox<String> userSel = new ComboBox<>();
        
        // FIXME: get the user list from server, the current user's name should be filtered out
        userSel.getItems().addAll("Item 1", "Item 2", "Item 3");
        
        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            user.set(userSel.getSelectionModel().getSelectedItem());
            stage.close();
        });
        
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(userSel, okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();
        
        // TODO: if the current user already chatted with the selected user, just open the chat with that user
        // TODO: otherwise, create a new chat item in the left panel, the title should be the selected user's name
    }
    
    /**
     * A new dialog should contain a multi-select list, showing all user's name.
     * You can select several users that will be joined in the group chat, including yourself.
     * <p>
     * The naming rule for group chats is similar to WeChat:
     * If there are > 3 users: display the first three usernames, sorted in lexicographic order, then use ellipsis with the number of users, for example:
     * UserA, UserB, UserC... (10)
     * If there are <= 3 users: do not display the ellipsis, for example:
     * UserA, UserB (2)
     */
    @FXML
    public void createGroupChat() {
    }
    
    /**
     * Sends the message to the <b>currently selected</b> chat.
     * <p>
     * Blank messages are not allowed.
     * After sending the message, you should clear the text input field.
     */
    @FXML
    public void doSendMessage() {
        // TODO
    }
    
    /**
     * You may change the cell factory if you changed the design of {@code Message} model.
     * Hint: you may also define a cell factory for the chats displayed in the left panel, or simply override the toString method.
     */
    private class MessageCellFactory implements Callback<ListView<Message>, ListCell<Message>> {
        @Override
        public ListCell<Message> call(ListView<Message> param) {
            return new ListCell<Message>() {
                
                @Override
                public void updateItem(Message msg, boolean empty) {
                    super.updateItem(msg, empty);
                    if (empty || Objects.isNull(msg)) {
                        return;
                    }
                    
                    HBox wrapper = new HBox();
                    Label nameLabel = new Label(msg.getSentBy());
                    Label msgLabel = new Label(msg.getData());
                    
                    nameLabel.setPrefSize(50, 20);
                    nameLabel.setWrapText(true);
                    nameLabel.setStyle("-fx-border-color: black; -fx-border-width: 1px;");
                    
                    if (name.equals(msg.getSentBy())) {
                        wrapper.setAlignment(Pos.TOP_RIGHT);
                        wrapper.getChildren().addAll(msgLabel, nameLabel);
                        msgLabel.setPadding(new Insets(0, 20, 0, 0));
                    } else {
                        wrapper.setAlignment(Pos.TOP_LEFT);
                        wrapper.getChildren().addAll(nameLabel, msgLabel);
                        msgLabel.setPadding(new Insets(0, 0, 0, 20));
                    }
                    
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(wrapper);
                }
            };
        }
    }
}
