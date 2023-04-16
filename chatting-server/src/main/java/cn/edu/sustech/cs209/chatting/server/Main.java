package cn.edu.sustech.cs209.chatting.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;

public class Main {
    
    private static final Map<Socket, String> onlineList = new HashMap<>();
    private static final Set<DataOutputStream> doss = new HashSet<>();
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
        private DataInputStream dis;
        private DataOutputStream dos;
        
        public ReadFromClient(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());
    
                while (true) {
                    // 0:sign up; 1:sign in
                    int flag = dis.readInt();
                    switch (flag) {
                        // sign up
                        case 0: {
                            String name = dis.readUTF();
                            String password = dis.readUTF();
                            sql = "select * from client where name = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            rs = ps.executeQuery();
                            if (rs.next()) {
                                dos.writeBoolean(false);
                                dos.flush();
                                break;
                            }
                            sql = "insert into client values (?, ?)";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            ps.setString(2, password);
                            ps.execute();
                            dos.writeBoolean(true);
                            dos.flush();
                            break;
                        }
                        // sign in
                        case 1: {
                            String name = dis.readUTF();
                            String password = dis.readUTF();
                            sql = "select * from client where name = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            rs = ps.executeQuery();
                            if (!rs.next()) {
                                dos.writeInt(0); // 用户名不存在，请先注册
                                dos.flush();
                                break;
                            }
                            sql = "select * from client where name = ? and password = ?";
                            ps = con.prepareStatement(sql);
                            ps.setString(1, name);
                            ps.setString(2, password);
                            rs = ps.executeQuery();
                            if (rs.next()) {
                                if (onlineList.containsValue(name)) {
                                    dos.writeInt(1); // 当前用户已登录，不能同时重复登录
                                    dos.flush();
                                    break;
                                }
                                dos.writeInt(2); // 成功登录
                                dos.flush();
                                System.out.println(socket.getInetAddress() + ":" + socket.getPort() + " 用户" + name +"成功登录");
                                onlineList.put(this.socket, name);
                                doss.add(dos);
                                updateUserList();
                            } else {
                                dos.writeInt(3); // 用户名或密码错误
                                dos.flush();
                            }
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(socket.getInetAddress() + ":" + socket.getPort() + " 用户" + onlineList.get(socket) + "退出登录");
                onlineList.remove(socket);
                doss.remove(dos);
                updateUserList();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        private void updateUserList() {
            doss.forEach(e -> {
                try {
                    e.writeInt(0);
                    e.writeInt(onlineList.size());
                    e.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        }
    }
}