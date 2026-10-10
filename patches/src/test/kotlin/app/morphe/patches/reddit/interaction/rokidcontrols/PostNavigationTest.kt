package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.extension.reddit.rokid.core.PostNavigation
import app.morphe.extension.reddit.rokid.core.SingleAxisInput
import org.junit.Assert.*
import org.junit.Test

class PostNavigationTest {
    @Test fun `text reaches preview before opening media and Back restores that page`() {
        val reader = PostNavigation()
        reader.configure(3, true, 0)
        assertEquals(PostNavigation.Move.PAGE, reader.move(1))
        assertEquals(1, reader.page())
        assertEquals(PostNavigation.Move.PAGE, reader.move(1))
        assertEquals(2, reader.page())
        assertEquals(PostNavigation.Move.OPEN_MEDIA, reader.move(1))
        reader.configure(3, true, reader.page())
        assertEquals(2, reader.page())
        assertEquals(PostNavigation.Move.PAGE, reader.move(-1))
        assertEquals(1, reader.page())
    }

    @Test fun `single merged preview opens on next swipe while text-only edges remain in post`() {
        val reader = PostNavigation()
        reader.configure(1, true, 0)
        assertEquals(PostNavigation.Move.OPEN_MEDIA, reader.move(1))
        reader.configure(2, false, 1)
        assertEquals(PostNavigation.Move.EDGE, reader.move(1))
        assertEquals(1, reader.page())
        reader.configure(2, false, 0)
        assertEquals(PostNavigation.Move.EDGE, reader.move(-1))
    }

    @Test fun `paired opening gesture cannot advance gallery or seek video`() {
        val input = SingleAxisInput()
        assertEquals(SingleAxisInput.Action.NEXT, input.map(0, 22, 0, 1000))
        assertEquals(SingleAxisInput.Action.DUPLICATE, input.map(0, 20, 0, 1041))
        assertFalse(PostNavigation.acceptsMediaSwipe(1000, 1041))
        assertFalse(PostNavigation.acceptsMediaSwipe(1000, 1299))
        assertTrue(PostNavigation.acceptsMediaSwipe(1000, 1300))
    }

    @Test fun `restored reading page clamps when text size changes pagination`() {
        val reader = PostNavigation()
        reader.configure(2, true, 9)
        assertEquals(1, reader.page())
        reader.configure(4, true, 1)
        assertEquals(1, reader.page())
    }
}
