package com.softeen.nflocospicks.presentation.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IconScaleOptionTest {

    @Test
    fun `fromKey resolves each known key to its option`() {
        assertThat(IconScaleOption.fromKey("pequeno")).isEqualTo(IconScaleOption.PEQUENO)
        assertThat(IconScaleOption.fromKey("mediano")).isEqualTo(IconScaleOption.MEDIANO)
        assertThat(IconScaleOption.fromKey("grande")).isEqualTo(IconScaleOption.GRANDE)
    }

    @Test
    fun `fromKey falls back to GRANDE for null`() {
        assertThat(IconScaleOption.fromKey(null)).isEqualTo(IconScaleOption.GRANDE)
    }

    @Test
    fun `fromKey falls back to GRANDE for an unknown or wrongly-cased key`() {
        assertThat(IconScaleOption.fromKey("")).isEqualTo(IconScaleOption.GRANDE)
        assertThat(IconScaleOption.fromKey("tiny")).isEqualTo(IconScaleOption.GRANDE)
        assertThat(IconScaleOption.fromKey("GRANDE")).isEqualTo(IconScaleOption.GRANDE)
        assertThat(IconScaleOption.fromKey("Mediano")).isEqualTo(IconScaleOption.GRANDE)
    }

    @Test
    fun `each key round-trips through fromKey`() {
        IconScaleOption.entries.forEach { option ->
            assertThat(IconScaleOption.fromKey(option.key)).isEqualTo(option)
        }
    }

    @Test
    fun `GRANDE is the full-size multiplier and options are ordered`() {
        assertThat(IconScaleOption.GRANDE.multiplier).isEqualTo(1.0f)
        assertThat(IconScaleOption.PEQUENO.multiplier).isLessThan(IconScaleOption.MEDIANO.multiplier)
        assertThat(IconScaleOption.MEDIANO.multiplier).isLessThan(IconScaleOption.GRANDE.multiplier)
    }
}
