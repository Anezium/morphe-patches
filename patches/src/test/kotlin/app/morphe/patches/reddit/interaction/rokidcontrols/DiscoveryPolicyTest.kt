package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.extension.reddit.rokid.core.DiscoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {
    @Test fun `native pagination keeps opaque cursors and stops only at exhaustion`() {
        val first = "opaque:+/=%cursor1"
        val second = "opaque:+/=%cursor2"
        assertEquals(first, DiscoveryPolicy.nextCursor(first, null))
        assertEquals(second, DiscoveryPolicy.nextCursor(second, first, true))
        assertNull(DiscoveryPolicy.nextCursor(null, second))
        assertNull(DiscoveryPolicy.nextCursor(second, second, false))
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.nextCursor(second, second, true)
        }
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.nextCursor(null, second, true)
        }
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.nextCursor("", second, true)
        }
    }

    @Test fun `cursor boundary rejects unbounded or hidden data without rewriting valid opaque values`() {
        assertEquals("a".repeat(2048), DiscoveryPolicy.cursor("a".repeat(2048)))
        listOf("a".repeat(2049), " leading", "trailing ", "line\nbreak", "null\u0000byte").forEach { cursor ->
            assertThrows(IllegalArgumentException::class.java) { DiscoveryPolicy.cursor(cursor) }
        }
    }

    @Test fun `switching account or replacing the session rejects delayed native results`() {
        val original = Any()
        assertTrue(DiscoveryPolicy.sameAccount(original, "reader", original, "reader"))
        assertFalse(DiscoveryPolicy.sameAccount(original, "reader", original, "another_reader"))
        assertFalse(DiscoveryPolicy.sameAccount(original, "reader", Any(), "reader"))
        assertFalse(DiscoveryPolicy.sameAccount(original, "reader", null, "reader"))
        assertFalse(DiscoveryPolicy.sameAccount(original, "reader", original, ""))
        assertFalse(DiscoveryPolicy.sameAccount(original, "", original, ""))
    }

    @Test fun `public feeds allow anonymous ownership without accepting missing owners`() {
        val graph = Any()
        val session = Any()
        assertTrue(DiscoveryPolicy.sameFeedOwner(graph, session, "", graph, session, ""))
        assertFalse(DiscoveryPolicy.sameFeedOwner(null, session, "", null, session, ""))
        assertFalse(DiscoveryPolicy.sameFeedOwner(graph, null, "", graph, null, ""))
    }

    @Test fun `feed callbacks reject replaced graph or session even with the same account name`() {
        val graph = Any()
        val session = Any()
        assertTrue(DiscoveryPolicy.sameFeedOwner(graph, session, "reader", graph, session, "reader"))
        assertFalse(DiscoveryPolicy.sameFeedOwner(graph, session, "reader", Any(), session, "reader"))
        assertFalse(DiscoveryPolicy.sameFeedOwner(graph, session, "reader", graph, Any(), "reader"))
        assertFalse(DiscoveryPolicy.sameFeedOwner(graph, session, "reader", graph, session, "another_reader"))
        assertFalse(DiscoveryPolicy.sameFeedOwner(graph, session, "", graph, session, "reader"))
    }

    @Test fun `promoted context survives every search entry classification`() {
        assertTrue(DiscoveryPolicy.organicSearchPost("Default", false))
        assertTrue(DiscoveryPolicy.organicSearchPost("Hero", false))
        listOf("Default", "Hero", "PromotedHero", "FutureUnknown", "").forEach { type ->
            assertFalse(DiscoveryPolicy.organicSearchPost(type, true))
            assertFalse(DiscoveryPolicy.organicSearchPost(type, null))
        }
        assertFalse(DiscoveryPolicy.organicSearchPost("PromotedHero", false))
        assertFalse(DiscoveryPolicy.organicSearchPost("FutureUnknown", false))
    }

    @Test fun `community feed identity requires one matching native id and literal subreddit name`() {
        assertEquals("t5_2qh1i", DiscoveryPolicy.communityId("2qh1i", "t5_2qh1i", "AskReddit"))
        listOf("../popular", "android/new", "r/android", "android?x=1", "", "a".repeat(22)).forEach { name ->
            assertThrows(IllegalStateException::class.java) {
                DiscoveryPolicy.communityId("2qh1i", "t5_2qh1i", name)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.communityId("2qh1i", "t5_another", "AskReddit")
        }
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.communityId("2qh1i", "t3_2qh1i", "AskReddit")
        }
        assertThrows(IllegalStateException::class.java) {
            DiscoveryPolicy.communityId(null, "t5_2qh1i", "AskReddit")
        }
    }

    @Test fun `search submits trimmed user text and rejects invalid request boundaries`() {
        assertEquals("Rokid glasses", DiscoveryPolicy.query("  Rokid glasses  "))
        assertEquals("a".repeat(200), DiscoveryPolicy.query("a".repeat(200)))
        listOf(null, "  ", "a".repeat(201), "two\nqueries", "bad\u0000query").forEach { query ->
            assertThrows(IllegalArgumentException::class.java) { DiscoveryPolicy.query(query) }
        }
    }
}