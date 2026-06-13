package project.witty.keys.ui.chat

import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import project.witty.keys.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = WkDesignResourceContractTest.StubApp::class)
class WkDesignResourceContractTest {
    class StubApp : android.app.Application() { override fun onCreate() {} }

    private fun themedCtx() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_WittyKeys)

    @Test
    fun launchCardDrawables_existAndAreGradientDrawables() {
        val context = themedCtx()
        listOf(
            R.drawable.wk_app_card_bg,
            R.drawable.wk_app_card_elevated_bg,
            R.drawable.wk_app_card_quiet_bg,
            R.drawable.wk_app_card_hero_bg,
            R.drawable.wk_app_search_bg,
            R.drawable.wk_ds_input_bar_bg
        ).forEach { drawableId ->
            assertTrue(ContextCompat.getDrawable(context, drawableId) is GradientDrawable)
        }
    }

    @Test
    fun launchCardDimensions_areStableAndCompact() {
        val res = themedCtx().resources
        val density = res.displayMetrics.density

        assertEquals(18f, res.getDimension(R.dimen.wk_app_card_radius) / density, 0.5f)
        assertEquals(24f, res.getDimension(R.dimen.wk_app_card_radius_large) / density, 0.5f)
        assertTrue(res.getDimensionPixelSize(R.dimen.wk_app_card_padding) >= res.getDimensionPixelSize(R.dimen.wk_ds_input_pill_padding_h))
        assertTrue(res.getDimensionPixelSize(R.dimen.wk_ds_input_bar_padding_bottom) >= res.getDimensionPixelSize(R.dimen.wk_ds_input_bar_padding_top))
    }
}
