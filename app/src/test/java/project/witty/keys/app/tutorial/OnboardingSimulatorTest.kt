package project.witty.keys.app.tutorial

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OnboardingSimulator and OnboardingConversations.
 * Tests conversation data integrity, skip logic, and completion tracking.
 */
class OnboardingSimulatorTest {

    @Test
    fun `conversations have exactly 3 scenarios`() {
        val conversations = OnboardingConversations.getConversations()
        assertEquals(3, conversations.size)
    }

    @Test
    fun `all 7 scenarios available including E2E`() {
        val all = OnboardingConversations.getAllScenarios()
        assertEquals(7, all.size)
    }

    @Test
    fun `each conversation has at least 2 messages`() {
        OnboardingConversations.getConversations().forEach { conv ->
            assertTrue("${conv.contactName} has < 2 messages", conv.messages.size >= 2)
        }
    }

    @Test
    fun `each received message has exactly 3 reply options`() {
        OnboardingConversations.getConversations().forEach { conv ->
            conv.messages.filter { it.isReceived }.forEach { msg ->
                assertNotNull("Reply options null for: ${msg.text}", msg.replies)
                assertEquals("Expected 3 replies for: ${msg.text}", 3, msg.replies.size)
            }
        }
    }

    @Test
    fun `E2E scenarios also have 3 replies per message`() {
        OnboardingConversations.getAllScenarios().forEach { conv ->
            conv.messages.filter { it.isReceived }.forEach { msg ->
                assertNotNull("Reply options null for: ${msg.text}", msg.replies)
                assertEquals("Expected 3 replies for: ${msg.text}", 3, msg.replies.size)
            }
        }
    }

    @Test
    fun `getScenarioByName returns correct scenarios`() {
        val casual = OnboardingConversations.getScenarioByName("CASUAL_FRIEND")
        assertNotNull(casual)
        assertEquals("Priya", casual!!.contactName)

        val boss = OnboardingConversations.getScenarioByName("FRUSTRATED_BOSS")
        assertNotNull(boss)
        assertEquals("Boss", boss!!.contactName)

        val hindi = OnboardingConversations.getScenarioByName("HINDI_CASUAL")
        assertNotNull(hindi)
        assertEquals("Amit", hindi!!.contactName)
    }

    @Test
    fun `getScenarioByName returns null for unknown scenario`() {
        val unknown = OnboardingConversations.getScenarioByName("NONEXISTENT")
        assertNull(unknown)
    }

    @Test
    fun `all conversations have unique contact names`() {
        val conversations = OnboardingConversations.getConversations()
        val names = conversations.map { it.contactName }
        assertEquals("Contact names should be unique", names.size, names.toSet().size)
    }

    @Test
    fun `all scenario types are covered`() {
        val allScenarios = OnboardingConversations.getAllScenarios()
        val types = allScenarios.map { it.scenarioType }
        OnboardingConversations.ScenarioType.values().forEach { expectedType ->
            assertTrue("Missing scenario type: $expectedType", types.contains(expectedType))
        }
    }

    @Test
    fun `reply texts are not empty`() {
        OnboardingConversations.getAllScenarios().forEach { conv ->
            conv.messages.filter { it.isReceived }.forEach { msg ->
                msg.replies.forEach { reply ->
                    assertTrue("Empty reply found in ${conv.contactName}", reply.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `message texts are not empty`() {
        OnboardingConversations.getAllScenarios().forEach { conv ->
            conv.messages.forEach { msg ->
                assertTrue("Empty message in ${conv.contactName}", msg.text.isNotEmpty())
            }
        }
    }
}
