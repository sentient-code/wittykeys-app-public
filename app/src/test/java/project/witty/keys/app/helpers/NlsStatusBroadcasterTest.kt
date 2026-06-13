package project.witty.keys.app.helpers

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class NlsStatusBroadcasterTest {

    @Before
    fun setup() {
        // Reset static state via reflection
        val field = NlsStatusBroadcaster::class.java.getDeclaredField("lastKnownStatus")
        field.isAccessible = true
        field.setBoolean(null, false)
    }

    @Test
    fun `isNlsConnected returns false by default`() {
        assertFalse(NlsStatusBroadcaster.isNlsConnected())
    }

    @Test
    fun `isNlsConnected reflects lastKnownStatus set to true`() {
        // Set the static field directly (avoids needing Context for sendStatus)
        val field = NlsStatusBroadcaster::class.java.getDeclaredField("lastKnownStatus")
        field.isAccessible = true
        field.setBoolean(null, true)

        assertTrue(NlsStatusBroadcaster.isNlsConnected())
    }

    @Test
    fun `isNlsConnected reflects lastKnownStatus set to false after true`() {
        val field = NlsStatusBroadcaster::class.java.getDeclaredField("lastKnownStatus")
        field.isAccessible = true

        field.setBoolean(null, true)
        assertTrue(NlsStatusBroadcaster.isNlsConnected())

        field.setBoolean(null, false)
        assertFalse(NlsStatusBroadcaster.isNlsConnected())
    }
}
