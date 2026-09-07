import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";
import { GLOBAL_GROUP_ID } from "./globalGroup";

/**
 * Elimina un grupo por completo: el doc `groups/{groupId}`, todas sus subcolecciones
 * (`weeks/**` — incluyendo `picks`/`results` — y `board/**`), el árbol
 * `standings/{groupId}/members/**`, y la foto de grupo en Storage.
 *
 * Corre con Admin SDK (omite `firestore.rules`) porque el cliente no puede borrar
 * subcolecciones ni `standings` (`allow write: if false`). El cliente llega aquí vía la
 * Cloud Function callable `deleteGroup`.
 *
 * Solo el creador (`createdBy`) puede borrar su grupo; el grupo global nunca se puede
 * eliminar. Lanza `HttpsError` con el código adecuado en cada caso — el caller la
 * propaga tal cual al cliente.
 */
export async function deleteGroupCompletely(groupId: string, uid: string): Promise<void> {
  if (groupId === GLOBAL_GROUP_ID) {
    throw new HttpsError("permission-denied", "El grupo global no se puede eliminar.");
  }

  const db = getFirestore();
  const groupRef = db.collection("groups").doc(groupId);
  const groupSnap = await groupRef.get();
  if (!groupSnap.exists) {
    throw new HttpsError("not-found", "El grupo no existe.");
  }
  if (groupSnap.data()?.createdBy !== uid) {
    throw new HttpsError("permission-denied", "Solo el creador del grupo puede eliminarlo.");
  }

  // Borrar primero SOLO el doc del grupo: si `scoreGroupForWeek` / `scheduledScoring`
  // corre concurrentemente, su `groups/{groupId}.get()` verá el doc inexistente y
  // abortará temprano, en vez de re-crear `weeks/**/results` huérfanos tras el barrido.
  await groupRef.delete();

  // Barrer subcolecciones (`weeks/**`, `board/**`) — opera aunque el doc ya no exista —
  // y el árbol de standings del grupo.
  await db.recursiveDelete(groupRef);
  await db.recursiveDelete(db.collection("standings").doc(groupId));

  // Foto de grupo en Storage (si la hay). Un fallo aquí NO revierte el borrado ya hecho
  // en Firestore: se registra y se sigue, para no reportar como fallida una eliminación
  // que en realidad se completó.
  try {
    await getStorage().bucket().file(`group_photos/${groupId}`).delete();
  } catch (err) {
    logger.warn(`deleteGroupCompletely: no se pudo borrar la foto de group_photos/${groupId}`, err);
  }

  logger.info(`deleteGroupCompletely: grupo ${groupId} eliminado por ${uid}`);
}
