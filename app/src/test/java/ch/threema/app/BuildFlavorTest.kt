package ch.threema.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildFlavorTest {
    @Test
    fun `isOnPrem returns correct values`() {
        assertTrue(BuildFlavor.OnPrem.isOnPrem)

        assertFalse(BuildFlavor.None.isOnPrem)
        assertFalse(BuildFlavor.StoreGoogle.isOnPrem)
        assertFalse(BuildFlavor.StoreThreema.isOnPrem)
        assertFalse(BuildFlavor.StoreGoogleWork.isOnPrem)
        assertFalse(BuildFlavor.Green.isOnPrem)
        assertFalse(BuildFlavor.SandboxWork.isOnPrem)
        assertFalse(BuildFlavor.Blue.isOnPrem)
        assertFalse(BuildFlavor.Hms.isOnPrem)
        assertFalse(BuildFlavor.HmsWork.isOnPrem)
        assertFalse(BuildFlavor.Libre.isOnPrem)
    }

    @Test
    fun `isWork returns correct values`() {
        assertTrue(BuildFlavor.OnPrem.isWork)
        assertTrue(BuildFlavor.StoreGoogleWork.isWork)
        assertTrue(BuildFlavor.SandboxWork.isWork)
        assertTrue(BuildFlavor.HmsWork.isWork)
        assertTrue(BuildFlavor.Blue.isWork)

        assertFalse(BuildFlavor.None.isWork)
        assertFalse(BuildFlavor.StoreGoogle.isWork)
        assertFalse(BuildFlavor.StoreThreema.isWork)
        assertFalse(BuildFlavor.Green.isWork)
        assertFalse(BuildFlavor.Hms.isWork)
        assertFalse(BuildFlavor.Libre.isWork)
    }
}
