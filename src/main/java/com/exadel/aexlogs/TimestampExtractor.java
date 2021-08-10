package com.exadel.aexlogs;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Extract timestamp from beginning of log line
 * in the commonly used format `yyyy-MM-dd HH:mm:ss,SSS`.
 *
 * Or you can set your own timestamp format using `setDateFormat()`.
 */
public class TimestampExtractor {

    /**
     * Timestamp format string.
     */
    String tstampFmt = "yyyy-MM-dd HH:mm:ss,SSS";

    static final String TZONE_FMT_SUFFIX = " X";

    /**
     * Timezone string of API Express server in `+00:00` format;
     */
    public static String timeZone =
        new SimpleDateFormat(TZONE_FMT_SUFFIX.trim()).format(new Date());

    /**
     * We use formatter with explicitly added timezone of API Express server.
     */
    SimpleDateFormat df = new SimpleDateFormat(tstampFmt + TZONE_FMT_SUFFIX);
    int tstampLen = tstampFmt.length();

    public void setDateFormat(String timeStampFmt) {
        tstampFmt = timeStampFmt;
        df = new SimpleDateFormat(tstampFmt + TZONE_FMT_SUFFIX);
        tstampLen = tstampFmt.length();
    }

    public Date extractTimestamp(String line) {
        if (line == null || line.length() < tstampLen) {
            return null;
        }
        try {
            String tstamp = line.substring(0, tstampLen);
            return df.parse(tstamp + " " + timeZone);

        } catch (Exception e) {
            return null;
        }
    }

    public String format(Date date) {
        return df.format(date);
    }
}
