package com.exadel.aexlogs;

public class ExcFile {

    private String path;
    private int startLine = -1;
    private int endLine;

    public void setPath(String path) {
        this.path = path;
    }

    public void updateLineInfo(LogLine ll) {
        if (startLine == -1) {
            startLine = ll.lno;
        }
        endLine = ll.lno;
    }


    public String getPath() {
        return path;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }
    
}
