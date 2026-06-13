package project.witty.keys.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class WkSessionListFormatTest {

    @Test
    fun dateBucket_usesTodayYesterdayOlderLabels() {
        val now = GregorianCalendar(2026, Calendar.MAY, 4, 12, 0, 0)
        val today = GregorianCalendar(2026, Calendar.MAY, 4, 8, 0, 0)
        val yesterday = GregorianCalendar(2026, Calendar.MAY, 3, 23, 30, 0)
        val older = GregorianCalendar(2026, Calendar.MAY, 2, 23, 30, 0)

        assertEquals("Today", WkSessionListFormat.dateBucket(today.timeInMillis, now.timeInMillis))
        assertEquals("Yesterday", WkSessionListFormat.dateBucket(yesterday.timeInMillis, now.timeInMillis))
        assertEquals("Older", WkSessionListFormat.dateBucket(older.timeInMillis, now.timeInMillis))
    }

    @Test
    fun hasMorePage_matchesKeyboardPaginationContract() {
        assertTrue(WkSessionListFormat.hasMorePage(20, 20))
        assertFalse(WkSessionListFormat.hasMorePage(19, 20))
        assertFalse(WkSessionListFormat.hasMorePage(0, 20))
    }
}
