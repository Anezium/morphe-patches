package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.extension.reddit.rokid.core.RingInput
import app.morphe.extension.reddit.rokid.core.RingInput.Action
import app.morphe.extension.reddit.rokid.core.SingleAxisInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingInputTest {
    @Test fun `raw media slides map to the canonical single axis`() {
        val ring = RingInput()
        assertEquals(Action.NEXT, ring.onKey(0, 87, 0, 1000, false))
        assertEquals(Action.PREVIOUS, ring.onKey(0, 88, 0, 1300, false))
        val temple = SingleAxisInput()
        assertEquals(SingleAxisInput.Action.NEXT, temple.map(0, 22, 0, 1000))
        assertEquals(SingleAxisInput.Action.DUPLICATE, temple.map(0, 20, 0, 1040))
        assertEquals(SingleAxisInput.Action.PREVIOUS, temple.map(0, 21, 0, 1300))
    }

    @Test fun `single tap waits and double tap becomes Back exactly once`() {
        val ring = RingInput()
        ring.onKey(0, 85, 0, 1000, false)
        assertEquals(Action.NONE, ring.resolve(1349, false))
        assertEquals(Action.SELECT, ring.resolve(1350, false))
        assertEquals(Action.NONE, ring.resolve(1700, false))
        ring.onKey(0, 126, 0, 2000, false)
        ring.onKey(0, 127, 0, 2200, false)
        assertEquals(Action.BACK, ring.resolve(2550, false))
        assertFalse(ring.pending())
    }

    @Test fun `UP repeats and unrelated keys do not become taps or slides`() {
        val ring = RingInput()
        ring.onKey(0, 85, 0, 1000, false)
        assertEquals(Action.NONE, ring.onKey(1, 85, 0, 1050, false))
        assertEquals(Action.NONE, ring.onKey(0, 85, 1, 1100, false))
        assertEquals(Action.NONE, ring.onKey(0, 87, 1, 1150, false))
        assertEquals(Action.NONE, ring.onKey(0, 30, 0, 1200, false))
        assertEquals(Action.SELECT, ring.resolve(1350, false))
    }

    @Test fun `direction and release cancel a pending tap`() {
        val ring = RingInput()
        ring.onKey(0, 85, 0, 1000, false)
        assertEquals(Action.NEXT, ring.onKey(0, 87, 0, 1100, false))
        assertEquals(Action.NONE, ring.resolve(1600, false))
        ring.onKey(0, 85, 0, 2000, false)
        ring.reset()
        assertFalse(ring.pending())
        assertEquals(Action.NONE, ring.resolve(2400, false))
    }

    @Test fun `triple tap never selects and expired taps cannot combine`() {
        val ring = RingInput()
        repeat(3) { ring.onKey(0, 85, 0, 1000L + it * 100, false) }
        assertEquals(Action.NONE, ring.resolve(1550, false))
        ring.onKey(0, 85, 0, 2000, false)
        ring.onKey(0, 85, 0, 2500, false)
        assertEquals(Action.SELECT, ring.resolve(2850, false))
    }

    @Test fun `editor taps cannot inject Enter or activate a later Send screen`() {
        val ring = RingInput()
        ring.onKey(0, 85, 0, 1000, true)
        assertEquals(Action.NONE, ring.resolve(1350, false))
        ring.onKey(0, 85, 0, 2000, false)
        assertEquals(Action.NONE, ring.resolve(2350, true))
        assertEquals(Action.NONE, ring.onKey(0, 87, 0, 2600, true))
        assertEquals(Action.NONE, ring.onKey(0, 88, 0, 2900, true))
        ring.onKey(0, 85, 0, 3000, true)
        ring.onKey(0, 85, 0, 3100, true)
        assertEquals(Action.BACK, ring.resolve(3450, true))
    }

    @Test fun `only focused foreground ownership may dispatch and editor allows only Back`() {
        for (action in Action.values()) {
            assertFalse(RingInput.mayDispatch(action, false, false))
            assertEquals(action == Action.BACK, RingInput.mayDispatch(action, true, true))
            assertEquals(action != Action.NONE, RingInput.mayDispatch(action, true, false))
        }
    }

    @Test fun `ordinary media controls are not classified as a ring device`() {
        assertTrue(RingInput.isRingDevice("R08 ring"))
        assertTrue(RingInput.isRingDevice("r08"))
        assertFalse(RingInput.isRingDevice("Bluetooth headset"))
        assertFalse(RingInput.isRingDevice(null))
        assertTrue(RingInput.accepts(87))
        assertFalse(RingInput.accepts(66))
    }
}
