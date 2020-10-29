package com.exadel.aexlogs;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RequestLine implements Comparable<RequestLine> {

    private int startLine;

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }
    private int endLine;
    private Date tstamp;
    private String id;
    private String projectId;
    private String method;
    private String url;

    private List<Param> params = new ArrayList<>();
    
    private StringBuilder body;
    private String response;
    
    private int millis;

    public Date getTstamp() {
        return tstamp;
    }

    public void setTstamp(Date tstamp) {
        this.tstamp = tstamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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
    
    public boolean isLoginUrl() {
        return url.endsWith("/apiexpress-api/security/login");
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<Param> getParams() {
        return params;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    public StringBuilder getBody() {
        return body;
    }

    public void setBody(StringBuilder body) {
        this.body = body;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public int getMillis() {
        return millis;
    }

    public void setMillis(int millis) {
        this.millis = millis;
    }

    @Override
    public int compareTo(RequestLine req) {
        return startLine - req.getStartLine();
    }
}
