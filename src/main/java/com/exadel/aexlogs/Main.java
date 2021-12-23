package com.exadel.aexlogs;

import static java.lang.System.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static com.exadel.aexlogs.TimestampExtractor.fmt;

/**
 * Analyze Standalone AEX logs from exc utility.
 */
@Command(name = "aexlogs", mixinStandardHelpOptions = true, version = "1.9")
public class Main implements Callable<Integer> {

    @Option(names = { "-f", "--file" },
            description = "Files for input in HTML format received from `exc`.")
    List<String> inputHtmlFiles;

    @Option(names = { "-tx", "--exc-timestamps" },
            description = "Files with additional information about exceptions with timestamps.")
    List<String> inputExcFiles;

    @Option(names = { "-e", "--errors" },
            description = "Track only requests with errors")
    boolean errorsOnly;

    @Option(names = { "-ef", "--error-filter" },
            description = "Track only specified errors.")
    List<String> filterErrors;

    @Option(names = { "-p", "--plain" },
            description = "Plain log file for input.")
    String inputLogFile;

    @Option(names = { "-pm", "--postman" },
            description = "Create Postman collection.")
    boolean createPostman;

    @Option(names = { "-b", "--brief" },
            description = "Brief format for output.")
    boolean briefOutput;

    @Option(names = { "-g", "--group" },
            description = "Group requests within time intervals, ms.")
    Long groupMs;

    @Option(names = { "-x", "--maxtime" },
            description = "Track only requests executing longer than max time, ms.")
    Long maxTimeMs;

    @Option(names = { "-m", "--mongo" },
            description = "MongoDB connection string.")
    String mongoUrl;

    @Option(names = { "-mc", "--mongo-coll" },
            description = "MongoDB collection to store requests data.")
    String mongoCollection;

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

    @Option(names = { "-hstep", "--histogram-step" },
            description = "Create CSV file of the distribution of http requests over time with a given step, min.")
    Long histogramStepMin;

    @Option(names = { "-husers", "--histogram-users" },
            description = "Separating data in histogram columns by user.")
    boolean histogramUsers;

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

    /**
     * Collects information about server restarts.
     */
    List<RequestLine> serverRestartLines = new ArrayList<>();

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
            LineProcessor lp = new LineProcessor(reqLines, filterErrors, serverRestartLines);
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

            /* Filter errors if needed
             */
            if (errorsOnly) {
                aexRequests = aexRequests.stream().filter(this::inFilteredErrors).collect(Collectors.toList());
            }

            /* Filter max execution time if needed
             */
            if (maxTimeMs != null) {
                //RequestLine.maxTimeMs = maxTimeMs;
                aexRequests = aexRequests.stream().filter(this::inMaxExecutionTime).collect(Collectors.toList());
            }

            /* Count camel errors.
             */
            long camelErrorCount = aexRequests.stream().filter(this::inFilteredErrors).count();

            /* Count users
             */
            Set<String> users = aexRequests.stream()
                .filter(req -> req.getUser() != null)
                .map(RequestLine::getUser)
                .collect(Collectors.toSet());
            if (histogramUsers) {
                out.println("Users: " + users);
            }

            /* Count logins
             */
            long numLogins = aexRequests.stream().filter(RequestLine::isLoginUrl).count();

            /* Process exception lists
             */
            if (inputExcFiles != null) {
                for (String excFile : inputExcFiles) {
                    aexRequests.addAll(processExceptionList(excFile));
                }
            }
            aexRequests.addAll(serverRestartLines);

            /* Do not generate report if no aex requests found
             */
            if (aexRequests.isEmpty()) {
                out.println("[ERROR] No AEX requests found");
                return 1;
            }

            out.println("-----------------------");
            out.println("       AEX requests: " + aexRequests.size());
            out.println("              Users: " + users.size());
            out.println("             Logins: " + numLogins);
            out.println("       Camel errors: " + camelErrorCount);

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
                String collName = mongoCollection;
                if (collName == null) {
                    collName = inputFolder;
                }
                new MongoService(mongoUrl, collName).saveRequests(aexRequests);
            }

            /* Sort requests by timestamp
             */
            Collections.sort(aexRequests);

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

            /* Filter out restart/exception notes
             */
            aexRequests = aexRequests.stream().filter(this::inRealRequests).collect(Collectors.toList());

            /* Do not generate postman or csv if there are no real aex requests
             */
            if (aexRequests.isEmpty()) {
                out.println("[ERROR] No valid AEX requests found");
                return 1;
            }

            /* Print start/end dates
             */
            Date logStarts = aexRequests.get(0).getTstamp();
            Date logEnds = aexRequests.get(aexRequests.size() - 1).getTstamp();
            out.println("          Starts at: " + fmt(logStarts));
            out.println("            Ends at: " + fmt(logEnds));

            /* Find the longest request
             */
            int maxTime = aexRequests.stream().map(RequestLine::getMillis).mapToInt(v -> v).max().orElse(0);
            out.println("       Max time, ms: " + maxTime);

            /* Create Postman collection
             */
            if (createPostman) {
                String postmanCollectionFile = replaceExtension(outputFile, PostmanService.postmanExt);
                new PostmanService().saveRequests(aexRequests, postmanCollectionFile);
                out.println(" Postman collection: " + postmanCollectionFile);
            }

            /* Create CSV file with histogram
             */
            if (histogramStepMin != null) {
                String csvFile = replaceExtension(outputFile, ".csv");
                new HistogramService().saveHisto(aexRequests, csvFile, histogramStepMin, histogramUsers, logStarts, logEnds);
                out.println("      Histogram CSV: " + csvFile);
            }

            /* Save output file
             */
            Files.write(Paths.get(outputFile), report.getBytes());
            out.println("        Output file: " + outputFile);
            out.println("-----------------------");
            return 0;

        } catch (Exception e) {
            e.printStackTrace();
            out.println("[ERROR] " + e.getMessage());
            return 1;
        }
    }

    private String replaceExtension(String fname, String ext) {
        int k = fname.lastIndexOf(".");
        return ((k == -1) ? fname : fname.substring(0, k)) + ext;
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

    boolean inFilteredErrors(RequestLine rex) {
        return rex.getError() != null;
    }

    boolean inFilteredExceptions(RequestLine rex) {
        return (rex.getError() != null) || (rex.getStartLine() == 0);
    }

    boolean inRealRequests(RequestLine rex) {
        return rex.getStartLine() != 0;
    }

    boolean inMaxExecutionTime(RequestLine rex) {
        return rex.getMillis() >= maxTimeMs;
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

    void processLogFile(String inputFile) throws IOException, ParseException {

        out.println("Plain log file: " + inputFile);
        List<String> lines = Files.readAllLines(Paths.get(inputFile));

        /* Extract request information from log
         */
        reqLines = new HashMap<>();
        LineProcessor lp = new LineProcessor(reqLines, filterErrors, serverRestartLines);
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
        for (String line : lines) {
            Date d = TimestampExtractor.extractTimestamp(line);
            if (d != null) {
                int k = line.indexOf(" ");
                if (k >= 0) {
                    RequestLine req = new RequestLine();
                    req.setTstamp(d);
                    req.setUrl(line.substring(TimestampExtractor.tstampLen).trim());
                    result.add(req);
                }
            }
        }
        return result;
    }

}
