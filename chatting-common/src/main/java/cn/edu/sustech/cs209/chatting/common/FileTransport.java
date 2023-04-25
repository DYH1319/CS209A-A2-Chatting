package cn.edu.sustech.cs209.chatting.common;

import java.io.Serializable;

public class FileTransport implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String name;
    private String type;
    private long size;
    private String data;
    
    public FileTransport(int id, String name, String type, long size, String data) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.size = size;
        this.data = data;
    }
    
    public FileTransport(String name, String type, long size, String data) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.data = data;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public long getSize() {
        return size;
    }
    
    public String getData() {
        return data;
    }
}
