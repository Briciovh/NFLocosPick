package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * A country toggle (one button per entry in [countries]) followed by a digits-only national
 * number field. Filters non-digit input and caps at 10 digits — both US (+1) and Mexico (+52)
 * use a 10-digit national number, so no per-country masking is needed.
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

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            countries.forEach { country ->
                val isSelected = country.regionCode == selectedCountry.regionCode
                OutlinedButton(
                    onClick  = { onCountrySelected(country) },
                    enabled  = enabled,
                    modifier = Modifier.weight(1f),
                    shape    = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) appColors.primary else Color.Transparent,
                        contentColor   = if (isSelected) appColors.onPrimary else appColors.onBackground
                    ),
                    border = BorderStroke(1.dp, if (isSelected) appColors.primary else appColors.secondary)
                ) {
                    Text("${country.flagEmoji} +${country.dialCode}")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value          = nationalNumber,
            onValueChange  = { onNationalNumberChange(it.filter(Char::isDigit).take(10)) },
            label          = { Text(label) },
            singleLine     = true,
            enabled        = enabled,
            modifier       = Modifier.fillMaxWidth().then(numberFieldModifier),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = appColors.primary,
                unfocusedBorderColor = appColors.secondary,
                focusedTextColor     = appColors.onBackground,
                unfocusedTextColor   = appColors.onBackground,
                cursorColor          = appColors.primary
            )
        )
    }
}
