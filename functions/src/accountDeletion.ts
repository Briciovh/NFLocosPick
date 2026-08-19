import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";

const DELETED_DISPLAY_NAME = "Usuario eliminado";

/**
 * Elimina la cuenta de [uid]: anonimiza su perfil y sus mensajes de tablero,
 * lo saca de memberIds de sus grupos, y borra su username, foto de perfil y
 * su registro de Firebase Auth.
 *
 * picks/results/standings NO se tocan — el nombre se resuelve en vivo desde
 * users/{uid}, que queda anonimizado en vez de borrado, así el historial y
 * el leaderboard del grupo no se rompen para el resto de los miembros.
 */
export async function deleteUserAccount(uid: string): Promise<void> {
  const db = getFirestore();

  const userRef = db.collection("users").doc(uid);
  const userSnap = await userRef.get();
  const username = userSnap.data()?.username as string | undefined;

  const groupsSnap = await db
    .collection("groups")
    .where("memberIds", "array-contains", uid)
    .get();

  for (const groupDoc of groupsSnap.docs) {
    await removeMemberFromGroup(db, groupDoc.id, groupDoc.data(), uid);
    await anonymizeBoardMessages(db, groupDoc.id, uid);
  }

  if (username) {
    // The reservation doc ID is always lowercase-normalized (see UserRepositoryImpl.
    // updateProfile on the client) while users/{uid}.username preserves the typed casing —
    // deleting by the raw casing here missed the doc whenever it had any uppercase letter,
    // leaving an orphaned reservation that made the username look permanently "taken".
    await db.collection("usernames").doc(username.toLowerCase()).delete();
  }

  await userRef.set(
    {
      displayName: DELETED_DISPLAY_NAME,
      email: null,
      phoneNumber: null,
      photoUrl: null,
      username: null,
      deletedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  try {
    await getStorage().bucket().file(`profile_photos/${uid}`).delete();
  } catch (err) {
    logger.info(`deleteUserAccount: sin foto de perfil que borrar para ${uid}`, err);
  }

  await getAuth().deleteUser(uid);
  logger.info(`deleteUserAccount: cuenta ${uid} eliminada`);
}

/**
 * Saca a [uid] de memberIds. Si era el creador/admin del grupo, transfiere
 * createdBy al primer miembro que quede — de lo contrario las acciones de
 * admin sobre el tablero (ver firestore.rules) quedarían inutilizables para
 * siempre, ya que apuntarían a un uid que ya no puede autenticarse.
 */
async function removeMemberFromGroup(
  db: FirebaseFirestore.Firestore,
  groupId: string,
  groupData: FirebaseFirestore.DocumentData,
  uid: string
): Promise<void> {
  const memberIds: string[] = groupData.memberIds ?? [];
  const remaining = memberIds.filter((id) => id !== uid);

  const update: Record<string, unknown> = {
    memberIds: FieldValue.arrayRemove(uid),
  };
  if (groupData.createdBy === uid && remaining.length > 0) {
    update.createdBy = remaining[0];
  }

  await db.collection("groups").doc(groupId).update(update);
}

/**
 * senderName/senderPhotoUrl están denormalizados en cada mensaje del
 * tablero (ver FirebaseBoardDataSource/BoardMessage) para no tener que
 * resolver el nombre en vivo al listar mensajes — por eso hay que
 * actualizarlos acá explícitamente, a diferencia de picks/standings.
 */
async function anonymizeBoardMessages(
  db: FirebaseFirestore.Firestore,
  groupId: string,
  uid: string
): Promise<void> {
  const messagesSnap = await db
    .collection("groups")
    .doc(groupId)
    .collection("board")
    .where("senderId", "==", uid)
    .get();

  if (messagesSnap.empty) return;

  const batch = db.batch();
  for (const doc of messagesSnap.docs) {
    batch.update(doc.ref, {
      senderName: DELETED_DISPLAY_NAME,
      senderPhotoUrl: null,
    });
  }
  await batch.commit();
}
