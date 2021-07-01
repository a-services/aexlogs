package com.exadel.aexlogs;

import static java.lang.System.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Analyze Standalone AEX logs from exc utility.
 */
@Command(name = "aexlogs", mixinStandardHelpOptions = true, version = "1.4")
public class Main implements Callable<Integer> {

    @Option(names = { "-f", "--file" },
            description = "Files for input in HTML format received from `exc`.")
    List<String> inputHtmlFiles;

    @Option(names = { "-p", "--plain" },
            description = "Plain log file for input.")
    String inputLogFile;

    @Option(names = { "-b", "--brief" },
            description = "Brief format for output.")
    boolean briefOutput;

    @Option(names = { "-g", "--group" },
            description = "Group requests within time intervals, ms.")
    Long groupMs;
    
    /*
    @Option(names = { "-c", "--chart" },
            description = "Create chart of given type (serverLoad, responseTime).")
    String createChart; 
    */

    @Option(names = { "-d", "--dir" },
            description = "Folder with files in HTML format received from `exc`.")
    String inputFolder;

    @Option(names = { "-r", "--rest" },
            description = "Track only specified REST requests.")
    List<String> filterRestServices;

    @Option(names = { "-u", "--user" }, 
            description = "Track only specified users.")
    List<String> filterUsers;

    @Option(names = { "-t1", "--timefrom" },
            description = "Track only after specified time in ISO format [yyyy-MM-ddTHH:mm:ss]")
    String filterFromTime;

    @Option(names = { "-t2", "--timeto" },
            description = "Track only before specified time in ISO format [yyyy-MM-ddTHH:mm:ss]")
    String filterToTime;

    @Parameters(index = "0", defaultValue = "aexlogs.html",
            description = "File name in HTML format "
            + "with information about REST requests passing through Standalone API Express.")
    String outputFile;




    SimpleDateFormat dfiso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    Date filterFromDate;
    Date filterToDate;
    
    List<String> chartType = Arrays.asList("serverLoad", "responseTime");

