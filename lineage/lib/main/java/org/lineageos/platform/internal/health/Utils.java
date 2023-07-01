package org.lineageos.platform.internal.health;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

public class Utils {
    public static String msToString(long ms) {
        return msToString(ms, TimeZone.getDefault());
    }

    public static String msToString(long ms, String timeZone) {
        return msToString(ms, TimeZone.getTimeZone(timeZone));
    }

    public static String msToString(long ms, TimeZone zone) {
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss");
        dateFormatter.setTimeZone(zone);
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(ms);
        return dateFormatter.format(calendar.getTime());
    }

    /**
     * Convert the seconds of the day to UTC milliseconds from epoch.
     *
     * @param time seconds of the day
     * @return UTC milliseconds from epoch
     */
    public static long getTimeMillisFromSecondOfDay(int time) {
        ZoneId utcZone = ZoneOffset.UTC;
        LocalDate currentDate = LocalDate.now();
        LocalTime timeOfDay = LocalTime.ofSecondOfDay(time);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(currentDate, timeOfDay,
                ZoneId.systemDefault()).withZoneSameInstant(utcZone);
        return zonedDateTime.toInstant().toEpochMilli();
    }

    public static LocalTime getLocalTimeFromEpochMilli(long time) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime();
    }
}
