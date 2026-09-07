package com.softeen.nflocospicks.presentation.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypographyScaledByTest {

    @Test
    fun `factor of 1 leaves every role's size and line height unchanged`() {
        val scaled = Typography.scaledBy(1f)
        assertThat(scaled.displayLarge.fontSize.value).isWithin(TOL).of(Typography.displayLarge.fontSize.value)
        assertThat(scaled.bodyMedium.fontSize.value).isWithin(TOL).of(Typography.bodyMedium.fontSize.value)
        assertThat(scaled.labelSmall.lineHeight.value).isWithin(TOL).of(Typography.labelSmall.lineHeight.value)
    }

    @Test
    fun `factor scales font size and line height of every role linearly`() {
        val factor = 1.15f
        val scaled = Typography.scaledBy(factor)

        val roles = listOf(
            Typography.displayLarge to scaled.displayLarge,
            Typography.displayMedium to scaled.displayMedium,
            Typography.displaySmall to scaled.displaySmall,
            Typography.headlineLarge to scaled.headlineLarge,
            Typography.headlineMedium to scaled.headlineMedium,
            Typography.headlineSmall to scaled.headlineSmall,
            Typography.titleLarge to scaled.titleLarge,
            Typography.titleMedium to scaled.titleMedium,
            Typography.titleSmall to scaled.titleSmall,
            Typography.bodyLarge to scaled.bodyLarge,
            Typography.bodyMedium to scaled.bodyMedium,
            Typography.bodySmall to scaled.bodySmall,
            Typography.labelLarge to scaled.labelLarge,
            Typography.labelMedium to scaled.labelMedium,
            Typography.labelSmall to scaled.labelSmall,
        )
        roles.forEach { (base, out) ->
            assertThat(out.fontSize.value).isWithin(TOL).of(base.fontSize.value * factor)
            assertThat(out.lineHeight.value).isWithin(TOL).of(base.lineHeight.value * factor)
        }
    }

    @Test
    fun `scaling preserves weight family and letter spacing`() {
        val scaled = Typography.scaledBy(0.9f)
        assertThat(scaled.titleMedium.fontWeight).isEqualTo(Typography.titleMedium.fontWeight)
        assertThat(scaled.titleMedium.fontFamily).isEqualTo(Typography.titleMedium.fontFamily)
        assertThat(scaled.titleMedium.letterSpacing.value)
            .isWithin(TOL).of(Typography.titleMedium.letterSpacing.value)
    }

    @Test
    fun `scaling is not applied in place`() {
        val originalDisplayLarge = Typography.displayLarge.fontSize.value
        Typography.scaledBy(2f)
        assertThat(Typography.displayLarge.fontSize.value).isWithin(TOL).of(originalDisplayLarge)
    }

    private companion object {
        const val TOL = 0.01f
    }
}
