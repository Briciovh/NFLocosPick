package com.softeen.nflocospicks.presentation.common

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import org.junit.Test

class NflTeamColorsTest {

    @Test
    fun `default team colors short-circuit to the Blue Steel palette`() {
        val app = defaultTeamColors.toAppColors()
        assertThat(app.primary).isEqualTo(BSGold)
        assertThat(app.onPrimary).isEqualTo(BSHeader)
        assertThat(app.background).isEqualTo(BSBg)
        assertThat(app.header).isEqualTo(BSHeader)
    }

    @Test
    fun `a non-default team derives its palette from accent and header`() {
        val accent = Color(0xFFFFB81C) // KC gold
        val header = Color(0xFF8B0014) // KC red
        val app = NflTeamColors(accent = accent, header = header).toAppColors()

        assertThat(app.primary).isEqualTo(accent)
        assertThat(app.onPrimary).isEqualTo(header)
        assertThat(app.surfaceVariant).isEqualTo(header) // card == header
        assertThat(app.onBackground).isEqualTo(Color.White)

        assertColorsClose(app.background, header.referenceDarken(0.4f))
        assertColorsClose(app.surface, header.referenceDarken(0.2f))
        assertColorsClose(app.primaryContainer, accent.referenceDarken(0.2f))
    }

    @Test
    fun `darken keeps alpha and scales rgb toward black`() {
        val opaqueMidGrey = Color(red = 0.5f, green = 0.5f, blue = 0.5f, alpha = 1f)
        val darkened = NflTeamColors(accent = BSGold, header = opaqueMidGrey).toAppColors().background
        // background == header.darken(0.4f) -> each channel * 0.6
        assertColorsClose(darkened, Color(red = 0.3f, green = 0.3f, blue = 0.3f, alpha = 1f))
    }

    @Test
    fun `darken preserves a partial alpha channel unchanged`() {
        // 0.4f is exact under Color's 8-bit sRGB alpha quantization (102/255).
        val translucent = Color(red = 0.8f, green = 0.4f, blue = 0.2f, alpha = 0.4f)
        val app = NflTeamColors(accent = translucent, header = translucent).toAppColors()
        // background/surface/primaryContainer are all darken()-derived; alpha must pass through,
        // not be forced back to 1.0.
        assertThat(app.background.alpha).isWithin(ALPHA_TOL).of(0.4f)
        assertThat(app.surface.alpha).isWithin(ALPHA_TOL).of(0.4f)
        assertThat(app.primaryContainer.alpha).isWithin(ALPHA_TOL).of(0.4f)
    }

    @Test
    fun `every mapped team produces a palette without throwing`() {
        nflTeamColorMap.forEach { (abbr, colors) ->
            val app = colors.toAppColors()
            assertThat(app.primary).isEqualTo(colors.accent)
            assertThat(abbr).isNotEmpty()
        }
    }

    private fun Color.referenceDarken(factor: Float) = Color(
        red = red * (1 - factor),
        green = green * (1 - factor),
        blue = blue * (1 - factor),
        alpha = alpha
    )

    private fun assertColorsClose(actual: Color, expected: Color) {
        assertThat(actual.red).isWithin(TOL).of(expected.red)
        assertThat(actual.green).isWithin(TOL).of(expected.green)
        assertThat(actual.blue).isWithin(TOL).of(expected.blue)
        assertThat(actual.alpha).isWithin(TOL).of(expected.alpha)
    }

    private companion object {
        const val TOL = 0.001f
        // Color stores channels in 8-bit sRGB; a round-trip can drift up to ~1/255.
        const val ALPHA_TOL = 0.01f
    }
}
