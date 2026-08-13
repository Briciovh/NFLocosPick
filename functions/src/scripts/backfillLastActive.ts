import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

/**
 * Script de un solo uso (PR-20) — NO se exporta desde index.ts, así que nunca se
 * despliega como Cloud Function. Estampa `lastActive = ahora` en todo usuario
 * existente que no lo tenga.
 *
 * Por qué hace falta: `scheduledInactivityCheck` (functions/src/inactivity.ts)
 * solo puede deshabilitar a un usuario cuyo doc TENGA el campo `lastActive` —
 * un `where("lastActive", "<", cutoff)` nunca hace match si el campo no existe.
 * Sin este backfill, ningún usuario que se registró antes de PR-20 entraría
 * jamás al scan, sin importar cuánto tiempo lleve inactivo. Como no sabemos
 * cuándo fue su actividad real más reciente, "ahora" es la base más justa: el
 * año de inactividad empieza a contar desde el día que se desplegó esta
 * feature, no se asume retroactivamente que ya llevan tiempo inactivos.
 *
 * Cómo correrlo (mismas credenciales de Admin SDK que los scripts de PR-16/17):
 *   npm run build && node lib/scripts/backfillLastActive.js
 */
async function main(): Promise<void> {
  initializeApp();
  const db = getFirestore();

  const usersSnap = await db.collection("users").get();
  const missing = usersSnap.docs.filter((doc) => !doc.data().lastActive);
  console.log(
    `backfillLastActive: ${usersSnap.size} usuarios encontrados, ${missing.length} sin lastActive`
  );

  if (missing.length === 0) return;

  const batch = db.batch();
  for (const doc of missing) {
    batch.update(doc.ref, { lastActive: FieldValue.serverTimestamp() });
  }
  await batch.commit();

  console.log(`backfillLastActive: lastActive estampado para ${missing.length} usuarios`);
}

main().catch((err) => {
  console.error("backfillLastActive falló:", err);
  process.exit(1);
});
