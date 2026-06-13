package project.witty.keys.app.helpers

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Tests for BatteryOptimizationHelper.
 * Uses Robolectric for Context and Build.MANUFACTURER mocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BatteryOptimizationHelperTest {

    // === isOptimized ===

    @Test
    fun `isOptimized returns boolean without crashing`() {
        val context = RuntimeEnvironment.getApplication()
        // Just verify it returns a valid boolean (actual value depends on test env)
        val result = BatteryOptimizationHelper.isOptimized(context)
        assertNotNull(result)
    }

    // === OEM Intent Mapping ===

    @Test
    fun `getBatterySettingsIntent returns non-null for xiaomi`() {
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", "Xiaomi")
        val context = RuntimeEnvironment.getApplication()
        val intent = BatteryOptimizationHelper.getBatterySettingsIntent(context)
        assertNotNull(intent)
    }

    @Test
    fun `getBatterySettingsIntent returns non-null for samsung`() {
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", "Samsung")
        val context = RuntimeEnvironment.getApplication()
        val intent = BatteryOptimizationHelper.getBatterySettingsIntent(context)
        assertNotNull(intent)
    }

    @Test
    fun `getBatterySettingsIntent returns non-null for unknown manufacturer`() {
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", "GenericBrand")
        val context = RuntimeEnvironment.getApplication()
        val intent = BatteryOptimizationHelper.getBatterySettingsIntent(context)
        // Should fall through to generic battery or app info settings
        assertNotNull(intent)
    }

    @Test
    fun `getBatterySettingsIntent handles all 6 OEM cases`() {
        val oems = listOf("xiaomi", "samsung", "oneplus", "huawei", "vivo", "oppo")
        val context = RuntimeEnvironment.getApplication()

        for (oem in oems) {
            ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", oem)
            val intent = BatteryOptimizationHelper.getBatterySettingsIntent(context)
            assertNotNull("Intent should not be null for OEM: $oem", intent)
        }
    }

    // === isNlsEnabled ===

    @Test
    fun `isNlsEnabled returns false when no listeners enabled`() {
        val context = RuntimeEnvironment.getApplication()
        // In test environment, no NLS is registered
        assertFalse(BatteryOptimizationHelper.isNlsEnabled(context))
    }

    // === isLikelyBeingKilled ===

    @Test
    fun `isLikelyBeingKilled returns false when NLS not granted`() {
        val context = RuntimeEnvironment.getApplication()
        // NLS not granted + not connected → not being killed (just not enabled)
        assertFalse(BatteryOptimizationHelper.isLikelyBeingKilled(context))
    }
}
