package project.witty.keys.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OverlayPermissionModalThemeContractTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `permission rationale modal uses branded onboarding theme`() {
        val layout = read("src/main/res/layout/overlay_permission_popup.xml")
        val flow = read("src/main/java/project/witty/keys/app/overlay/OverlayPermissionFlow.java")

        assertTrue(layout.contains("@drawable/wk_permission_modal_bg"))
        assertTrue(layout.contains("@drawable/wk_logo_overlay"))
        assertTrue(layout.contains("android:layout_weight=\"1\""))
        assertTrue(layout.contains("overlay_perm_step_label"))
        assertTrue(layout.contains("overlay_perm_detail_1"))
        assertTrue(layout.contains("overlay_perm_detail_2"))
        assertTrue(layout.contains("overlay_perm_detail_3"))
        assertTrue(flow.contains("\"Step \" + stepNum + \" of \" + totalSteps"))
        assertTrue(flow.contains("window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT)"))
        assertTrue(flow.contains("actionButton.setBackgroundTintList(null)"))
        assertTrue(flow.contains("configurePermissionCopy("))
        assertFalse(layout.contains("🛡️"))
        assertFalse(flow.contains("\"Step \" + stepNum + \"/\" + totalSteps"))
    }
}
