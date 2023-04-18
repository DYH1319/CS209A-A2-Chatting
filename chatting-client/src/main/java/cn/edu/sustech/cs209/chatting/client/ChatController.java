package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Message;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ChatController implements Initializable {
    @FXML
    private Label currentOnlineCnt;
    @FXML
    private Label currentUsername;
    @FXML
    private TextArea inputArea;
    @FXML
    private ListView<Chat> chatList;
    @FXML
    private ListView<Message> chatContentList;
    private ListView<String> userSel;
    private String name;
    private Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    
    public void setSocketAndStream(String name, Socket socket, ObjectInputStream dis, ObjectOutputStream dos, Set<String> onlineList) {
        this.name = name;
        this.socket = socket;
        this.ois = dis;
        this.oos = dos;
        Platform.runLater(() -> {
            userSel = new ListView<>();
//            userSel.setCellFactory(e -> new SelectListCell());
            currentUsername.setText("Current User:" + name);
            userSel.setItems(FXCollections.observableArrayList(new ArrayList<>(onlineList)));
        });
        new Thread(new ReadFromServer()).start();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        chatContentList.setCellFactory(new MessageCellFactory());
        chatList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            Stage stage = (Stage) chatList.getScene().getWindow();
            stage.setTitle("Chatting Client - Private Chat - " + newValue.youName);
            chatContentList.setItems(newValue.listView.getItems());
        });
    }
    
    @SuppressWarnings("unchecked")
    private class ReadFromServer implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    int flag = ois.readInt();
                    switch (flag) {
                        // 0: Update online users
                        case 0: {
                            updateOnlineList(ois.readInt(), ois.readBoolean(), ois.readUTF());
                            break;
                        }
                        // 1: Private chat
                        case 1: {
                            String[] user = new String[]{"null"};
                            user[0] = ois.readUTF();
                            Platform.runLater(() -> {
                                ListView<Message> listView = new ListView<>();
                                listView.getItems().add(new Message(10L, "Server", name, user[0] + "对你发起了私聊"));
                                chatList.getItems().add(new Chat(name, user[0], null, listView, ChatType.PRIVATE));
                            });
                            break;
                        }
                        // 2: Group chat
                        case 2: {
                            String inviteUser = ois.readUTF();
                            String groupName = ois.readUTF();
                            String groupMember = ois.readUTF();
                            List<String> user = (List<String>) ois.readObject();
                            user.remove(name);
                            Platform.runLater(() -> {
                                ListView<Message> listView = new ListView<>();
                                listView.getItems().add(new Message(10L, "Server", name, inviteUser + "邀请你加入了群聊，参与群聊的人有：" + groupMember));
                                chatList.getItems().add(new Chat(name, groupName, user, listView, ChatType.GROUP));
                            });
                            break;
                        }
                    }
                } catch (IOException e) {
                    serverOffline();
                    return;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
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
    
    public void updateOnlineList(int onlineCount, boolean isAdd, String name) {
        Platform.runLater(() -> {
            currentOnlineCnt.setText("Online: " + onlineCount);
            if (!name.equals(this.name)) {
                if (isAdd) userSel.getItems().add(name);
                else userSel.getItems().remove(name);
            }
        });
    }
    
    @FXML
    public void createPrivateChat() {
        try {
            AtomicReference<String> user = new AtomicReference<>();
            
            Stage stage = new Stage();
            stage.setTitle("New Private Chat");
            
            Platform.runLater(() -> userSel.getSelectionModel().setSelectionMode(SelectionMode.SINGLE));
            
            Button okBtn = new Button("OK");
            okBtn.setOnAction(e -> {
                user.set(userSel.getSelectionModel().getSelectedItem());
                stage.close();
            });
            
            VBox box = new VBox(25);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(40, 40, 40, 40));
            box.getChildren().addAll(userSel, okBtn);
            stage.setScene(new Scene(box));
            stage.showAndWait();
            
            if (user.get() != null && !user.get().isEmpty()) {
                if (!this.chatList.getItems().stream().map(o -> o.youName).collect(Collectors.toList()).contains(user.get())) {
                    oos.writeInt(2);
                    oos.writeUTF(user.get());
                    oos.flush();
                    ListView<Message> listView = new ListView<>();
                    listView.getItems().add(new Message(10L, "Server", name, "你对" + user.get() + "发起了私聊"));
                    chatList.getItems().add(new Chat(name, user.get(), null, listView, ChatType.PRIVATE));
                }
                chatList.getSelectionModel().select(chatList.getItems().stream().map(o -> o.youName).collect(Collectors.toList()).indexOf(user.get()));
            }
        } catch (IOException e) {
            serverOffline();
        }
    }
    
    @FXML
    public void createGroupChat() {
        try {
            ArrayList<String> user = new ArrayList<>();
            
            Label label = new Label("请按住Ctrl键依次群聊用户");
            label.setStyle("-fx-text-fill: #00ffff; -fx-font-size: 24;");
            
            Stage stage = new Stage();
            stage.setTitle("New Group Chat");
            
            userSel.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            
            Button okBtn = new Button("OK");
            okBtn.setOnAction(e -> {
                user.addAll(userSel.getSelectionModel().getSelectedItems());
                user.add(name);
                stage.close();
            });
            
            VBox box = new VBox(25);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(40, 40, 40, 40));
            box.getChildren().addAll(label, userSel, okBtn);
            stage.setScene(new Scene(box));
            stage.showAndWait();
            
            if (!user.isEmpty()) {
                StringBuilder groupName = new StringBuilder();
                Collections.sort(user);
                if (user.size() <= 3) {
                    user.forEach(u -> groupName.append(u).append(", "));
                    groupName.delete(groupName.length() - 2, groupName.length() - 1);
                    groupName.append("(").append(user.size()).append(")");
                } else {
                    user.stream().limit(3).forEach(u -> groupName.append(u).append(", "));
                    groupName.delete(groupName.length() - 2, groupName.length());
                    groupName.append("... (").append(user.size()).append(")");
                }
                
                StringBuilder groupMember = new StringBuilder();
                user.forEach(u -> groupMember.append(u).append(", "));
                groupMember.delete(groupMember.length() - 2, groupMember.length());
                
                oos.writeInt(3);
                oos.writeUTF(groupName.toString());
                oos.writeUTF(groupMember.toString());
                oos.writeObject(user);
                oos.flush();
                
                user.remove(name);
                ListView<Message> listView = new ListView<>();
                listView.getItems().add(new Message(10L, "Server", name, "你发起了群聊，参加群聊的人有：" + groupMember));
                chatList.getItems().add(new Chat(name, groupName.toString(), user, listView, ChatType.GROUP));
                chatList.getSelectionModel().select(chatList.getItems().stream().map(o -> o.youName).collect(Collectors.toList()).indexOf(groupName.toString()));
            }
        } catch (IOException e) {
            serverOffline();
        }
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
                    
                    if (currentUsername.getText().equals(msg.getSentBy())) {
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
    
    private static class Chat {
        private String myName;
        private String youName;
        private ChatType chatType;
        private List<String> groupMembers;
        private ListView<Message> listView;
        
        public Chat(String myName, String youName, List<String> groupMembers, ListView<Message> listView, ChatType chatType) {
            this.myName = myName;
            this.youName = youName;
            this.listView = listView;
            this.chatType = chatType;
            this.groupMembers = groupMembers;
        }
        
        @Override
        public String toString() {
            return this.youName;
        }
    }
}
