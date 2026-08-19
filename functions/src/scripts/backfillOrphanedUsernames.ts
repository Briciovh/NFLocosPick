import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Script de un solo uso — NO se exporta desde index.ts, así que nunca se despliega
 * como Cloud Function. Limpia reservaciones huérfanas en `usernames/{id}` dejadas
 * por un bug ya corregido en accountDeletion.ts: ese bug borraba la reservación
 * usando el username con la casing original en vez del ID normalizado a minúsculas,
 * así que el `.delete()` era un no-op silencioso para cualquier username con
 * mayúsculas — el doc de reserva quedaba existiendo sin dueño real, y un usuario
 * que luego intentaba reclamar (o reclamar de nuevo) ese mismo username se topaba
 * con "That username is already taken" aunque nadie lo tuviera.
 *
 * Un doc de `usernames/{id}` se considera huérfano si:
 *   - el `users/{userId}` referenciado ya no existe, o
 *   - existe pero su `username` (normalizado a minúsculas) ya no coincide con `id`
 *     (la cuenta cambió de username o fue anonimizada por borrado de cuenta).
 *
 * Cómo correrlo (mismas credenciales de Admin SDK que los scripts de PR-16/17/20):
 *   npm run build && node lib/scripts/backfillOrphanedUsernames.js
 */
async function main(): Promise<void> {
  initializeApp();
  const db = getFirestore();

  const usernamesSnap = await db.collection("usernames").get();
  console.log(`backfillOrphanedUsernames: ${usernamesSnap.size} reservaciones encontradas`);

  const orphaned: FirebaseFirestore.QueryDocumentSnapshot[] = [];

  for (const doc of usernamesSnap.docs) {
    const userId = doc.data()?.userId as string | undefined;
    if (!userId) {
      orphaned.push(doc);
      continue;
    }

    const userSnap = await db.collection("users").doc(userId).get();
    const currentUsername = userSnap.data()?.username as string | undefined;

    if (!userSnap.exists || currentUsername?.toLowerCase() !== doc.id) {
      orphaned.push(doc);
    }
  }

  console.log(`backfillOrphanedUsernames: ${orphaned.length} reservaciones huérfanas encontradas`);
  if (orphaned.length === 0) return;

  const batch = db.batch();
  for (const doc of orphaned) {
    batch.delete(doc.ref);
  }
  await batch.commit();

  console.log(`backfillOrphanedUsernames: ${orphaned.length} reservaciones huérfanas borradas`);
}

main().catch((err) => {
  console.error("backfillOrphanedUsernames falló:", err);
  process.exit(1);
});
