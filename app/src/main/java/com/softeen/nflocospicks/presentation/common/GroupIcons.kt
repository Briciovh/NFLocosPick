package com.softeen.nflocospicks.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Set curado de íconos predefinidos que un creador de grupo puede elegir como
 * alternativa a subir una foto propia. La clave (`iconId`) es lo que se persiste
 * en `Group.iconId` — nunca cambiar una clave existente sin migrar los documentos
 * que ya la referencian.
 */
val groupIconMap: Map<String, ImageVector> = mapOf(
    "football" to Icons.Filled.SportsFootball,
    "trophy" to Icons.Filled.EmojiEvents,
    "shield" to Icons.Filled.Shield,
    "groups" to Icons.Filled.Groups,
    "medal" to Icons.Filled.MilitaryTech,
    "stadium" to Icons.Filled.Stadium,
    "fire" to Icons.Filled.Whatshot,
    "flag" to Icons.Filled.Flag
)
