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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

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
    private DataInputStream dis;
    private DataOutputStream dos;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            socket = new Socket("127.0.0.1", 8520);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            
            signUpBtn.setOnAction(event -> signUp(signUpName.getText(), signUpPwd.getText()));
            signInBtn.setOnAction(event -> signIn(signInName.getText(), signInPwd.getText()));
        } catch (IOException e) {
            serverOffline();
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
            dos.writeInt(0); // sign up request
            dos.writeUTF(name);
            dos.writeUTF(password);
            dos.flush();
            
            boolean flag = dis.readBoolean();
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
            dos.writeInt(1); // sign in request
            dos.writeUTF(name);
            dos.writeUTF(password);
            dos.flush();
            
            int flag = dis.readInt();
            if (flag == 2) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
                    Scene chatScene = new Scene(loader.load());
                    ChatController chatController = loader.getController();
                    chatController.setSocketAndStream(name, socket, dis, dos);
                    Stage stage = (Stage) signInBtn.getScene().getWindow();
                    stage.hide();
                    stage.setScene(chatScene);
                    stage.show();
                } catch (IOException e) {
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
