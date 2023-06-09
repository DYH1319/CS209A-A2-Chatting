package cn.edu.sustech.cs209.chatting.server;

import cn.edu.sustech.cs209.chatting.common.FileTransport;
import cn.edu.sustech.cs209.chatting.common.Message;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

public class Main {
    
    private static final Map<String, Socket> onlineList = new HashMap<>();
    private static final Map<String, ObjectOutputStream> ooss = new HashMap<>();
    private static PreparedStatement ps;
    private static Statement statement;
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
            try {
                con = DriverManager.getConnection("jdbc:sqlite:file:chatting-server\\src\\main\\resources\\data_server.db?mode=rw");
            } catch (SQLException e) {
                String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                try {
                    path = URLDecoder.decode(path, "UTF-8");
                } catch (UnsupportedEncodingException ignore) {
                }
                File jarFile = new File(path);
                con = DriverManager.getConnection("jdbc:sqlite:file:" + jarFile.getParentFile().getPath() + File.separator + "data_server.db?mode=rw");
            }
        } catch (SQLException e) {
            System.err.println("Database connection failed");
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
    
    public static void main(String[] args) {
        try (ServerSocket ss = new ServerSocket(8520)) {
            System.out.println("-- Starting server -- Current time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
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
                            System.out.println(socket.getInetAddress() + ":" + socket.getPort() + " 用户" + name + "成功注册");
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
                            user.stream().filter(u -> !u.equals(name)).forEach(u -> {
                                ObjectOutputStream dest = ooss.get(u);
                                try {
                                    dest.writeInt(2);
                                    dest.writeUTF(name);
                                    dest.writeUTF(groupName);
                                    dest.writeUTF(groupMember);
                                    dest.writeObject(user);
                                    dest.flush();
                                } catch (IOException e) {
                                    userOffline(u);
                                }
                            });
                            break;
                        }
                        // 4: private message
                        case 4: {
                            sendPrivateMessage(-1);
                            break;
                        }
                        // 5: group message
                        case 5: {
                            sendGroupMessage(-1);
                            break;
                        }
                        // 6: Client has already to receiver
                        case 6: {
                            sendOfflineMessage(name);
                            break;
                        }
                        // 7: Private File
                        case 7: {
                            int fileId = saveFile();
                            sendPrivateMessage(fileId);
                            break;
                        }
                        // 8: Group File
                        case 8: {
                            int fileId = saveFile();
                            sendGroupMessage(fileId);
                            break;
                        }
                        // 9: Download file
                        case 9: {
                            int fileId = ois.readInt();
                            
                            sql = "select * from file where id = ?";
                            ps = con.prepareStatement(sql);
                            ps.setInt(1, fileId);
                            rs = ps.executeQuery();
                            
                            FileTransport file = null;
                            if (rs.next()) {
                                file = new FileTransport(fileId, rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5));
                            }
                            
                            oos.writeInt(6);
                            if (file == null)
                                oos.writeBoolean(true);
                            else {
                                oos.writeBoolean(false);
                                oos.writeUTF(file.getName());
                                oos.writeUTF(file.getType());
                                oos.flush();
                                if (ois.readBoolean()) {
                                    oos.writeObject(file);
                                }
                            }
                            oos.flush();
                            
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
        
        private int saveFile() throws IOException, ClassNotFoundException, SQLException {
            FileTransport file = (FileTransport) ois.readObject();
    
            sql = "insert into file (file_name, file_type, file_size, file_data) VALUES (?, ?, ?, ?);";
            ps = con.prepareStatement(sql);
            ps.setString(1, file.getName());
            ps.setString(2, file.getType());
            ps.setLong(3, file.getSize());
            ps.setString(4, file.getData());
            ps.executeUpdate();
    
            sql = "select id from file order by id desc limit 1;";
            statement = con.createStatement();
            rs = statement.executeQuery(sql);
            if (rs.next()) return rs.getInt(1);
            return -1;
        }
        
        private void sendPrivateMessage(int fileId) throws IOException, ClassNotFoundException, SQLException {
            Message message = (Message) ois.readObject();
            if (fileId != -1) {
                message.setFileId(fileId);
                oos.writeInt(5);
                oos.writeObject(message);
                oos.flush();
            }
            ObjectOutputStream dest = ooss.get(message.getSendTo());
            try {
                // if dest is offline, dest is null
                dest.writeInt(3);
                dest.writeObject(message);
                dest.flush();
            } catch (IOException ex) {
                userOffline(message.getSendTo());
            } catch (NullPointerException handle) {
                // Server save the private message
                sql = "insert into message values (?, ?, ?, ?, ?, ?, ?, ?)";
                ps = con.prepareStatement(sql);
                ps.setString(1, message.getSendTo());
                ps.setString(2, message.getSentBy());
                ps.setString(3, message.getSendTo());
                ps.setString(4, null);
                ps.setString(5, message.getData());
                ps.setInt(6, message.getFileId());
                ps.setBoolean(7, true);
                ps.setString(8, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(message.getTimestamp()));
                ps.executeUpdate();
            }
        }
        
        private void sendGroupMessage(int fileId) throws IOException, ClassNotFoundException {
            Message message = (Message) ois.readObject();
            if (fileId != -1) {
                message.setFileId(fileId);
                oos.writeInt(5);
                oos.writeObject(message);
                oos.flush();
            }
            message.getSendTos().stream().filter(e -> !e.equals(name)).forEach(e -> {
                ObjectOutputStream dest = ooss.get(e);
                try {
                    dest.writeInt(4);
                    dest.writeObject(message);
                    dest.flush();
                } catch (IOException ex) {
                    userOffline(e);
                } catch (NullPointerException handle) {
                    try {
                        // Server save the group message
                        System.out.println("服务器存储了一条群聊离线消息");
                        sql = "insert into message values (?, ?, ?, ?, ?, ?, ?)";
                        ps = con.prepareStatement(sql);
                        ps.setString(1, e);
                        ps.setString(2, message.getSentBy());
                        ps.setString(3, message.getSendTo());
                        String temp = message.getSendTos().toString();
                        ps.setString(4, temp.substring(0, temp.length() - 1).substring(1));
                        ps.setString(5, message.getData());
                        ps.setInt(6, message.getFileId());
                        ps.setBoolean(7, false);
                        ps.setString(8, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(message.getTimestamp()));
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
        
        private void sendOfflineMessage(String currentClient) throws SQLException, IOException {
            sql = "select * from message where current_client = ?";
            ps = con.prepareStatement(sql);
            ps.setString(1, currentClient);
            rs = ps.executeQuery();
            while (rs.next()) {
                ObjectOutputStream dest = ooss.get(currentClient);
                if (rs.getBoolean(7)) {
                    Message message = new Message(rs.getString(2), rs.getString(3), rs.getString(5), rs.getString(8));
                    message.setFileId(rs.getInt(6));
                    dest.writeInt(3);
                    dest.writeObject(message);
                    dest.flush();
                } else {
                    Message message = new Message(rs.getString(2), rs.getString(3), Arrays.stream(rs.getString(4).split(", ")).collect(Collectors.toList()), rs.getString(5), rs.getString(8));
                    message.setFileId(rs.getInt(6));
                    dest.writeInt(4);
                    dest.writeObject(message);
                    dest.flush();
                }
            }
            
            sql = "delete from message where current_client = ?";
            ps = con.prepareStatement(sql);
            ps.setString(1, currentClient);
            ps.executeUpdate();
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