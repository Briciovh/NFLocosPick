import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";

/**
 * Id fijo y reservado del grupo global "NFLocos de Corazón" (PR-16). Debe
 * coincidir exactamente con GlobalGroupConstants.GROUP_ID (app cliente, Kotlin)
 * y con el literal usado en firestore.rules (`groups/{groupId}` → `allow delete`).
 */
export const GLOBAL_GROUP_ID = "global_nflocos_de_corazon";

/**
 * Siembra standings/{GLOBAL_GROUP_ID}/members/{uid} en 0 puntos si todavía no
 * existe (PR-17). standings tiene `allow write: if false` para clientes, así que
 * esto solo puede correr vía Admin SDK — el cliente llama a la Cloud Function
 * "ensureGlobalStanding" justo después de auto-unirse al grupo global en su
 * primer sign-in (ver UserRepositoryImpl.ensureGlobalGroupMembership).
 * Idempotente: no toca el doc si ya existe, para no pisar puntos ya ganados.
 */
export async function seedGlobalStanding(uid: string): Promise<void> {
  const ref = getFirestore()
    .collection("standings").doc(GLOBAL_GROUP_ID)
    .collection("members").doc(uid);

  const snap = await ref.get();
  if (snap.exists) return;

  await ref.set({ totalPoints: 0, weeklyBreakdown: {} });
  logger.info(`seedGlobalStanding: standing sembrado para uid=${uid}`);
}
