package com.exadel.aexlogs;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static java.lang.System.*;

import static com.exadel.aexlogs.TimestampExtractor.fmt;

public class HistogramService {

    public void saveHisto(
        List<RequestLine> aexRequests,
        String csvFile,
        long histogramStepMin,
        boolean histogramUsers,
        Date logStarts,
        Date logEnds)
    {
        long stepMs = histogramStepMin * 60000;
        long rangeMin = Math.round(Math.floor((double) logStarts.getTime() / stepMs) * stepMs);
        long rangeMax = Math.round(Math.ceil((double) logEnds.getTime() / stepMs) * stepMs);
        int numSteps = Math.toIntExact((rangeMax - rangeMin) / stepMs);

        int[] histo = new int[numSteps];
        Map<String, int[]> userHisto = new HashMap<>();

        for (RequestLine req: aexRequests) {
            if (req.getStartLine() == 0) {
                continue;
            }
            if (req.getUser() == null && histogramUsers) {
                continue;
            }

            int k = Math.toIntExact((req.getTstamp().getTime() - rangeMin) / stepMs);
            if (k >= histo.length) {
                out.println("[WARN] Out of bounds: " + k + " of " + histo.length + ", time: " + fmt(req.getTstamp()));
                out.println("       URL: " + req.getUrl());
                k = histo.length - 1;
            }
            histo[k]++;
            if (histogramUsers) {
                int[] h = userHisto.get(req.getUser());
                if (h == null) {
                    h = new int[numSteps];
                    userHisto.put(req.getUser(), h);
                }
                h[k]++;
            }

        }

        /* Write CSV file
         */
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(csvFile), CSVFormat.EXCEL)) {

            /* Print header
             */
            List<String> users = null;
            if (histogramUsers) {
                users = new ArrayList<>(userHisto.keySet());
                Collections.sort(users);

                List<String> header = new ArrayList<>();
                header.add("Time");
                header.add("Total");
                header.addAll(users);

                printer.printRecord(header);
            } else {
                printer.printRecord("Time", "Total");
            }

            /* Print body
             */
            for (int i = 0; i<numSteps; i++) {
                LocalDateTime t = new Timestamp(rangeMin + i * stepMs).toLocalDateTime();
                if (histogramUsers) {
                    List<Object> row = new ArrayList<>();
                    row.add(t);
                    row.add(histo[i]);
                    for (String u: users) {
                        row.add(userHisto.get(u)[i]);
                    }
                    printer.printRecord(row);
                } else {
                    printer.printRecord(t, histo[i]);
                }
            }

        } catch (IOException e) {
            out.println("[WARN] Cannot write CSV file: " + csvFile);
            out.println("       Reason: " + e.getMessage());
        }
    }

}
