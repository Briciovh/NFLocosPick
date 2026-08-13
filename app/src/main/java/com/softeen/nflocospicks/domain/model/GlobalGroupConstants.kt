package com.softeen.nflocospicks.domain.model

/**
 * Id fijo y reservado del grupo global "NFLocos de Corazón" (PR-16), sembrado una sola
 * vez vía Admin SDK (ver functions/src/scripts/seedGlobalGroup.ts). Debe coincidir
 * exactamente con el groupId usado en firestore.rules y en el script de siembra.
 */
object GlobalGroupConstants {
    const val GROUP_ID = "global_nflocos_de_corazon"
}
