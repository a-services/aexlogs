package com.exadel.aexlogs;

import static java.lang.System.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.Jsoup;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Analyze Standalone AEX logs from exc utility.
 */
public class Main {

    /* Signatures in log.
     */
    final String SIG_1 = "[com.exadel.appery.mobilesrv.api.security.filter.ApiKeyRequestFilter]";
    final String SIG_2 = "[com.exadel.appery.mobilesrv.api.runtime.ServiceRuntimeRestService]";
    final String SIG_3 = "[com.exadel.appery.mobilesrv.model.utils.LogHelper]";

    TimestampExtractor tse = new TimestampExtractor();

    /**
     * Collects requests mapped by `requestId`
     */
    Map<String, RequestLine> reqLines;
    
    /**
     * Opens special mode to collect request body.
     */
    boolean bodyMode;
    
    /**
     * Current request used to collect request body.
     */
    RequestLine curReq;
        
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            out.println("[ERROR] Parameters: inputFile outputFile");
            return;
        }
        new Main().main(args[0], args[1]);
    }

    /**
     * @param inputFile   File name in HTML format received from `exc`.
     * @param outputFile  File name in HTML format with information about 
     *                    REST requests passing through Standalone API Express.
     */
    void main(String inputFile, String outputFile) throws Exception {

        /* Load and parse file specified as command-line parameter.
         */
        out.println("-----------------------");
        out.println("Input file: " + inputFile);

        Document doc = Jsoup.parse(new File(inputFile), "UTF-8");
        Element block = doc.select("div.block").get(0);
        String[] lines = block.html().split("<br>");

        /* Extract request information from log
         */
        reqLines = new HashMap<>();
        bodyMode = false;
        for (String line : lines) {
            LogLine ll = extractLineNum(line);
            if (ll != null) {
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
        }
        
        /* Create requests list
         */
        Collection<RequestLine> aexRequests = reqLines.values();
        
        /* Generate report
         */
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(templateResolver);
        Context context = new Context();
        context.setVariable("aexRequests", aexRequests);
        String report = templateEngine.process("templates/aexlogs.html", context);
        
        /* Save output file
         */
        Files.write(Paths.get(outputFile), report.getBytes());
        out.println("Output file: " + outputFile);
        out.println("-----------------------");
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
            return;
        }
        msg = msg.substring(k);

        final String METHOD = " Method: ";
        final String URL = " Url: ";
        final String PARAM = " Parameter: ";
        final String START_BODY = "---- Start Body Request:";
        final String RESPONSE = " Response: ";
        
        if (msg.startsWith(METHOD)) {
            req.setMethod(msg.substring(METHOD.length()));
        } else 
        if (msg.startsWith(URL)) {
            req.setUrl(msg.substring(URL.length()));
        } else 
        if (msg.startsWith(PARAM)) {
            String p = msg.substring(PARAM.length());
            k = p.indexOf("=");
            if (k == -1) {
                return;
            }
            String name = p.substring(0,k);
            String value = p.substring(k+1);
            req.getParams().put(name, value);
        } else
        /*if (msg.endsWith(START_BODY)) {
            bodyMode = true;
            curReq = req;
        } else*/
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
        } catch (ParseException ex) {
            return;
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
    
    /**
     * Process special mode to collect request body.
     */
    void appendBody(LogLine ll) {

        final String END_BODY = "---- End Body Request";
        
        if (END_BODY.equals(ll.text)) {
            bodyMode = false;
            return;
        }
        
        if (curReq.getBody() == null) {
            curReq.setBody(new StringBuilder());
            curReq.getBody().append(ll.text);
        } else {
            curReq.getBody().append("\n").append(ll.text);
        }
    }

}