    /**
     * Collects requests mapped by `requestId`
     */
    Map<String, RequestLine> reqLines;

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        try {
            out.println("-----------------------");

            /* Verify arguments
             */
            if (inputHtmlFiles==null && inputLogFile==null && inputFolder==null) {
                out.println("[ERROR] <inputHtmlFiles> or <inputLogFiles> or <inputFolder> should be specified.");
                return 1;
            }
            boolean countGroups = groupMs != null;
            /*
            if (createChart != null) {
                if (chartType.contains(createChart)) {
                    out.println("[ERROR] Chart type not found: " + createChart);
                    out.println("        Known chart types: " + String.join(", ", chartType));
                    return 1;
                }
                if (groupMs == null) {
                    out.println("[ERROR] <groupMs> should be specified for charts.");
                    return 1;
                }
            }
            */

            /* Add files from input folder
             */
            if (inputHtmlFiles==null) {
                inputHtmlFiles = new ArrayList<>();
            }
            if (inputFolder!=null) {
                File[] dirFiles = new File(inputFolder).listFiles();
                if (dirFiles==null) {
                    out.println("[ERROR] No log files in input folder: " + inputFolder);
                    return 1;
                } else {
                    for (File f: dirFiles) {
                        if (f.isFile() && f.getName().endsWith(".html")) {
                            inputHtmlFiles.add(f.getPath());
                        }
                    }
                }
            }

            /* Process input html files
             */
            reqLines = new HashMap<>();
            LineProcessor lp = new LineProcessor(reqLines);
            List<RequestLine> aexRequests = new ArrayList<>();
            out.println("Input HTML files: " + inputHtmlFiles.size());
            for (String inputFile : inputHtmlFiles) {
                lp.processHtmlFile(inputFile);
            }
            List<RequestLine> rex = new ArrayList<>(reqLines.values());
            out.println("-- rex.size(): " + rex.size());
            Collections.sort(rex);
            aexRequests.addAll(rex);

            /* Process plain log files
             */
            if (inputLogFile!=null) {
                processLogFile(inputLogFile);
                rex = new ArrayList<>(reqLines.values());
                Collections.sort(rex);
                aexRequests.addAll(rex);
            }

            /* Filter requests if needed
             */
            if (filterRestServices != null) {
                aexRequests = aexRequests.stream().filter(this::inFilteredServices).collect(Collectors.toList());
            }

            /* Filter users if needed
             */
            if (filterUsers != null) {
                aexRequests = aexRequests.stream().filter(this::inFilteredUsers).collect(Collectors.toList());
            }

            /* Filter start time if needed
             */
            if (filterFromTime != null) {
                filterFromDate = dfiso.parse(filterFromTime);
                aexRequests = aexRequests.stream().filter(this::inFilteredFromDate).collect(Collectors.toList());
            }

            /* Filter end time if needed
             */
            if (filterToTime != null) {
                filterToDate = dfiso.parse(filterToTime);
                aexRequests = aexRequests.stream().filter(this::inFilteredToDate).collect(Collectors.toList());
            }

            /* Do not generate report if no aex requests found
             */
            if (aexRequests.isEmpty()) {
                out.println("[ERROR] No AEX requests found");
                return 1;
            }
            out.println(aexRequests.size() + " AEX request(s) found");

            /* Count requests in groups
             */
            if (groupMs != null) {
                long groupId_1 = 0;
                long groupCount = 0;
                RequestLine firstReq = null;
                for (RequestLine it: aexRequests) {
                    long groupId_2 = it.getGroupId(groupMs);
                    if (groupId_1 != groupId_2) {
                        if (firstReq != null) {
                            firstReq.setGroupCount(groupCount);
                        }
                        firstReq = it;
                        groupCount = 1;
                        groupId_1 = groupId_2;
                    } else {
                        groupCount++;
                    }
                }
                if (firstReq != null) {
                    firstReq.setGroupCount(groupCount);
                }
            }

            /* Update start/end line links
             */
            for (RequestLine it: aexRequests) {
                ExcFile ef = lp.findExcFile(it.getStartLine());
                if (ef != null) {
                    it.setStartLineLink(ef.getPath() + "#" + it.getStartLine());
                }

                ef = lp.findExcFile(it.getEndLine());
                if (ef != null) {
                    it.setEndLineLink(ef.getPath() + "#" + it.getEndLine());
                }
            }

            /* Generate report
             */
            TemplateEngine templateEngine = new TemplateEngine();
            ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setTemplateMode("HTML");
            templateEngine.setTemplateResolver(templateResolver);
            Context context = new Context();
            context.setVariable("aexRequests", aexRequests);
            context.setVariable("countGroups", countGroups);
            String report = templateEngine.process(
                    briefOutput ? "templates/aexlogs-brief.html" : 
                    "templates/aexlogs-bootstrap.html", context);

            /* Save output file
             */
            Files.write(Paths.get(outputFile), report.getBytes());
            out.println("Output file: " + outputFile);
            out.println("-----------------------");
            return 0;

        } catch (Exception e) {
            e.printStackTrace();
            out.println("[ERROR] " + e.getMessage());
            return 1;
        }
    }

    boolean inFilteredServices(RequestLine rex) {
        if (rex.getUrl() == null) {
            return false;
        }
        String serviceName = removeLastSlash(rex.getUrl());
        Iterator<String> iter = filterRestServices.iterator();
        while (iter.hasNext()) {
            String filterServiceName = removeLastSlash(iter.next());
            if (serviceName.endsWith("/" + filterServiceName)) {
                return true;
            }
        }
        return false;
    }

    boolean inFilteredFromDate(RequestLine rex) {
        if (rex.getTstamp() == null) {
            return false;
        }
        return rex.getTstamp().after(filterFromDate);
    }

    boolean inFilteredToDate(RequestLine rex) {
        if (rex.getTstamp() == null) {
            return false;
        }
        return rex.getTstamp().before(filterToDate);
    }

    String removeLastSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    boolean inFilteredUsers(RequestLine rex) {
        if (rex.getUser() == null) {
            return false;
        }
        String userName = rex.getUser().toLowerCase();
        Iterator<String> iter = filterUsers.iterator();
        while (iter.hasNext()) {
            String filterUserName = iter.next();
            if (userName.equals(filterUserName.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    void processLogFile(String inputFile) throws IOException {

        out.println("Plain log file: " + inputFile);
        List<String> lines = Files.readAllLines(Paths.get(inputFile));

        /* Extract request information from log
         */
        reqLines = new HashMap<>();
        LineProcessor lp = new LineProcessor(reqLines);
        int k = 0;
        for (String line : lines) {
            LogLine ll = new LogLine();
            ll.lno = ++k;
            ll.text = line;
            lp.processLogLine(ll);
        }
    }

}
