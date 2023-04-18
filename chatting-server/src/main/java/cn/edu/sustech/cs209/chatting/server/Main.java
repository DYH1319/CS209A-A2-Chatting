package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;

public class Main {
    
    private static final Map<String, Socket> onlineList = new HashMap<>();
    private static final Map<String, ObjectOutputStream> ooss = new HashMap<>();
    private static PreparedStatement ps;
    private static Connection con;
    private static ResultSet rs;
    private static String sql;
    
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            System.err.println("Cannot find the sqlite driver");
            System.exit(1);
        }
        
        try {
            String url = "jdbc:sqlite:chatting-server/src/main/resources/cn.edu.sustech.cs209.chatting.server/data.db";
            con = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.err.println("Database connection failed");
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
    
    public static void main(String[] args) {
        try (ServerSocket ss = new ServerSocket(8520)) {
            System.out.println("-- Starting server --");
            while (true) {
                Socket socket = ss.accept();
                new Thread(new ReadFromClient(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Server cannot start, please check and restart.");
        }
    }
    
    private static class ReadFromClient implements Runnable {
        private final Socket socket;
        private ObjectInputStream ois;
        private ObjectOutputStream oos;
        private String name;
        
        public ReadFromClient(Socket socket) {
            this.socket = socket;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            try {
                ois = new ObjectInputStream(socket.getInputStream());
                ois.readObject();
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject("");
                
                while (true) {
                    // 0: sign up; 1: sign in; 2: start a new private chat; 3: start a new group chat
                    int flag = ois.readInt();
                    switch (flag) {
                        // 0: sign up
                        case 0: {
                            String name = ois.readUTF();
                            String password = ois.readUTF();
                            sql = "select * from client where name = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            rs = ps.executeQuery();
                            if (rs.next()) {
                                oos.writeBoolean(false);
                                oos.flush();
                                break;
                            }
                            sql = "insert into client values (?, ?)";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            ps.setString(2, password);
                            ps.execute();
                            oos.writeBoolean(true);
                            oos.flush();
                            break;
                        }
                        // 1: sign in
                        case 1: {
                            String name = ois.readUTF();
                            String password = ois.readUTF();
                            sql = "select * from client where name = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            rs = ps.executeQuery();
                            if (!rs.next()) {
                                oos.writeInt(0); // 用户名不存在，请先注册
                                oos.flush();
                                break;
                            }
                            sql = "select * from client where name = ? and password = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            ps.setString(2, password);
                            rs = ps.executeQuery();
                            if (rs.next()) {
                                if (onlineList.containsKey(name)) {
                                    oos.writeInt(1); // 当前用户已登录，不能同时重复登录
                                    oos.flush();
                                    break;
                                }
                                oos.writeInt(2); // 成功登录
                                oos.writeObject(new HashSet<>(onlineList.keySet()));
                                oos.flush();
                                this.name = name;
                                System.out.println(socket.getInetAddress() + ":" + socket.getPort() + " 用户" + name + "成功登录");
                                onlineList.put(name, this.socket);
                                ooss.put(name, oos);
                                updateUserList(true, name);
                            } else {
                                oos.writeInt(3); // 用户名或密码错误
                                oos.flush();
                            }
                            break;
                        }
                        // 2: start a new private chat
                        case 2: {
                            ObjectOutputStream dest = ooss.get(ois.readUTF());
                            dest.writeInt(1);
                            dest.writeUTF(name);
                            dest.flush();
                            break;
                        }
                        // 3: start a new group chat
                        case 3: {
                            String groupName = ois.readUTF();
                            String groupMember = ois.readUTF();
                            List<String> user = (List<String>) ois.readObject();
                            user.remove(name);
                            user.forEach(u -> {
                                ObjectOutputStream dest = ooss.get(u);
                                try {
                                    dest.writeInt(2);
                                    dest.writeUTF(name);
                                    dest.writeUTF(groupName);
                                    dest.writeUTF(groupMember);
                                    dest.writeObject(user);
                                } catch (IOException e) {
                                    userOffline(name);
                                }
                            });
                            break;
                        }
                        // 4: 转发消息
                        case 4: {
                            
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                userOffline(name);
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        
        private void userOffline(String name) {
            if (onlineList.containsKey(name)) {
                System.out.println(socket.getInetAddress() + ":" + socket.getPort() + " 用户" + name + "退出登录");
                onlineList.remove(name);
                ooss.remove(name);
                updateUserList(false, name);
            }
        }
        
        private void updateUserList(boolean isAdd, String name) {
            ooss.forEach((k, e) -> {
                try {
                    e.writeInt(0);
                    e.writeInt(onlineList.size());
                    e.writeBoolean(isAdd);
                    e.writeUTF(name);
                    e.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        }
    }
}