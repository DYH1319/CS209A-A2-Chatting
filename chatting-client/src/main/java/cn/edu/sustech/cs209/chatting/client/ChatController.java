package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.FileTransport;
import cn.edu.sustech.cs209.chatting.common.Message;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
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
    private Button sendBtn;
    @FXML
    private ListView<Chat> chatList;
    @FXML
    private ListView<Message> chatContentList;
    private ListView<String> userSel;
    private ObservableList<Chat> chats;
    private String name;
    private Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private String sql;
    private ResultSet rs;
    private Connection con;
    private Statement statement;
    private PreparedStatement ps;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        chatContentList.setCellFactory(e -> new MessageCellFactory());
        chats = FXCollections.observableArrayList(chat -> new Observable[]{chat.lastChatTimeProperty()});
        chatList.setCellFactory(e -> new ChatListCell());
        chatList.setItems(new SortedList<>(chats, Comparator.comparing(Chat::getLastChatTime).reversed()));
    }
    
    public void setSocketAndStream(String name, Socket socket, ObjectInputStream dis, ObjectOutputStream dos, Set<String> onlineList) {
        this.name = name;
        this.socket = socket;
        this.ois = dis;
        this.oos = dos;
        Platform.runLater(() -> {
            userSel = new ListView<>();
            currentUsername.setText("Current User:" + name);
            userSel.setItems(FXCollections.observableArrayList(new ArrayList<>(onlineList)));
        });
        new Thread(new ReadFromServer()).start();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            System.err.println("Cannot find the sqlite driver");
            System.exit(1);
        }
        
        try {
            try {
                con = DriverManager.getConnection("jdbc:sqlite:chatting-client\\src\\main\\resources\\data\\client_" + name + ".db");
            } catch (SQLException e) {
                String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                try {
                    path = URLDecoder.decode(path, "UTF-8");
                    File jarFile = new File(path);
                    File databaseFile = new File(jarFile.getParentFile().getPath() + File.separator + "data");
                    if (!databaseFile.exists()) {
                        databaseFile.mkdirs();
                    }
                    con = DriverManager.getConnection("jdbc:sqlite:" + databaseFile + File.separator + "client_" + name + ".db");
                } catch (UnsupportedEncodingException ignore) {
                }
            }
            
            sql = "create table if not exists message\n" +
                  "(\n" +
                  "    current_client TEXT,\n" +
                  "    send_by        TEXT,\n" +
                  "    send_to        TEXT,\n" +
                  "    message_data   TEXT,\n" +
                  "    file_id        int,\n" +
                  "    time_stamp     timestamp\n" +
                  ");";
            statement = con.createStatement();
            statement.execute(sql);
            
            sql = "create table if not exists chat\n" +
                  "(\n" +
                  "    my_name        text,\n" +
                  "    you_name       text,\n" +
                  "    chat_type      text,\n" +
                  "    group_members  text,\n" +
                  "    unread_count   int,\n" +
                  "    last_chat_time timestamp,\n" +
                  "    primary key (my_name, you_name)\n" +
                  ");";
            statement = con.createStatement();
            statement.execute(sql);
            
        } catch (SQLException e) {
            System.err.println("Database connection failed");
            System.err.println(e.getMessage());
            System.exit(1);
        }
        
        Platform.runLater(() -> chatList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            Stage stage = (Stage) inputArea.getScene().getWindow();
            if (newValue == null)
                stage.setTitle("Chatting Client");
            else if (newValue.chatType == ChatType.PRIVATE)
                stage.setTitle("Chatting Client - Private Chat - " + newValue.youName);
            else if (newValue.chatType == ChatType.GROUP)
                stage.setTitle("Chatting Client - Group Chat - " + newValue.youName);
            if (newValue == null) chatContentList.setItems(null);
            else {
                chatContentList.setItems(newValue.listView.getItems());
                updateDatabase(false, null, true, newValue.youName, false, true, newValue.getLastChatTime(), null, null);
            }
        }));
        
        // Read data from database
        Platform.runLater(() -> {
            try {
                sql = "select * from chat";
                statement = con.createStatement();
                rs = statement.executeQuery(sql);
                while (rs.next()) {
                    ListView<Message> listView = new ListView<>();
                    String youName = rs.getString(2);
                    if (rs.getString(3).equals(ChatType.PRIVATE.name())) {
                        sql = "select * from message where send_by = 'Server' and (message_data like ? or message_data like ?);";
                        ps = con.prepareStatement(sql);
                        ps.setString(1, "你对" + youName + "%");
                        ps.setString(2, youName + "对你%");
                        ResultSet rs2 = ps.executeQuery();
                        if (rs2.next())
                            listView.getItems().add(new Message("Server", name, rs2.getString(4), rs2.getString(6)));
                        
                        sql = "select * from message where (send_by = ? and send_to = ?) or (send_by = ? and send_to = ?);";
                        ps = con.prepareStatement(sql);
                        ps.setString(1, name);
                        ps.setString(2, youName);
                        ps.setString(3, youName);
                        ps.setString(4, name);
                        rs2 = ps.executeQuery();
                        while (rs2.next()) {
                            Message message = new Message(rs2.getString(2), rs2.getString(3), rs2.getString(4), rs2.getString(6));
                            message.setFileId(rs2.getInt(5));
                            listView.getItems().add(message);
                        }
                        if (userSel.getItems().contains(youName))
                            chats.add(new Chat(rs.getString(1), rs.getString(2), null, listView, ChatType.PRIVATE, rs.getInt(5), rs.getString(6), true));
                        else
                            chats.add(new Chat(rs.getString(1), rs.getString(2), null, listView, ChatType.PRIVATE, rs.getInt(5), rs.getString(6), false));
                    } else {
                        sql = "select * from message where send_by = 'Server' and (message_data like ?);";
                        ps = con.prepareStatement(sql);
                        List<String> list = Arrays.stream(rs.getString(4).split(", ")).collect(Collectors.toList());
                        list.add(name);
                        Collections.sort(list);
                        String temp = list.toString();
                        ps.setString(1, "%" + temp.substring(0, temp.length() - 1).substring(1));
                        ResultSet rs2 = ps.executeQuery();
                        if (rs2.next())
                            listView.getItems().add(new Message("Server", name, rs2.getString(4), rs2.getString(6)));
                        
                        sql = "select * from message where send_to = ?;";
                        ps = con.prepareStatement(sql);
                        ps.setString(1, youName);
                        rs2 = ps.executeQuery();
                        while (rs2.next()) {
                            Message message = new Message(rs2.getString(2), rs2.getString(3), rs2.getString(4), rs2.getString(6));
                            message.setFileId(rs2.getInt(5));
                            listView.getItems().add(message);
                        }
                        chats.add(new Chat(rs.getString(1), rs.getString(2), Arrays.stream(rs.getString(4).split(", ")).collect(Collectors.toList()), listView, ChatType.GROUP, rs.getInt(5), rs.getString(6), false));
                    }
                }
                oos.writeInt(6);
                oos.flush();
            } catch (SQLException | ParseException | IOException e) {
                e.printStackTrace();
            }
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
                            if (!chats.stream().map(c -> c.youName).collect(Collectors.toList()).contains(user[0])) {
                                Platform.runLater(() -> {
                                    ListView<Message> listView = new ListView<>();
                                    Message message = new Message("Server", name, user[0] + "对你发起了私聊");
                                    synchronized (this) {
                                        updateDatabase(true, message, true, user[0], true, false, message.getTimestamp(), ChatType.PRIVATE, null);
                                    }
                                    listView.getItems().add(message);
                                    Chat chat = new Chat(name, user[0], null, listView, ChatType.PRIVATE, 1, message.getTimestamp());
                                    chats.add(chat);
                                });
                            }
                            break;
                        }
                        // 2: Group chat
                        case 2: {
                            String inviteUser = ois.readUTF();
                            String groupName = ois.readUTF();
                            String groupMember = ois.readUTF();
                            List<String> user = (List<String>) ois.readObject();
                            user.remove(name);
                            if (!chats.stream().map(c -> c.youName).collect(Collectors.toList()).contains(groupName)) {
                                Platform.runLater(() -> {
                                    ListView<Message> listView = new ListView<>();
                                    Message message = new Message("Server", name, inviteUser + "邀请你加入了群聊，参与群聊的人有：" + groupMember);
                                    synchronized (this) {
                                        updateDatabase(true, message, true, groupName, true, false, message.getTimestamp(), ChatType.GROUP, user);
                                    }
                                    listView.getItems().add(message);
                                    Chat chat = new Chat(name, groupName, user, listView, ChatType.GROUP, 1, message.getTimestamp());
                                    chats.add(chat);
                                    chat.setUnread(1);
                                });
                            }
                            break;
                        }
                        // 3: Private message
                        case 3: {
                            Message message = (Message) ois.readObject();
                            synchronized (this) {
                                updateDatabase(true, message, true, message.getSentBy(), false, false, message.getTimestamp(), null, null);
                            }
                            Platform.runLater(() -> chats.get(chats.stream().map(c -> c.youName).collect(Collectors.toList()).indexOf(message.getSentBy())).listView.getItems().add(message));
                            break;
                        }
                        // 4: Group message
                        case 4: {
                            Message message = (Message) ois.readObject();
                            synchronized (this) {
                                updateDatabase(true, message, true, message.getSendTo(), false, false, message.getTimestamp(), null, null);
                            }
                            Platform.runLater(() -> chats.get(chats.stream().map(c -> c.youName).collect(Collectors.toList()).indexOf(message.getSendTo())).listView.getItems().add(message));
                            break;
                        }
                        // 5: Get message with file id
                        case 5: {
                            Message message = (Message) ois.readObject();
                            Chat chat = chatList.getSelectionModel().getSelectedItem();
                            updateDatabase(true, message, true, chat.youName, false, false, message.getTimestamp(), chat.chatType, chat.groupMembers);
                            Platform.runLater(() -> chatContentList.getItems().add(message));
                            break;
                        }
                        // 6: Download file
                        case 6: {
                            if (ois.readBoolean()) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.WARNING);
                                    alert.setTitle("无法下载");
                                    alert.setHeaderText("你所下载的文件已失效，无法下载");
                                    alert.setContentText(null);
                                    alert.showAndWait();
                                });
                                break;
                            }
                            
                            String fileName = ois.readUTF();
                            String fileType = ois.readUTF();
                            
                            CountDownLatch latch = new CountDownLatch(1);
                            
                            Platform.runLater(() -> {
                                FileChooser fileChooser = new FileChooser();
                                fileChooser.setTitle("Save File");
                                fileChooser.setInitialFileName(fileName);
                                fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter(fileType + "文件", ".*" + fileType));
                                File fileToSave = fileChooser.showSaveDialog(sendBtn.getScene().getWindow());
                                
                                if (fileToSave != null) {
                                    try {
                                        FileTransport file = null;
                                        try {
                                            oos.writeBoolean(true);
                                            oos.flush();
                                            file = (FileTransport) ois.readObject();
                                        } catch (IOException e) {
                                            serverOffline();
                                        }
                                        byte[] decodedFile = Base64.getDecoder().decode(Objects.requireNonNull(file).getData());
                                        Files.write(fileToSave.toPath(), decodedFile);
                                        
                                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                        alert.setTitle("文件下载完成");
                                        alert.setHeaderText("文件已成功下载到: " + fileToSave.getAbsolutePath());
                                        alert.setContentText(null);
                                        alert.showAndWait();
                                    } catch (IOException e) {
                                        try {
                                            oos.writeBoolean(false);
                                            oos.flush();
                                        } catch (IOException ex) {
                                            serverOffline();
                                        }
                                        Alert alert = new Alert(Alert.AlertType.WARNING);
                                        alert.setTitle("无法下载文件");
                                        alert.setHeaderText("选择的路径不存在、无法访问或无法写入");
                                        alert.setContentText(null);
                                        alert.showAndWait();
                                    } catch (ClassNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (NullPointerException e) {
                                        serverOffline();
                                    } finally {
                                        latch.countDown();
                                    }
                                } else {
                                    try {
                                        oos.writeBoolean(false);
                                        oos.flush();
                                    } catch (IOException ex) {
                                        serverOffline();
                                    } finally {
                                        latch.countDown();
                                    }
                                }
                            });
                            
                            try {
                                latch.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            
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
    
    private void serverOffline() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Server is offline. Program will be exit.");
            alert.setContentText(null);
            alert.showAndWait();
            Platform.exit();
        });
    }
    
    private void updateOnlineList(int onlineCount, boolean isAdd, String name) {
        Platform.runLater(() -> {
            currentOnlineCnt.setText("Online: " + onlineCount);
            if (!name.equals(this.name)) {
                if (isAdd) {
                    userSel.getItems().add(name);
                    int index;
                    if ((index = chats.stream().map(c -> c.youName).collect(Collectors.toList()).indexOf(name)) != -1) {
                        chatList.getItems().get(index).setOnline(true);
                    }
                } else {
                    userSel.getItems().remove(name);
                    int index;
                    if ((index = chats.stream().map(c -> c.youName).collect(Collectors.toList()).indexOf(name)) != -1) {
                        chatList.getItems().get(index).setOnline(false);
                    }
                }
            }
        });
    }
    
    private synchronized void updateDatabase(boolean updateDatabaseOfMessage, Message message, boolean updateDataBaseOfChat, String youName, boolean isCreating, boolean isReset, Date lastChatTime, ChatType chatType, List<String> groupMembers) {
        if (updateDatabaseOfMessage) updateDatabaseOfMessage(message);
        if (updateDataBaseOfChat)
            updateDataBaseOfChat(youName, isCreating, isReset, lastChatTime, chatType, groupMembers);
    }
    
    private synchronized void updateDatabaseOfMessage(Message message) {
        if (message == null) return;
        try {
            sql = "insert into message values (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(sql);
            ps.setString(1, name);
            ps.setString(2, message.getSentBy());
            ps.setString(3, message.getSendTo());
            ps.setString(4, message.getData());
            ps.setInt(5, message.getFileId());
            ps.setString(6, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(message.getTimestamp()));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private synchronized void updateDataBaseOfChat(String youName, boolean isCreating, boolean isReset, Date lastChatTime, ChatType chatType, List<String> groupMembers) {
        if (isCreating) {
            try {
                sql = "insert into chat values (?, ?, ?, ?, 1, ?)";
                ps = con.prepareStatement(sql);
                ps.setString(1, name);
                ps.setString(2, youName);
                ps.setString(3, chatType.name());
                if (groupMembers == null) {
                    ps.setString(4, null);
                } else {
                    String temp = groupMembers.toString();
                    ps.setString(4, temp.substring(0, temp.length() - 1).substring(1));
                }
                ps.setString(5, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(lastChatTime));
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            if (isReset) {
                try {
                    sql = "update chat set unread_count = 0 where my_name = ? and you_name = ?";
                    ps = con.prepareStatement(sql);
                    ps.setString(1, name);
                    ps.setString(2, youName);
                    ps.executeUpdate();
                    
                    updateChatList(youName, 0, lastChatTime, true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    int unread = 0;
                    if (chatList.getSelectionModel().getSelectedItem() == null || !chatList.getSelectionModel().getSelectedItem().youName.equals(youName)) {
                        sql = "select unread_count from chat where my_name = ? and you_name = ?";
                        ps = con.prepareStatement(sql);
                        ps.setString(1, name);
                        ps.setString(2, youName);
                        rs = ps.executeQuery();
                        if (rs.next()) unread = rs.getInt(1) + 1;
                    }
                    
                    sql = "update chat set unread_count = ?, last_chat_time = ? where my_name = ? and you_name = ?";
                    ps = con.prepareStatement(sql);
                    ps.setInt(1, unread);
                    ps.setString(2, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(lastChatTime));
                    ps.setString(3, name);
                    ps.setString(4, youName);
                    ps.executeUpdate();
                    
                    updateChatList(youName, unread, lastChatTime, false);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void updateChatList(String youName, int unread, Date lastChatTime, boolean isReset) {
        Platform.runLater(() -> {
            Chat chat = chatList.getItems().stream().filter(c -> c.youName.equals(youName)).collect(Collectors.toList()).get(0);
            if (isReset) {
                chat.setUnread(unread);
            } else {
                chat.setUnread(unread);
                chat.setLastChatTime(lastChatTime);
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
                    Message message = new Message("Server", name, "你对" + user.get() + "发起了私聊");
                    synchronized (this) {
                        updateDatabase(true, message, true, user.get(), true, false, message.getTimestamp(), ChatType.PRIVATE, null);
                    }
                    listView.getItems().add(message);
                    chats.add(new Chat(name, user.get(), null, listView, ChatType.PRIVATE, 0, message.getTimestamp()));
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
            
            Label label = new Label("请按住Ctrl键依次选择群聊用户");
            label.setStyle("-fx-text-fill: #c800ff; -fx-font-size: 24;");
            
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
                
                if (!this.chatList.getItems().stream().map(o -> o.youName).collect(Collectors.toList()).contains(groupName.toString())) {
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
                    Message message = new Message("Server", name, "你发起了群聊，参加群聊的人有：" + groupMember);
                    synchronized (this) {
                        updateDatabase(true, message, true, groupName.toString(), true, false, message.getTimestamp(), ChatType.GROUP, user);
                    }
                    listView.getItems().add(message);
                    chats.add(new Chat(name, groupName.toString(), user, listView, ChatType.GROUP, 1, message.getTimestamp()));
                }
                chatList.getSelectionModel().select(chatList.getItems().stream().map(o -> o.youName).collect(Collectors.toList()).indexOf(groupName.toString()));
            }
        } catch (IOException e) {
            serverOffline();
        }
    }
    
    @FXML
    public void doSendMessage() {
        try {
            String data = inputArea.getText();
            if (data == null || data.isEmpty()) return;
            Chat chat = chatList.getSelectionModel().getSelectedItem();
            if (chat == null) return;
            Message message;
            
            if (chat.chatType == ChatType.PRIVATE) {
                message = new Message(name, chat.youName, data);
                oos.writeInt(4);
            } else {
                message = new Message(name, chat.youName, chat.groupMembers, data);
                oos.writeInt(5);
            }
            oos.writeObject(message);
            oos.flush();
            
            updateDatabase(true, message, true, chat.youName, false, false, message.getTimestamp(), chat.chatType, chat.groupMembers);
            chatContentList.getItems().add(message);
            inputArea.clear();
        } catch (IOException e) {
            serverOffline();
        }
    }
    
    @FXML
    public void doSendFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a File");
        File selectedFile = fileChooser.showOpenDialog(sendBtn.getScene().getWindow());
        if (selectedFile != null) {
            try {
                Chat chat = chatList.getSelectionModel().getSelectedItem();
                if (chat == null) return;
                
                byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
                String fileName = selectedFile.getName();
                String fileExtension = "";
                
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0 && lastDot < fileName.length() - 1) {
                    fileExtension = fileName.substring(lastDot + 1).toLowerCase();
                }
                FileTransport file = new FileTransport(fileName, fileExtension, selectedFile.length(), encodedFile);
                
                Message message;
                
                try {
                    if (chat.chatType == ChatType.PRIVATE) {
                        message = new Message(name, chat.youName, String.format("%s (%s)", file.getName(), formatFileSize(file.getSize())));
                        oos.writeInt(7);
                    } else {
                        message = new Message(name, chat.youName, chat.groupMembers, String.format("%s (%s)", file.getName(), formatFileSize(file.getSize())));
                        oos.writeInt(8);
                    }
                    oos.writeObject(file);
                    oos.writeObject(message);
                    oos.flush();
                    
                } catch (IOException e) {
                    serverOffline();
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("无法上传文件");
                    alert.setHeaderText("选择的文件不存在或不可读");
                    alert.setContentText(null);
                    alert.showAndWait();
                });
            }
        }
    }
    
    @FXML
    public void doSendEmoji() {
        String[] emojis = {"😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "😇", "😍", "😘", "😗", "😙", "😚", "😋", "😛", "😜", "😝", "😞", "😟", "😠", "😡", "😢", "😣"};
        Random random = new Random();
        String emoji = emojis[random.nextInt(emojis.length)];
        Platform.runLater(() -> inputArea.appendText(emoji));
    }
    
    private String formatFileSize(long fileSize) {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1048576) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else {
            return String.format("%.2f MB", fileSize / 1048576.0);
        }
    }
    
    private class MessageCellFactory extends ListCell<Message> {
        @Override
        protected void updateItem(Message msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || Objects.isNull(msg)) {
                setText(null);
                setGraphic(null);
                return;
            }
            
            HBox wrapper = new HBox();
            Label nameLabel = new Label(msg.getSentBy());
            Label msgLabel = new Label(msg.getData());
            
            Button downloadBtn = new Button("Download");
            downloadBtn.setOnAction(event -> {
                try {
                    oos.writeInt(9);
                    oos.writeInt(msg.getFileId());
                    oos.flush();
                } catch (IOException e) {
                    serverOffline();
                }
            });
            
            wrapper.setSpacing(5);
            nameLabel.setPrefSize(70, 20);
            nameLabel.setWrapText(true);
            nameLabel.setStyle("-fx-border-color: #ff8000; -fx-border-width: 1px;");
            
            if (name.equals(msg.getSentBy())) {
                wrapper.setAlignment(Pos.TOP_RIGHT);
                if (msg.getFileId() != -1) {
                    wrapper.getChildren().addAll(msgLabel, downloadBtn, nameLabel);
                } else {
                    wrapper.getChildren().addAll(msgLabel, nameLabel);
                }
                msgLabel.setPadding(new Insets(0, 20, 0, 0));
            } else {
                wrapper.setAlignment(Pos.TOP_LEFT);
                if (msg.getFileId() != -1) {
                    wrapper.getChildren().addAll(nameLabel, downloadBtn, msgLabel);
                } else {
                    wrapper.getChildren().addAll(nameLabel, msgLabel);
                }
                msgLabel.setPadding(new Insets(0, 0, 0, 20));
            }
            
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(wrapper);
        }
    }
    
    private static class ChatListCell extends ListCell<Chat> {
        @Override
        protected void updateItem(Chat chat, boolean empty) {
            super.updateItem(chat, empty);
            Label unreadCountLabel = new Label();
            Label lastMessageTimeLabel = new Label();
            Circle onlineState = new Circle(6);
            
            if (empty || chat == null) {
                unreadCountLabel.textProperty().unbind();
                lastMessageTimeLabel.textProperty().unbind();
                onlineState.fillProperty().unbind();
                setGraphic(null);
                setText(null);
            } else {
                unreadCountLabel.textProperty().bind(Bindings.format("%d", chat.unreadProperty()));
//                lastMessageTimeLabel.textProperty().bind(Bindings.format("%s", formatTime(LocalDateTime.parse(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(chat.lastChatTimeProperty().get()), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")))));
                lastMessageTimeLabel.textProperty().bind(Bindings.createStringBinding(() -> formatTime(LocalDateTime.parse(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(chat.getLastChatTime()), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))), chat.lastChatTimeProperty()));
                if (chat.chatType == ChatType.PRIVATE)
                    onlineState.fillProperty().bind(Bindings.when(chat.online).then(Color.GREEN).otherwise(Color.GRAY));
                else
                    onlineState.setFill(Color.TRANSPARENT);
                
                HBox box = new HBox(10);
                HBox hBox = new HBox();
                VBox vBox = new VBox();
                Label chatNameLabel = new Label(" " + chat.youName);
                
                Circle redCircle = new Circle(8, Color.RED);
                unreadCountLabel.setTextFill(Color.WHITE);
                
                StackPane stackPane = new StackPane(redCircle, unreadCountLabel);
                
                BooleanBinding unreadCountGreaterThanZero = chat.unread.greaterThan(0);
                stackPane.visibleProperty().bind(unreadCountGreaterThanZero);
                
                hBox.getChildren().addAll(onlineState, chatNameLabel);
                hBox.setAlignment(Pos.CENTER_LEFT);
                vBox.getChildren().addAll(lastMessageTimeLabel, stackPane);
                box.getChildren().addAll(hBox, vBox);
                box.setAlignment(Pos.CENTER_LEFT);
                
                HBox.setHgrow(hBox, Priority.ALWAYS);
                
                setGraphic(box);
            }
        }
        
        private String formatTime(LocalDateTime dateTime) {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate oneYearAgo = today.minusYears(1);
            
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter yesterdayFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("MM-dd");
            DateTimeFormatter yearMonthDayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            if (dateTime.toLocalDate().isEqual(today)) {
                return dateTime.format(timeFormatter);
            } else if (dateTime.toLocalDate().isEqual(yesterday)) {
                return "昨天 " + dateTime.format(yesterdayFormatter);
            } else if (dateTime.toLocalDate().isAfter(oneYearAgo)) {
                return dateTime.format(monthDayFormatter);
            } else {
                return dateTime.format(yearMonthDayFormatter);
            }
        }
    }
    
    private static class Chat {
        private String myName;
        private String youName;
        private ChatType chatType;
        private List<String> groupMembers;
        private ListView<Message> listView;
        private BooleanProperty online;
        private IntegerProperty unread;
        private ObjectProperty<Date> lastChatTime;
        
        public Chat(String myName, String youName, List<String> groupMembers, ListView<Message> listView, ChatType chatType, int unread, Date createTime) {
            this.myName = myName;
            this.youName = youName;
            this.listView = listView;
            this.chatType = chatType;
            this.groupMembers = groupMembers;
            this.online = new SimpleBooleanProperty(true);
            this.unread = new SimpleIntegerProperty(unread);
            this.lastChatTime = new SimpleObjectProperty<>(createTime);
        }
        
        public Chat(String myName, String youName, List<String> groupMembers, ListView<Message> listView, ChatType chatType, int unread, String createTime, boolean online) throws ParseException {
            this.myName = myName;
            this.youName = youName;
            this.listView = listView;
            this.chatType = chatType;
            this.groupMembers = groupMembers;
            this.online = new SimpleBooleanProperty(online);
            this.unread = new SimpleIntegerProperty(unread);
            this.lastChatTime = new SimpleObjectProperty<>(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(createTime));
        }
        
        public boolean isOnline() {
            return online.get();
        }
        
        public BooleanProperty onlineProperty() {
            return online;
        }
        
        public int getUnread() {
            return unread.get();
        }
        
        public IntegerProperty unreadProperty() {
            return unread;
        }
        
        public Date getLastChatTime() {
            return lastChatTime.get();
        }
        
        public ObjectProperty<Date> lastChatTimeProperty() {
            return lastChatTime;
        }
        
        public void setOnline(boolean online) {
            this.online.set(online);
        }
        
        public void setUnread(int unread) {
            this.unread.set(unread);
        }
        
        public void setLastChatTime(Date lastChatTime) {
            this.lastChatTime.set(lastChatTime);
        }
        
        @Override
        public String toString() {
            return this.youName;
        }
    }
}
