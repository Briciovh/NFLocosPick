import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000;

/**
 * Deshabilita (isActive=false) a todo usuario cuyo `lastActive` tenga más de un
 * año, y OCULTA (no borra) su standing en standings/{groupId}/members/{uid} en
 * TODOS los grupos a los que pertenece (no solo el global) — mismo patrón de
 * iteración que accountDeletion.ts (`groups.where("memberIds", "array-contains", uid)`).
 * memberIds NO se toca: el requisito es "retirarse de las tablas de standings",
 * no expulsarlo del grupo.
 *
 * Antes esto hacía un .delete() del standing completo, perdiendo totalPoints y
 * weeklyBreakdown para siempre sin posibilidad de recuperarlos. Ahora solo se
 * marca `hidden: true` — reactivateUser() de abajo es quien restaura estos
 * docs ocultos, con su historial intacto, cuando el usuario vuelve a entrar
 * (hallazgo Codex+Antigravity, sept 2026, ver docs/plans/global-default-group.md).
 *
 * Idempotente: salta usuarios ya marcados isActive=false para no repetir
 * trabajo ni volver a ocultar standings ya ocultos.
 *
 * Retorna el número de cuentas deshabilitadas en esta corrida.
 */
export async function deactivateInactiveUsers(): Promise<number> {
  const db = getFirestore();
  const cutoff = Timestamp.fromMillis(Date.now() - ONE_YEAR_MS);

  const usersSnap = await db.collection("users").where("lastActive", "<", cutoff).get();

  let deactivated = 0;
  for (const userDoc of usersSnap.docs) {
    if (userDoc.data().isActive === false) continue;

    const uid = userDoc.id;
    await setStandingsHidden(db, uid, true);
    await userDoc.ref.update({
      isActive: false,
      disabledAt: FieldValue.serverTimestamp(),
    });
    deactivated++;
    logger.info(`deactivateInactiveUsers: uid=${uid} deshabilitado por inactividad`);
  }

  return deactivated;
}

/**
 * Reactiva a [uid]: pone isActive=true, limpia disabledAt, y des-oculta sus
 * standings archivados (hidden=false) en todos sus grupos, restaurando
 * totalPoints y weeklyBreakdown tal como estaban antes de la desactivación.
 *
 * La llama la Cloud Function "reactivateAccount" (ver index.ts), invocada por
 * el cliente en cada login mientras isActive siga false (ver
 * UserRepositoryImpl.kt) — isActive/disabledAt están bloqueados para
 * escritura de cliente en firestore.rules, así que solo Admin SDK puede
 * revertirlos. No-op si la cuenta ya está activa, para que reintentar sea
 * seguro (mismo patrón idempotente que ensureGlobalGroupMembership).
 */
export async function reactivateUser(uid: string): Promise<void> {
  const db = getFirestore();
  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  if (userSnap.data()?.isActive !== false) return;

  await setStandingsHidden(db, uid, false);
  await userRef.update({
    isActive: true,
    disabledAt: FieldValue.delete(),
  });
  logger.info(`reactivateUser: uid=${uid} reactivado, standings restaurados`);
}

/**
 * Marca (u des-marca) `hidden` en el standing de [uid] en cada grupo al que
 * pertenece. Salta grupos donde el usuario todavía no tiene standing (nunca
 * puntuó ahí) — no hay nada que ocultar/restaurar.
 */
async function setStandingsHidden(
  db: FirebaseFirestore.Firestore,
  uid: string,
  hidden: boolean
): Promise<void> {
  const groupsSnap = await db.collection("groups").where("memberIds", "array-contains", uid).get();

  for (const groupDoc of groupsSnap.docs) {
    const standingRef = db.collection("standings").doc(groupDoc.id).collection("members").doc(uid);
    const standingSnap = await standingRef.get();
    if (!standingSnap.exists) continue;

    await standingRef.update(
      hidden
        ? { hidden: true, hiddenAt: FieldValue.serverTimestamp() }
        : { hidden: FieldValue.delete(), hiddenAt: FieldValue.delete() }
    );
  }
}
