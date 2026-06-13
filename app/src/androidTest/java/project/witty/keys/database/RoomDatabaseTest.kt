package project.witty.keys.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import project.witty.keys.app.database.*

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var db: WittyKeysDatabase
    private lateinit var sessionDao: ChatSessionDao
    private lateinit var messageDao: ChatMessageDao
    private lateinit var screenshotDao: SessionScreenshotDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, WittyKeysDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.chatSessionDao()
        messageDao = db.chatMessageDao()
        screenshotDao = db.screenshotDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // === ChatSession CRUD ===

    @Test
    fun insertAndRetrieveSession() {
        val session = ChatSession().apply {
            contactName = "Priya"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Priya"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
        }
        val id = sessionDao.insert(session)
        assertTrue(id > 0)

        val retrieved = sessionDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Priya", retrieved!!.contactName)
        assertEquals("com.whatsapp", retrieved.packageName)
    }

    @Test
    fun getActiveByConversationKey() {
        val session = ChatSession().apply {
            contactName = "Priya"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Priya"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
            isArchived = false
        }
        sessionDao.insert(session)

        val active = sessionDao.getActiveByConversationKey("com.whatsapp|Priya")
        assertNotNull(active)
    }

    @Test
    fun archivedSessionNotReturnedByGetActive() {
        val session = ChatSession().apply {
            contactName = "Priya"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Priya"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
            isArchived = false
        }
        val id = sessionDao.insert(session)
        sessionDao.archive(id)

        val active = sessionDao.getActiveByConversationKey("com.whatsapp|Priya")
        assertNull(active)
    }

    @Test
    fun incrementMessageCount() {
        val session = ChatSession().apply {
            contactName = "Priya"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Priya"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
            messageCount = 0
        }
        val id = sessionDao.insert(session)
        sessionDao.incrementMessageCount(id, System.currentTimeMillis())

        val updated = sessionDao.getById(id)
        assertEquals(1, updated!!.messageCount)
    }

    @Test
    fun getAllActiveExcludesArchived() {
        sessionDao.insert(ChatSession().apply {
            contactName = "Active1"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Active1"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
            isArchived = false
        })
        val archivedId = sessionDao.insert(ChatSession().apply {
            contactName = "Archived1"
            packageName = "com.whatsapp"
            conversationKey = "com.whatsapp|Archived1"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
            isArchived = false
        })
        sessionDao.archive(archivedId)

        val active = sessionDao.getAllActive()
        assertEquals(1, active.size)
        assertEquals("Active1", active[0].contactName)
    }

    // === ChatMessage CRUD ===

    @Test
    fun insertAndRetrieveMessages() {
        val sessionId = createTestSession()

        val msg = ChatMessage().apply {
            this.sessionId = sessionId
            role = "user"
            content = "Hey Priya!"
            timestamp = System.currentTimeMillis()
            type = "text"
        }
        val msgId = messageDao.insert(msg)
        assertTrue(msgId > 0)

        val messages = messageDao.getBySession(sessionId)
        assertEquals(1, messages.size)
        assertEquals("Hey Priya!", messages[0].content)
    }

    @Test
    fun getRecentBySessionRespectsLimit() {
        val sessionId = createTestSession()

        for (i in 1..5) {
            messageDao.insert(ChatMessage().apply {
                this.sessionId = sessionId
                role = "user"
                content = "Message $i"
                timestamp = System.currentTimeMillis() + i
                type = "text"
            })
        }

        val recent = messageDao.getRecentBySession(sessionId, 3)
        assertEquals(3, recent.size)
    }

    @Test
    fun getMessageCount() {
        val sessionId = createTestSession()

        messageDao.insert(ChatMessage().apply {
            this.sessionId = sessionId
            role = "user"
            content = "msg1"
            timestamp = System.currentTimeMillis()
            type = "text"
        })
        messageDao.insert(ChatMessage().apply {
            this.sessionId = sessionId
            role = "assistant"
            content = "msg2"
            timestamp = System.currentTimeMillis()
            type = "text"
        })

        assertEquals(2, messageDao.getMessageCount(sessionId))
    }

    // === SessionScreenshot CRUD ===

    @Test
    fun insertAndRetrieveScreenshot() {
        val sessionId = createTestSession()

        val screenshot = SessionScreenshot().apply {
            this.sessionId = sessionId
            filePath = "/data/data/project.witty.keys/files/screenshots/test.jpg"
            capturedAt = System.currentTimeMillis()
            width = 1080
            height = 2640
        }
        val id = screenshotDao.insert(screenshot)
        assertTrue(id > 0)

        val screenshots = screenshotDao.getBySession(sessionId)
        assertEquals(1, screenshots.size)
        assertEquals(1080, screenshots[0].width)
    }

    // === Foreign Key & Cascade ===

    @Test
    fun deletingSessionCascadesDeleteMessages() {
        val sessionId = createTestSession()

        messageDao.insert(ChatMessage().apply {
            this.sessionId = sessionId
            role = "user"
            content = "test"
            timestamp = System.currentTimeMillis()
            type = "text"
        })

        assertEquals(1, messageDao.getMessageCount(sessionId))

        val session = sessionDao.getById(sessionId)!!
        sessionDao.delete(session)

        assertEquals(0, messageDao.getMessageCount(sessionId))
    }

    @Test
    fun deletingSessionCascadesDeleteScreenshots() {
        val sessionId = createTestSession()

        screenshotDao.insert(SessionScreenshot().apply {
            this.sessionId = sessionId
            filePath = "/test.jpg"
            capturedAt = System.currentTimeMillis()
            width = 100
            height = 200
        })

        val session = sessionDao.getById(sessionId)!!
        sessionDao.delete(session)

        val screenshots = screenshotDao.getBySession(sessionId)
        assertTrue(screenshots.isEmpty())
    }

    // === Helper ===

    private fun createTestSession(): Long {
        return sessionDao.insert(ChatSession().apply {
            contactName = "Test"
            packageName = "com.test"
            conversationKey = "com.test|Test"
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
        })
    }
}
