package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.extension.reddit.rokid.core.SingleAxisInput
import app.morphe.extension.reddit.rokid.core.SingleAxisInput.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleAxisInputTest {
    @Test fun `paired Rokid swipe aliases advance exactly once`() {
        val input = SingleAxisInput()
        assertEquals(Action.NEXT, input.map(0, 20, 0, 1000))
        assertEquals(Action.DUPLICATE, input.map(0, 183, 0, 1010))
        assertEquals(Action.DUPLICATE, input.map(0, 22, 0, 1020))
        assertEquals(Action.NEXT, input.map(0, 183, 0, 1250))
    }
    @Test fun `opposite aliases share debounce and repeats never activate`() {
        val input = SingleAxisInput()
        assertEquals(Action.PREVIOUS, input.map(0, 184, 0, 1000))
        assertEquals(Action.DUPLICATE, input.map(0, 20, 0, 1010))
        assertEquals(Action.DUPLICATE, input.map(0, 23, 1, 1500))
        assertEquals(Action.SELECT, input.map(0, 23, 0, 1500))
    }
    @Test fun `key up and unrelated keys do not perform actions`() {
        val input = SingleAxisInput()
        assertEquals(Action.NONE, input.map(1, 23, 0, 1000))
        assertEquals(Action.NONE, input.map(0, 30, 0, 1000))
        assertEquals(Action.BACK, input.map(0, 4, 0, 1000))
    }
}
