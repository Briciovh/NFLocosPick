package com.softeen.nflocospicks.presentation.common

import android.content.Context
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale

data class CountryDialCode(
    val regionCode: String,
    val dialCode: String,
    val displayName: String,
    val flagEmoji: String
)

/** Countries accepted for phone sign-in / phone linking — US and Mexico only, for now. */
private val SUPPORTED_REGIONS = listOf("US", "MX")

fun supportedCountryDialCodes(context: Context): List<CountryDialCode> {
    val phoneNumberUtil = PhoneNumberUtil.createInstance(context)
    return SUPPORTED_REGIONS.map { region ->
        CountryDialCode(
            regionCode = region,
            dialCode = phoneNumberUtil.getCountryCodeForRegion(region).toString(),
            displayName = Locale.Builder().setRegion(region).build().displayCountry,
            flagEmoji = flagEmoji(region)
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

/** Both US (+1) and Mexico (+52) use a 10-digit national number since Mexico dropped its
 *  extra mobile "1" prefix from the international format in 2019 — no per-country masking
 *  or length differences are needed. */
fun composeE164(country: CountryDialCode, nationalNumber: String): String =
    "+${country.dialCode}$nationalNumber"
