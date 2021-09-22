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
@Command(name = "aexlogs", mixinStandardHelpOptions = true, version = "1.5")
public class Main implements Callable<Integer> {

    @Option(names = { "-f", "--file" },
            description = "Files for input in HTML format received from `exc`.")
    List<String> inputHtmlFiles;

    @Option(names = { "-e", "--exc" },
            description = "Files with additional information about exceptions.")
    List<String> inputExcFiles;

    @Option(names = { "-p", "--plain" },
            description = "Plain log file for input.")
    String inputLogFile;

    @Option(names = { "-b", "--brief" },
            description = "Brief format for output.")
    boolean briefOutput;

    @Option(names = { "-be", "--brief-exc" },
            description = "Brief exceptions format for output.")
    boolean briefExcOutput;

    @Option(names = { "-g", "--group" },
            description = "Group requests within time intervals, ms.")
    Long groupMs;

    @Option(names = { "-x", "--maxtime" },
            description = "Highlight requests in report executing longer than max time, ms.")
    Long maxTimeMs;

    @Option(names = { "-m", "--mongo" },
            description = "MongoDB connection string.")
    String mongoUrl;

    @Option(names = { "-z", "--timezone" },
            description = "AEX server timezone in +00:00 format.")
    String timeZone;

    @Option(names = { "-d", "--dir" },
            description = "Folder with files in HTML format received from `exc`.")
    String inputFolder;

    @Option(names = { "-l", "--lineurl" },
            description = "`exc` server url to inspect log lines.")
    String excLineUrl;

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

            /* Update timezone
             */
            if (timeZone != null) {
                TimestampExtractor.timeZone = timeZone;
            }
            out.println("Timezone: " + TimestampExtractor.timeZone);

            /* Update max time
             */
            if (maxTimeMs != null) {
                RequestLine.maxTimeMs = maxTimeMs;
            }

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
            aexRequests.addAll(rex);

            /* Process plain log files
             */
            if (inputLogFile!=null) {
                processLogFile(inputLogFile);
                rex = new ArrayList<>(reqLines.values());
                aexRequests.addAll(rex);
            }

            /* Process exception lists
             */
            if (inputExcFiles != null) {
                for (String excFile : inputExcFiles) {
                    aexRequests.addAll(processExceptionList(excFile));
                }
            }

            /* Sort requests by timestamp
             */
            Collections.sort(aexRequests);

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
                if (excLineUrl != null) {
                    it.setStartLineLink(excLineUrl + "&lno=" + it.getStartLine());
                } else
                if (ef != null) {
                    it.setStartLineLink(ef.getPath() + "#" + it.getStartLine());
                }

                ef = lp.findExcFile(it.getEndLine());
                if (excLineUrl != null) {
                    it.setEndLineLink(excLineUrl + "&lno=" + it.getEndLine());
                } else
                if (ef != null) {
                    it.setEndLineLink(ef.getPath() + "#" + it.getEndLine());
                }
            }

            /* Save requests in MongoDB
             */
            if (mongoUrl != null) {
                new MongoService(mongoUrl, inputFolder).saveRequests(aexRequests);
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
                    briefExcOutput ? "templates/aexlogs-brief-exc.html" :
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

    List<RequestLine> processExceptionList(String inputFile) throws IOException {
        out.println("Exception list: " + inputFile);
        List<String> lines = Files.readAllLines(Paths.get(inputFile));

        /* Each line contains timestamp and exception name
         */
        List<RequestLine> result = new ArrayList<>();
        TimestampExtractor tse = new TimestampExtractor();
        for (String line : lines) {
            Date d = tse.extractTimestamp(line);
            if (d != null) {
                int k = line.indexOf(" ");
                if (k >= 0) {
                    RequestLine req = new RequestLine();
                    req.setTstamp(d);
                    req.setUrl(line.substring(tse.tstampLen).trim());
                    result.add(req);
                }
            }
        }
        return result;
    }

}
