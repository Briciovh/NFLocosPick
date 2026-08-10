package com.softeen.nflocospicks.presentation.common

import android.content.Context
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale

data class CountryDialCode(
    val regionCode: String,
    val dialCode: String,
    val displayName: String,
    val flagEmoji: String,
    val nationalNumberLength: Int
)

/** Countries accepted for phone sign-in / phone linking — US, Mexico, and Spain, for now. */
private val SUPPORTED_REGIONS = listOf("US", "MX", "ES")

/** National significant number length for regions whose mobile example number is missing from
 *  libphonenumber's metadata — should not happen for any [SUPPORTED_REGIONS] entry, but avoids
 *  an unusable 0-digit cap if it ever does. */
private const val FALLBACK_NATIONAL_NUMBER_LENGTH = 10

fun supportedCountryDialCodes(context: Context): List<CountryDialCode> {
    val phoneNumberUtil = PhoneNumberUtil.createInstance(context)
    return SUPPORTED_REGIONS.map { region ->
        val exampleMobileNumber = phoneNumberUtil.getExampleNumberForType(
            region,
            PhoneNumberUtil.PhoneNumberType.MOBILE
        )
        CountryDialCode(
            regionCode = region,
            dialCode = phoneNumberUtil.getCountryCodeForRegion(region).toString(),
            displayName = Locale.Builder().setRegion(region).build().displayCountry,
            flagEmoji = flagEmoji(region),
            nationalNumberLength = exampleMobileNumber
                ?.let { phoneNumberUtil.getNationalSignificantNumber(it).length }
                ?: FALLBACK_NATIONAL_NUMBER_LENGTH
        )
    }
}

/** Builds a flag emoji from an ISO region code via the regional-indicator Unicode block. */
fun flagEmoji(regionCode: String): String =
    regionCode.uppercase().map { 0x1F1E6 + (it - 'A') }
        .joinToString(separator = "") { String(Character.toChars(it)) }

/** Prefers the device's locale region when supported, otherwise falls back to the US. */
fun defaultCountry(countries: List<CountryDialCode>): CountryDialCode {
    val deviceRegion = Locale.getDefault().country
    return countries.find { it.regionCode == deviceRegion }
        ?: countries.find { it.regionCode == "US" }
        ?: countries.first()
}

/** National number length varies per country (e.g. 10 digits for US/MX, 9 for Spain) — see
 *  [CountryDialCode.nationalNumberLength] — so no masking is applied here beyond the dial code
 *  prefix; callers cap/validate digit count against that field. */
fun composeE164(country: CountryDialCode, nationalNumber: String): String =
    "+${country.dialCode}$nationalNumber"
