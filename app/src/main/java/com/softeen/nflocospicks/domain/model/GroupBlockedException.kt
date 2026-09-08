package com.softeen.nflocospicks.domain.model

/**
 * El usuario intentó unirse a un grupo del que fue bloqueado por el admin
 * (su uid está en `blockedIds`). Sin texto de despliegue — la capa de UI lo
 * mapea a un string localizado, igual que [AuthError].
 */
class GroupBlockedException : Exception("blocked from group")
