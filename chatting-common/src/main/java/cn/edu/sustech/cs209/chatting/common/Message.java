package cn.edu.sustech.cs209.chatting.common;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class Message implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private Date timestamp;
    private String sentBy;
    private String sendTo;
    private String data;
    private List<String> sendTos;
    
    public Message(String sentBy, String sendTo, String data) {
        this.timestamp = new Date();
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.data = data;
    }
    
    public Message(String sentBy, String sendTo, List<String> sendTos, String data) {
        this.timestamp = new Date();
        this.sentBy = sentBy;
        this.sendTo = sendTo;
        this.sendTos = sendTos;
        this.data = data;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getSentBy() {
        return sentBy;
    }

    public String getSendTo() {
        return sendTo;
    }

    public String getData() {
        return data;
    }
    
    public List<String> getSendTos() {
        return sendTos;
    }
}
