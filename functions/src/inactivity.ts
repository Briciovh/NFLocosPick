import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000;

/**
 * Deshabilita (isActive=false) a todo usuario cuyo `lastActive` tenga más de un
 * año, y lo retira de standings/{groupId}/members/{uid} en TODOS los grupos a
 * los que pertenece (no solo el global) — mismo patrón de iteración que
 * accountDeletion.ts (`groups.where("memberIds", "array-contains", uid)`).
 * memberIds NO se toca: el requisito es "retirarse de las tablas de standings",
 * no expulsarlo del grupo.
 *
 * No hay lógica de reactivación separada: UserRepositoryImpl reescribe
 * `lastActive` en cada login, así que un usuario que vuelve a entrar
 * simplemente deja de aparecer en la próxima corrida — isActive nunca se
 * vuelve a poner en true explícitamente porque nadie más lo pone en false
 * salvo esta función.
 *
 * Idempotente: salta usuarios ya marcados isActive=false para no repetir
 * trabajo ni volver a intentar borrar standings ya borrados.
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
    await removeFromAllStandings(db, uid);
    await userDoc.ref.update({
      isActive: false,
      disabledAt: FieldValue.serverTimestamp(),
    });
    deactivated++;
    logger.info(`deactivateInactiveUsers: uid=${uid} deshabilitado por inactividad`);
  }

  return deactivated;
}

async function removeFromAllStandings(db: FirebaseFirestore.Firestore, uid: string): Promise<void> {
  const groupsSnap = await db.collection("groups").where("memberIds", "array-contains", uid).get();

  for (const groupDoc of groupsSnap.docs) {
    await db.collection("standings").doc(groupDoc.id).collection("members").doc(uid).delete();
  }
}
