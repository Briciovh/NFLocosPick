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
 *
 * El check-then-set corre dentro de una transacción (antes era un get()+set()
 * suelto): si scoreGroupForWeek puntúa un pick de este uid justo entre el
 * chequeo y la escritura, la transacción de Firestore reintenta esta función
 * al detectar que el doc cambió, en vez de pisar los puntos recién anotados
 * con { totalPoints: 0 } (hallazgo Antigravity, sept 2026, ver
 * docs/plans/global-default-group.md — el lado de scoreGroupForWeek de esta
 * misma carrera queda fuera de alcance de este fix, ver esa nota en el plan).
 */
export async function seedGlobalStanding(uid: string): Promise<void> {
  const db = getFirestore();
  const ref = db
    .collection("standings").doc(GLOBAL_GROUP_ID)
    .collection("members").doc(uid);

  const seeded = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (snap.exists) return false;
    tx.set(ref, { totalPoints: 0, weeklyBreakdown: {} });
    return true;
  });

  if (seeded) {
    logger.info(`seedGlobalStanding: standing sembrado para uid=${uid}`);
  }
}
