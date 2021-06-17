package com.exadel.aexlogs;

import static java.lang.System.*;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.Jsoup;

public class LineProcessor {

    /**
     * Opens special mode to collect request body.
     */
    boolean bodyMode = false;
    
    /* Signatures in log.
     */
    static final String SIG_1 = "[com.exadel.appery.mobilesrv.api.security.filter.ApiKeyRequestFilter]";
    static final String SIG_2 = "[com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService]";
    static final String SIG_3 = "[com.exadel.appery.mobilesrv.model.utils.LogHelper]";
    
    TimestampExtractor tse = new TimestampExtractor();

    Map<String, RequestLine> reqLines;

    /**
     * Current request used to collect request body.
     */
    RequestLine curReq;

    List<ExcFile> excFiles = new ArrayList<>();
    
    Map<Integer, LogLine> logLines = new HashMap<>();
    
    public LineProcessor(Map<String, RequestLine> reqLines) {
        this.reqLines = reqLines;
    }

    /* Find ExcFile that contains given line.
     */
    ExcFile findExcFile(int lno) {
        for (ExcFile ef: excFiles) {
            if (lno >= ef.getStartLine() && lno <= ef.getEndLine()) {
                return ef;
            }
        }
        return null;
    }

    void processHtmlFile(String inputFile) throws IOException {

        /* Load and parse file specified as command-line parameter.
         */
        out.println("Input HTML file: " + inputFile);

        Document doc = Jsoup.parse(new File(inputFile), "UTF-8");
        Element block = doc.select("div.block").get(0);
        String[] lines = block.html().split("<br>");
        ExcFile excFile = new ExcFile();
        excFile.setPath(inputFile);

        /* Extract request information from log
         */
        for (String line : lines) {
            LogLine ll = extractLineNum(line);
            if (ll != null) {
                logLines.put(ll.lno, ll);
                excFile.updateLineInfo(ll);
            }
        }

        /* Store file information
         */
        excFiles.add(excFile);
    }

    void processLogLines() {
        List<Integer> keys = new ArrayList<>(logLines.keySet());
        Collections.sort(keys);
        for (Integer key: keys) {
            processLogLine(logLines.get(key));
        }
    }

    void processLogLine(LogLine ll) {
        if (bodyMode) {
            appendBody(ll);
        } else
        if (ll.text.contains(SIG_1)) {
            openRequest(ll);
        } else if (ll.text.contains(SIG_2)) {
            updateRequest(ll);
        } else if (ll.text.contains(SIG_3)) {
            closeRequest(ll);
        }
    }

    /**
     * Parse first line that opens request.
     */
    void openRequest(LogLine ll) {
        Date d = tse.extractTimestamp(ll.text);
        if (d == null) {
            return;
        }
        final MessageFormat mf = new MessageFormat("{0} Starting request for project {1}");
        int k = ll.text.indexOf(") ");
        if (k == -1) {
            return;
        }
        try {
            String msg = ll.text.substring(k + 2);
            Object[] res = mf.parse(msg);
            String requestId = (String) res[0];
            String projectId = (String) res[1];
            RequestLine req = new RequestLine();
            req.setStartLine(ll.lno);
            req.setTstamp(d);
            req.setId(requestId.trim());
            req.setProjectId(projectId.trim());
            reqLines.put(req.getId(), req);
        } catch (ParseException ex) {
        }
    }

    /**
     * Parse line that updates request information.
     */
    void updateRequest(LogLine ll) {
        int k = ll.text.indexOf(") ");
        if (k == -1) {
            return;
        }
        String msg = ll.text.substring(k + 2);
        k = msg.indexOf(" ");
        if (k == -1) {
            return;
        }
        String requestId = msg.substring(0, k);
        RequestLine req = reqLines.get(requestId);
        if (req == null) {

            /* Request with this id not found, create a new one.
             */
            Date d = tse.extractTimestamp(ll.text);
            if (d == null) {
                return;
            }
            req = new RequestLine();
            req.setStartLine(ll.lno);
            req.setTstamp(d);
            req.setId(requestId.trim());
            reqLines.put(req.getId(), req);
        }
        msg = msg.substring(k);

        final String METHOD = " Method: ";
        final String URL = " Url: ";
        final String PARAM = " Parameter: ";
        final String START_BODY = "---- Start Body Request:";
        final String RESPONSE = " Response: ";
        final String USER = " User: ";

        if (msg.startsWith(METHOD)) {
            req.setMethod(msg.substring(METHOD.length()));
        } else
        if (msg.startsWith(URL)) {
            req.setUrl(msg.substring(URL.length()));
        } else
        if (msg.startsWith(USER)) {
            req.setUser(msg.substring(USER.length()));
        } else
        if (msg.startsWith(PARAM)) {
            String p = msg.substring(PARAM.length());
            k = p.indexOf("=");
            if (k == -1) {
                return;
            }
            String name = p.substring(0,k);
            String value = p.substring(k+1);
            req.getParams().add(new Param(name, value));
        } else
        if (msg.endsWith(START_BODY)) {
            bodyMode = true;
            curReq = req;
        } else
        if (msg.startsWith(RESPONSE)) {
            req.setResponse(msg.substring(RESPONSE.length()));
        }
    }

    /**
     * Parse last line that closes request.
     */
    void closeRequest(LogLine ll) {
        int k = ll.text.indexOf(") ");
        if (k == -1) {
            return;
        }
        String msg = ll.text.substring(k + 2);
        k = msg.indexOf(" ");
        if (k == -1) {
            return;
        }
        String requestId = msg.substring(0, k);
        RequestLine req = reqLines.get(requestId);
        if (req == null) {
            return;
        }
        msg = msg.substring(k);

        final MessageFormat mf = new MessageFormat(" Request. Finished execution of endpoint logic. Time spent {0} millis");
        try {
            Object[] res = mf.parse(msg);
            req.setMillis(Integer.parseInt((String) res[0]));
            req.setEndLine(ll.lno);
        } catch (ParseException ex) {
            return;
        }

    }
    /**
     * Process special mode to collect request body.
     */
    void appendBody(LogLine ll) {

        final String END_BODY = "---- End Body Request";

        if (ll.text.endsWith(END_BODY)) {
            bodyMode = false;

            if (curReq.isLoginUrl()) {
                JSONObject json = new JSONObject(curReq.getBody().toString());
                curReq.setUser(json.getString("username"));
            }

            return;
        }

        if (curReq.getBody() == null) {
            curReq.setBody(new StringBuilder());
            curReq.getBody().append(ll.text);
        } else {
            curReq.getBody().append("\n").append(ll.text);
        }
    }

    /**
     * Transform line with HTML markup into `(line number, message)` pair.
     */
    LogLine extractLineNum(String line) {
        final String SEP = ":</a>";
        final String CUR = "<span class=\"cur\">";
        int k = line.indexOf(SEP);
        if (k == -1) {
            return null;
        }
        int k2 = line.substring(0, k).lastIndexOf(">");
        if (k2 == -1) {
            return null;
        }
        LogLine result = new LogLine();
        result.lno = Integer.parseInt(line.substring(k2 + 1, k));
        result.text = line.substring(k + SEP.length()).trim();
        if (result.text.startsWith(CUR)) {
            result.text = result.text.substring(CUR.length(), result.text.length());
        }
        return result;
    }
}
