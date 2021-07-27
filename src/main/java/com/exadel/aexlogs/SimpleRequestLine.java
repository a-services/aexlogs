package com.exadel.aexlogs;

import java.util.Date;

public class SimpleRequestLine {
    
    private Date tstamp;
    private String method;
    private String url;
    private String user;
    private int millis;

    public SimpleRequestLine(RequestLine it) {
        this.tstamp = it.getTstamp();
        this.method = it.getMethod();
        this.url = it.getUrl();
        this.user = it.getUser();
        this.millis = it.getMillis();
    }

    public Date getTstamp() {
        return tstamp;
    }

    public void setTstamp(Date tstamp) {
        this.tstamp = tstamp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getMillis() {
        return millis;
    }

    public void setMillis(int millis) {
        this.millis = millis;
    }
}
