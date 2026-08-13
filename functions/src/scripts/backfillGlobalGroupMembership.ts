import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { GLOBAL_GROUP_ID, seedGlobalStanding } from "../globalGroup";

/**
 * Script de un solo uso (PR-17) — NO se exporta desde index.ts, así que nunca se
 * despliega como Cloud Function. Afilia a TODOS los usuarios existentes en
 * `users/` al grupo global (`memberIds`) y siembra su standing en 0 puntos —
 * el auto-join en UserRepositoryImpl.ensureGlobalGroupMembership solo corre
 * para usuarios nuevos desde que PR-17 se desplegó, así que quien ya tenía
 * cuenta antes de eso necesita este backfill.
 *
 * Idempotente: arrayUnion no duplica uids ya presentes, y seedGlobalStanding
 * no toca standings que ya existan.
 *
 * Cómo correrlo (mismas credenciales de Admin SDK que seedGlobalGroup.ts):
 *   npm run build && node lib/scripts/backfillGlobalGroupMembership.js
 */
async function main(): Promise<void> {
  initializeApp();
  const db = getFirestore();

  const usersSnap = await db.collection("users").get();
  const uids = usersSnap.docs.map((doc) => doc.id);
  console.log(`backfillGlobalGroupMembership: ${uids.length} usuarios encontrados en users/`);

  if (uids.length === 0) return;

  await db.collection("groups").doc(GLOBAL_GROUP_ID).update({
    memberIds: FieldValue.arrayUnion(...uids),
  });
  console.log(`backfillGlobalGroupMembership: ${uids.length} uids agregados a memberIds`);

  let seeded = 0;
  for (const uid of uids) {
    await seedGlobalStanding(uid);
    seeded++;
  }
  console.log(`backfillGlobalGroupMembership: standings confirmados/sembrados para ${seeded} usuarios`);
}

main().catch((err) => {
  console.error("backfillGlobalGroupMembership falló:", err);
  process.exit(1);
});
