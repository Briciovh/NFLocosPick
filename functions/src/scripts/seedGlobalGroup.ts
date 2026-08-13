import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { GLOBAL_GROUP_ID } from "../globalGroup";

/**
 * Script de un solo uso (PR-16) — NO se exporta desde index.ts, así que nunca se
 * despliega como Cloud Function. Crea el doc `groups/{GLOBAL_GROUP_ID}` del grupo
 * global "NFLocos de Corazón" una única vez. Ya se corrió en producción — se deja
 * en el repo por trazabilidad y por si algún día hay que re-crear el grupo desde cero.
 *
 * GLOBAL_ADMIN_UID es el uid de nezaboost@gmail.com / username "saulbrisniega",
 * resuelto vía el doc `usernames/saulbrisniega` en Firestore — nunca se hardcodea
 * el email, solo el uid ya resuelto.
 *
 * Cómo correrlo (requiere credenciales de Admin SDK — service account o
 * `gcloud auth application-default login` con permisos sobre el proyecto
 * nflocospicks):
 *   npm run build && node lib/scripts/seedGlobalGroup.js
 */
const GLOBAL_GROUP_NAME = "NFLocos de Corazón";
const GLOBAL_ADMIN_UID = "VNN8bcyqaNhwGrIQYMabl4UIiIc2";

function randomInviteCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}

async function main(): Promise<void> {
  initializeApp();
  const db = getFirestore();
  const ref = db.collection("groups").doc(GLOBAL_GROUP_ID);

  const existing = await ref.get();
  if (existing.exists) {
    console.log(
      `groups/${GLOBAL_GROUP_ID} ya existe — no se sobreescribe. ` +
        "Bórralo manualmente desde la consola de Firebase primero si de verdad quieres re-sembrarlo."
    );
    return;
  }

  await ref.set({
    name: GLOBAL_GROUP_NAME,
    inviteCode: randomInviteCode(),
    createdBy: GLOBAL_ADMIN_UID,
    memberIds: [GLOBAL_ADMIN_UID],
  });

  console.log(`groups/${GLOBAL_GROUP_ID} creado. createdBy=${GLOBAL_ADMIN_UID}`);
}

main().catch((err) => {
  console.error("seedGlobalGroup falló:", err);
  process.exit(1);
});
