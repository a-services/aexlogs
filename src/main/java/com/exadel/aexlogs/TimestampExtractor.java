package com.exadel.aexlogs;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Extract timestamp from the beginning of the log line
 * in commonly used `yyyy-MM-dd HH:mm:ss.SSS` format.
 *
 * Or you can set your own timestamp format with `setDateFormat()`.
 */
public class TimestampExtractor {

    String dformat = "yyyy-MM-dd HH:mm:ss,SSS";
    SimpleDateFormat df = new SimpleDateFormat(dformat);
    int dfLen = dformat.length();

    public void setDateFormat(String dformat) {
        this.dformat = dformat;
        df = new SimpleDateFormat(dformat);
        dfLen = dformat.length();
    }

    public Date extractTimestamp(String line) {
        if (line == null || line.length() < dfLen) {
            return null;
        }
        try {
            String tstamp = line.substring(0, dfLen);
            return df.parse(tstamp);

        } catch (Exception e) {
            return null;
        }
    }

    public String format(Date date) {
        return df.format(date);
    }
}
