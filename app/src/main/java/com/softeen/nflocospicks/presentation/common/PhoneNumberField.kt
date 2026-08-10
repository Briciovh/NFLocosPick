package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * A single text field whose `leadingIcon` slot is a compact country-code selector (flag + dial
 * code, opens a dropdown menu of [countries] on tap) instead of a separate composable next to
 * the field — Material3 vertically centers `leadingIcon` using its own internal text field
 * layout, which guarantees pixel-perfect alignment with the input text regardless of label/font
 * metrics, rather than approximating it from outside. Filters non-digit input and caps digit
 * count at the selected country's [CountryDialCode.nationalNumberLength] since it varies per
 * country (e.g. 10 for US/MX, 9 for Spain).
 */
@Composable
fun PhoneNumberField(
    countries: List<CountryDialCode>,
    selectedCountry: CountryDialCode,
    onCountrySelected: (CountryDialCode) -> Unit,
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numberFieldModifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val appColors = LocalAppColors.current
    var isMenuExpanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value          = nationalNumber,
        onValueChange  = {
            onNationalNumberChange(it.filter(Char::isDigit).take(selectedCountry.nationalNumberLength))
        },
        label          = { Text(label) },
        singleLine     = true,
        enabled        = enabled,
        modifier       = modifier.fillMaxWidth().then(numberFieldModifier),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        leadingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        modifier = Modifier
                            .clickable(enabled = enabled) { isMenuExpanded = true }
                            .testTag(TestTags.PHONE_COUNTRY_SELECTOR)
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "${selectedCountry.flagEmoji} +${selectedCountry.dialCode}",
                            color = appColors.onBackground
                        )
                        Icon(
                            imageVector        = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.cd_phone_country_selector),
                            tint               = appColors.secondary
                        )
                    }

                    DropdownMenu(
                        expanded         = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        countries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text("${country.flagEmoji} ${country.displayName} +${country.dialCode}") },
                                onClick = {
                                    onCountrySelected(country)
                                    isMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    color = appColors.secondary
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = appColors.primary,
            unfocusedBorderColor = appColors.secondary,
            focusedTextColor     = appColors.onBackground,
            unfocusedTextColor   = appColors.onBackground,
            cursorColor          = appColors.primary
        )
    )
}
