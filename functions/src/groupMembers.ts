import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";
import { GLOBAL_GROUP_ID } from "./globalGroup";

/**
 * Quita a un miembro de un grupo y, opcionalmente, lo bloquea para que no
 * pueda volver a unirse con el código de invitación.
 *
 * Si el usuario tenía standing en el grupo, se marca `hidden: true` (reversible,
 * mismo patrón de inactividad) para ocultar su fila del leaderboard sin destruir
 * su historial de puntos.
 *
 * Solo el creador del grupo (admin) puede invocar esto; el grupo global nunca
 * permite gestión de miembros; y el admin no puede quitarse a sí mismo.
 */
export async function removeGroupMember(
  groupId: string,
  requesterUid: string,
  targetUid: string,
  block: boolean
): Promise<void> {
  if (groupId === GLOBAL_GROUP_ID) {
    throw new HttpsError("permission-denied", "En el grupo global no se administran miembros.");
  }

  const db = getFirestore();
  const groupRef = db.collection("groups").doc(groupId);
  const groupSnap = await groupRef.get();
  if (!groupSnap.exists) {
    throw new HttpsError("not-found", "El grupo no existe.");
  }

  const data = groupSnap.data();
  if (data?.createdBy !== requesterUid) {
    throw new HttpsError("permission-denied", "Solo el administrador del grupo puede hacer esto.");
  }

  if (targetUid === data?.createdBy) {
    throw new HttpsError("invalid-argument", "El administrador no puede quitarse a sí mismo.");
  }

  // Ocultar el standing si existe (mismo patrón que inactivity.ts)
  const standingRef = db.collection("standings").doc(groupId).collection("members").doc(targetUid);
  const standingSnap = await standingRef.get();
  if (standingSnap.exists) {
    await standingRef.update({
      hidden: true,
      hiddenAt: FieldValue.serverTimestamp(),
    });
  }

  await groupRef.update({
    memberIds: FieldValue.arrayRemove(targetUid),
    ...(block ? { blockedIds: FieldValue.arrayUnion(targetUid) } : {}),
  });

  logger.info(`removeGroupMember: ${targetUid} quitado de ${groupId} por ${requesterUid} (block=${block})`);
}

/**
 * Desbloquea a un usuario previamente bloqueado de un grupo.
 *
 * Solo quita a [targetUid] de `blockedIds` para permitirle volver a unirse
 * con el código. NO lo re-agrega a `memberIds` (el usuario se re-une por sí
 * mismo) ni des-oculta su standing (eso ocurre al volver a entrar vía joinGroup).
 */
export async function unblockGroupMember(
  groupId: string,
  requesterUid: string,
  targetUid: string
): Promise<void> {
  if (groupId === GLOBAL_GROUP_ID) {
    throw new HttpsError("permission-denied", "En el grupo global no se administran miembros.");
  }

  const db = getFirestore();
  const groupRef = db.collection("groups").doc(groupId);
  const groupSnap = await groupRef.get();
  if (!groupSnap.exists) {
    throw new HttpsError("not-found", "El grupo no existe.");
  }

  const data = groupSnap.data();
  if (data?.createdBy !== requesterUid) {
    throw new HttpsError("permission-denied", "Solo el administrador del grupo puede hacer esto.");
  }

  await groupRef.update({
    blockedIds: FieldValue.arrayRemove(targetUid),
  });

  logger.info(`unblockGroupMember: ${targetUid} desbloqueado de ${groupId} por ${requesterUid}`);
}
