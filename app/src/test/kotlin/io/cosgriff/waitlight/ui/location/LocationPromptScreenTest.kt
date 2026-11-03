package io.cosgriff.waitlight.ui.location

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.ui.TALL_PHONE
import io.cosgriff.waitlight.ui.hasTextSpaced
import io.cosgriff.waitlight.ui.text
import io.cosgriff.waitlight.ui.theme.WaitlightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The location prompt's ZIP code entry, drawn from plain state. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class LocationPromptScreenTest {
    @get:Rule val compose = createComposeRule()

    private val typed = mutableListOf<String>()
    private var goes = 0
    private var clears = 0
    private var skips = 0

    private fun show(zip: ZipFieldUi) {
        compose.setContent {
            WaitlightTheme(dark = false) {
                LocationPromptContent(
                    zip = zip,
                    onUseLocation = {},
                    onZipChange = { typed += it },
                    onGo = { goes++ },
                    onClearZip = { clears++ },
                    onSkip = { skips++ },
                )
            }
        }
    }

    private fun shown(text: String) = compose.onNode(hasTextSpaced(text)).assertExists()
    private fun notShown(text: String) = compose.onNode(hasTextSpaced(text)).assertDoesNotExist()
    private fun field() = compose.onNodeWithText(text(R.string.location_zip_label))

    @Test
    fun `a ZIP field and Go under the location button`() {
        show(ZipFieldUi())
        shown(text(R.string.location_use))
        shown(text(R.string.location_or))
        shown(text(R.string.location_zip_go))
        notShown(text(R.string.location_zip_incomplete))

        field().performTextInput("80003")
        assertEquals(listOf("80003"), typed)
        compose.onNodeWithText(text(R.string.location_zip_go)).performClick()
        assertEquals(1, goes)
        // The keyboard's Go does the same.
        field().performImeAction()
        assertEquals(2, goes)
    }

    @Test
    fun `too few digits - asks for five`() {
        show(ZipFieldUi(text = "800", error = ZipError.INCOMPLETE))
        shown(text(R.string.location_zip_incomplete))
    }

    @Test
    fun `an unknown ZIP - says it isn't Colorado's`() {
        show(ZipFieldUi(text = "90210", error = ZipError.UNKNOWN))
        shown("90210 isn’t a Colorado ZIP code")
    }

    @Test
    fun `a saved ZIP can be cleared here`() {
        show(ZipFieldUi(text = "80003", saved = "80003"))
        compose.onNodeWithText(text(R.string.location_zip_clear, "80003")).performClick()
        assertEquals(1, clears)
    }

    @Test
    fun `no saved ZIP - nothing to clear`() {
        show(ZipFieldUi())
        notShown(text(R.string.location_zip_clear, "80003"))
        compose.onNodeWithText(text(R.string.location_skip)).performClick()
        assertEquals(1, skips)
    }
}
