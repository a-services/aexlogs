package com.exadel.aexlogs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import static java.lang.System.*;

public class PostmanService {

    final String postmanExt = ".postman_collection.json";

    final String restSig = "/apiexpress-api/rest/";
    final String securitySig = "/apiexpress-api/security/";

    final String aexHostVar = "aex_host";
    final String aexKeyVar = "aex_key";

    public void saveRequests(List<RequestLine> aexRequests, String postmanCollectionFile) {
        Path outFile = Paths.get(postmanCollectionFile);
        String collName = outFile.getFileName().toString();
        if (collName.endsWith(postmanExt)) {
            collName = collName.substring(0, collName.length() - postmanExt.length());
        }

        JSONObject json = new JSONObject();
        JSONObject info = new JSONObject();
        JSONArray items = new JSONArray();
        json.put("info", info);
        info.put("name", collName);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        json.put("item", items);
        int count = 0;
        for (RequestLine req : aexRequests) {
            if (req.getUrl() == null) {
                continue;
            }

            JSONObject item = new JSONObject();
            items.put(item);
            count++;

            /* Set name
             */
            int securitySigPos = req.getUrl().indexOf(securitySig);
            int restSigPos = req.getUrl().indexOf(restSig);
            boolean isLogin = securitySigPos != -1;
            String name = req.getUrl();
            if (isLogin) {
                name = name.substring(securitySigPos + securitySig.length());
            } else {
                name = name.substring(restSigPos + restSig.length());
            }
            String reqName = String.format("%05d_%s", count, name);
            item.put("name", reqName);
            String sessionTokenVar = "token_" + req.getUser();
            if (isLogin) {
                item.put("event", wrapEvent(reqName, sessionTokenVar));
            }

            /* Set request
             */
            JSONObject request = new JSONObject();
            item.put("request", request);
            request.put("method", req.getMethod());

            /* Set header
             */
            JSONArray header = new JSONArray();
            header.put(wrapPair("Content-Type", "application/json"));
            if (!isLogin) {
                header.put(wrapPair("X-Appery-Session-Token", "{{" + sessionTokenVar + "}}"));
            }
            request.put("header", header);

            /* Set url
             */
            JSONObject url = new JSONObject();
            request.put("url", url);
            String rawUrl = req.getUrl();
            if (isLogin) {
                rawUrl = "{{" + aexHostVar + "}}" + rawUrl.substring(securitySigPos);
            } else {
                rawUrl = "{{" +aexHostVar + "}}" + rawUrl.substring(restSigPos);
            }
            url.put("raw", rawUrl);
            try {
                URI uri = new URI(req.getUrl());
                //url.put("protocol", uri.getScheme());

                JSONArray host = new JSONArray();
                url.put("host", host);
                host.put("{{" +aexHostVar + "}}");
                //host.putAll(uri.getHost().split("\\."));

                url.put("path", wrapPath(uri.getPath()));
                url.put("query", wrapQuery(req.getParams()));

            } catch (URISyntaxException e) {
                out.println("[WARN] Invalid URI: " + req.getUrl());
                out.println("       Reason: " + e.getMessage());
            }

            if (req.getBody() != null) {
                request.put("body", wrapBody(req.getBody().toString()));
            }
        }

        /* Save JSON
         */
        try {
            Files.writeString(outFile, json.toString());
        } catch (IOException e) {
            out.println("[WARN] Cannot write Postman file: " + postmanCollectionFile);
            out.println("       Reason: " + e.getMessage());
        }
    }

    JSONArray wrapEvent(String reqName, String sessionTokenVar) {
        JSONArray result = new JSONArray();
        JSONObject event = new JSONObject();
        result.put(event);
        event.put("listen", "test");
        JSONObject script = new JSONObject();
        event.put("script", script);
        JSONArray exec = new JSONArray();
        script.put("exec", exec);
        script.put("type", "text/javascript");
        exec.putAll(Arrays.asList(
            "pm.test(\"" + reqName + "\", function () {",
            "   pm.response.to.have.jsonBody(\"sessionToken\");",
            "   var token = pm.response.json().sessionToken",
            "   pm.globals.set(\"" + sessionTokenVar + "\", token);",
            "});"));
        return result;
    }

    JSONObject wrapBody(String body) {
        JSONObject result = new JSONObject();
        result.put("mode", "raw");
        result.put("raw", body);
        JSONObject options = new JSONObject();
        result.put("options", options);
        JSONObject raw = new JSONObject();
        options.put("raw", raw);
        raw.put("language", "json");
        return result;
    }

    JSONArray wrapPath(String urlPath) {
        JSONArray result = new JSONArray();
        if (urlPath.startsWith("/")) {
            urlPath = urlPath.substring(1, urlPath.length());
        }
        result.putAll(urlPath.split("/"));
        return result;
    }

    JSONArray wrapQuery(List<Param> query) {
        JSONArray result = new JSONArray();
        for (Param pair : query) {
            result.put(wrapPair(pair.getName(),
                    pair.getName().equals("apiKey") ? "{{" + aexKeyVar + "}}" :
                    pair.getValue()));
        }
        return result;
    }

    JSONObject wrapPair(String key, String value) {
        JSONObject result = new JSONObject();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

}
