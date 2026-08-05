import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { fetchCurrentWeekGames, computeWinners } from "./espn";
import { scoreGroupForWeek } from "./scoring";

initializeApp();

/**
 * Puntuación automática. Corre cada 30 min, solo domingo/lunes/jueves
 * (días de partido NFL), horario America/New_York.
 *
 * Trae el scoreboard de ESPN UNA sola vez (la semana actual es la misma
 * para todos los grupos) y aplica el scoring a cada grupo existente.
 */
export const scheduledScoring = onSchedule(
  {
    schedule: "*/30 * * * 0,1,4",
    timeZone: "America/New_York",
  },
  async () => {
    const games = await fetchCurrentWeekGames();
    const finalGames = games.filter((g) => g.status === "FINAL");
    if (finalGames.length === 0) {
      logger.info("scheduledScoring: no hay juegos FINAL todavía, nada que hacer");
      return;
    }
    const weekId = games[0].weekId;
    const winners = computeWinners(games);

    const db = getFirestore();
    const groupsSnap = await db.collection("groups").get();

    let totalScored = 0;
    for (const groupDoc of groupsSnap.docs) {
      const memberIds: string[] = groupDoc.data().memberIds ?? [];
      if (memberIds.length === 0) continue;
      try {
        totalScored += await scoreGroupForWeek(groupDoc.id, weekId, memberIds, winners);
      } catch (err) {
        logger.error(`scheduledScoring: fallo puntuando grupo ${groupDoc.id}`, err);
      }
    }
    logger.info(`scheduledScoring: ${totalScored} picks puntuados en total (semana=${weekId})`);
  }
);

/**
 * Disparo manual desde la app ("Puntuar ahora" en la tarjeta de grupo).
 * Requiere estar autenticado y ser miembro del grupo.
 */
export const scoreGroupWeek = onCall<{ groupId?: string }>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  }
  const groupId = request.data.groupId;
  if (!groupId) {
    throw new HttpsError("invalid-argument", "Falta groupId.");
  }

  const db = getFirestore();
  const groupSnap = await db.collection("groups").doc(groupId).get();
  if (!groupSnap.exists) {
    throw new HttpsError("not-found", "El grupo no existe.");
  }
  const memberIds: string[] = groupSnap.data()?.memberIds ?? [];
  if (!memberIds.includes(uid)) {
    throw new HttpsError("permission-denied", "No eres miembro de este grupo.");
  }

  const games = await fetchCurrentWeekGames();
  const finalGames = games.filter((g) => g.status === "FINAL");
  if (finalGames.length === 0 || games.length === 0) {
    return { scoredCount: 0 };
  }
  const weekId = games[0].weekId;
  const winners = computeWinners(games);

  const scoredCount = await scoreGroupForWeek(groupId, weekId, memberIds, winners);
  return { scoredCount };
});
