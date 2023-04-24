package cn.edu.sustech.cs209.chatting.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Set;

public class LoginController implements Initializable {
    @FXML
    private TextField signUpName;
    @FXML
    private PasswordField signUpPwd;
    @FXML
    private Button signUpBtn;
    @FXML
    private TextField signInName;
    @FXML
    private PasswordField signInPwd;
    @FXML
    private Button signInBtn;
    private Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            try {
                socket = new Socket("127.0.0.1", 8520);
                System.out.println("成功连接本地服务器");
            } catch (IOException e) {
                socket = new Socket("43.139.17.93", 8520);
                System.out.println("成功连接远程服务器");
            }
            oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject("");
            ois = new ObjectInputStream(socket.getInputStream());
            ois.readObject();
            
            signUpBtn.setOnAction(event -> signUp(signUpName.getText(), signUpPwd.getText()));
            signInBtn.setOnAction(event -> signIn(signInName.getText(), signInPwd.getText()));
        } catch (IOException e) {
            serverOffline();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    private void signUp(String name, String password) {
        if (name.isEmpty() || password.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("用户名或密码不能为空，请重新输入");
                alert.setContentText(null);
                alert.showAndWait();
            });
            return;
        }
        try {
            boolean flag = false;
            if (!name.equals("Server")) { // Server是系统保留名，不能注册名为Server的用户
                oos.writeInt(0); // sign up request
                oos.writeUTF(name);
                oos.writeUTF(password);
                oos.flush();
                flag = ois.readBoolean();
            }
            if (flag) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Success");
                    alert.setHeaderText("注册成功，请登录");
                    alert.setContentText(null);
                    alert.showAndWait();
                    signUpName.clear();
                    signUpPwd.clear();
                });
            } else {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Fail");
                    alert.setHeaderText("用户名已被使用，请更换用户名再次注册");
                    alert.setContentText(null);
                    alert.showAndWait();
                    signUpPwd.clear();
                });
            }
        } catch (IOException e) {
            serverOffline();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void signIn(String name, String password) {
        if (name.isEmpty() || password.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("用户名或密码不能为空，请重新输入");
                alert.setContentText(null);
                alert.showAndWait();
            });
            return;
        }
        try {
            oos.writeInt(1); // sign in request
            oos.writeUTF(name);
            oos.writeUTF(password);
            oos.flush();
            
            int flag = ois.readInt();
            if (flag == 2) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
                    Scene chatScene = new Scene(loader.load());
                    ChatController chatController = loader.getController();
                    chatController.setSocketAndStream(name, socket, ois, oos, (Set<String>) ois.readObject());
                    Stage stage = (Stage) signInBtn.getScene().getWindow();
                    stage.hide();
                    stage.setScene(chatScene);
                    stage.show();
                    System.out.println("当前登录用户:" + name);
                } catch (IOException | ClassNotFoundException | ClassCastException e) {
                    e.printStackTrace();
                }
            } else {
                String[] msg = new String[]{"Fail"};
                switch (flag) {
                    case 0:
                        msg[0] = "用户名不存在，请先注册";
                        break;
                    case 1:
                        msg[0] = "当前用户已登录，不能同时重复登录";
                        break;
                    case 3:
                        msg[0] = "用户名或密码错误，请再次尝试";
                        break;
                }
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Fail");
                    alert.setHeaderText(msg[0]);
                    alert.setContentText(null);
                    alert.showAndWait();
                    signUpPwd.clear();
                });
            }
        } catch (IOException e) {
            serverOffline();
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
}
