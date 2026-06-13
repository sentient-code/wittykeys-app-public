package project.witty.keys.ui.chat;

import java.util.Calendar;

public final class WkSessionListFormat {

    private WkSessionListFormat() {}

    public static String dateBucket(long timestamp, long nowMillis) {
        Calendar sessionCal = Calendar.getInstance();
        sessionCal.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMillis);

        Calendar yesterday = Calendar.getInstance();
        yesterday.setTimeInMillis(nowMillis);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (sameDay(sessionCal, today)) return "Today";
        if (sameDay(sessionCal, yesterday)) return "Yesterday";
        return "Older";
    }

    public static boolean hasMorePage(int loadedCount, int pageSize) {
        return pageSize > 0 && loadedCount == pageSize;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
