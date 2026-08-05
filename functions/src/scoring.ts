import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";

interface PickEntry {
  pickedTeam?: string;
}

interface ResultEntry {
  isCorrect?: boolean;
  scoredAt?: number;
  winnerTeamAbbr?: string;
}

/**
 * Puntúa los picks de un grupo para [weekId] dados los ganadores ya calculados.
 *
 * El resultado (isCorrect/scoredAt/winnerTeamAbbr) se escribe en
 * weeks/{weekId}/results/{userId} — NUNCA en el doc de picks del usuario.
 * picks/{userId} solo contiene pickedTeam y es escribible por el dueño; si el
 * resultado viviera ahí, un cliente podría auto-marcarse isCorrect:true con
 * una llamada directa al SDK. results/{userId} tiene allow write: if false
 * en las reglas — solo Admin SDK (esta función) puede tocarlo.
 *
 * Idempotente: los picks ya presentes en el doc de resultados se ignoran.
 * Retorna el número de picks recién puntuados.
 */
export async function scoreGroupForWeek(
  groupId: string,
  weekId: string,
  memberIds: string[],
  winners: Map<string, string | null>
): Promise<number> {
  const db = getFirestore();
  const batch = db.batch();
  const nowMs = Date.now();
  let scored = 0;

  for (const userId of memberIds) {
    const picksRef = db
      .collection("groups").doc(groupId)
      .collection("weeks").doc(weekId)
      .collection("picks").doc(userId);
    const resultsRef = db
      .collection("groups").doc(groupId)
      .collection("weeks").doc(weekId)
      .collection("results").doc(userId);

    const [picksSnap, resultsSnap] = await Promise.all([picksRef.get(), resultsRef.get()]);
    if (!picksSnap.exists) continue;

    const picks = (picksSnap.data() ?? {}) as Record<string, PickEntry>;
    const existingResults = (resultsSnap.data() ?? {}) as Record<string, ResultEntry>;

    const unsettled = Object.entries(picks).filter(
      ([gameId]) => existingResults[gameId] === undefined && winners.has(gameId)
    );
    if (unsettled.length === 0) continue;

    const resultUpdate: Record<string, ResultEntry> = {};
    let newlyCorrect = 0;

    for (const [gameId, pick] of unsettled) {
      const winner = winners.get(gameId) ?? null;
      const correct = winner !== null && pick.pickedTeam === winner;
      if (correct) newlyCorrect++;
      resultUpdate[gameId] = {
        isCorrect: correct,
        scoredAt: nowMs,
        winnerTeamAbbr: winner ?? "",
      };
      scored++;
    }
    batch.set(resultsRef, resultUpdate, { merge: true });

    const alreadyCorrect = Object.values(existingResults).filter((r) => r.isCorrect === true).length;
    const totalWeekCorrect = alreadyCorrect + newlyCorrect;

    const standingsRef = db
      .collection("standings").doc(groupId)
      .collection("members").doc(userId);

    const standingsSnap = await standingsRef.get();
    const existingBreakdown = (standingsSnap.data()?.weeklyBreakdown ?? {}) as Record<string, number>;
    const newBreakdown = { ...existingBreakdown, [weekId]: totalWeekCorrect };
    const totalPoints = Object.values(newBreakdown).reduce((sum, v) => sum + (v ?? 0), 0);

    batch.set(standingsRef, {
      totalPoints,
      weeklyBreakdown: newBreakdown,
    });
  }

  if (scored > 0) {
    await batch.commit();
    logger.info(`Puntuados ${scored} picks — grupo=${groupId} semana=${weekId}`);
  }
  return scored;
}
