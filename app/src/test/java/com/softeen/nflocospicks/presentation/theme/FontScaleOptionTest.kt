package com.softeen.nflocospicks.presentation.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FontScaleOptionTest {

    @Test
    fun `fromKey resolves each known key to its option`() {
        assertThat(FontScaleOption.fromKey("pequeno")).isEqualTo(FontScaleOption.PEQUENO)
        assertThat(FontScaleOption.fromKey("normal")).isEqualTo(FontScaleOption.NORMAL)
        assertThat(FontScaleOption.fromKey("grande")).isEqualTo(FontScaleOption.GRANDE)
    }

    @Test
    fun `fromKey falls back to NORMAL for null`() {
        assertThat(FontScaleOption.fromKey(null)).isEqualTo(FontScaleOption.NORMAL)
    }

    @Test
    fun `fromKey falls back to NORMAL for an unknown or wrongly-cased key`() {
        assertThat(FontScaleOption.fromKey("")).isEqualTo(FontScaleOption.NORMAL)
        assertThat(FontScaleOption.fromKey("huge")).isEqualTo(FontScaleOption.NORMAL)
        assertThat(FontScaleOption.fromKey("NORMAL")).isEqualTo(FontScaleOption.NORMAL)
        assertThat(FontScaleOption.fromKey("Pequeno")).isEqualTo(FontScaleOption.NORMAL)
    }

    @Test
    fun `each key round-trips through fromKey`() {
        FontScaleOption.entries.forEach { option ->
            assertThat(FontScaleOption.fromKey(option.key)).isEqualTo(option)
        }
    }

    @Test
    fun `multipliers are ordered and NORMAL is identity`() {
        assertThat(FontScaleOption.NORMAL.multiplier).isEqualTo(1.0f)
        assertThat(FontScaleOption.PEQUENO.multiplier).isLessThan(FontScaleOption.NORMAL.multiplier)
        assertThat(FontScaleOption.GRANDE.multiplier).isGreaterThan(FontScaleOption.NORMAL.multiplier)
    }
}
