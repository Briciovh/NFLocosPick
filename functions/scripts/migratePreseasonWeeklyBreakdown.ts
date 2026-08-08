/**
 * Migración única de datos de producción — NO se despliega como Cloud Function.
 *
 * Antes de este feature, weekId no distinguía pre-temporada de temporada regular
 * (ambas producían "{year}-week-{NN}"). Semanas de pre-temporada ya puntuadas antes
 * de este cambio quedaron guardadas en standings/{groupId}/members/{userId}
 * .weeklyBreakdown bajo ese formato ambiguo, y el nuevo split cliente-side las
 * contaría como temporada regular a menos que se renombren a "{year}-pre-week-{NN}".
 *
 * Qué hace:
 *   1. Consulta el scoreboard histórico de ESPN con parámetros EXPLÍCITOS
 *      (?seasontype=1&week=N&dates=YYYY) para los años dados, y arma el set de
 *      event ids que ESPN confirma como pre-temporada por (año, semana).
 *   2. Recorre groups/{groupId}/weeks/{weekId} (el cache de partidos por semana que
 *      ya escribe ScheduleRepositoryImpl) buscando weekIds con el formato viejo
 *      "{year}-week-{NN}" (NN <= 4) cuyos game ids coincidan TODOS con el set de
 *      pre-temporada de ESPN para ese (año, semana).
 *   3. Para cada standings/{groupId}/members/{userId} cuyo weeklyBreakdown tenga
 *      alguna de esas claves viejas, renombra la clave (mueve el valor, borra la
 *      vieja) en un WriteBatch. totalPoints NO se toca — renombrar una clave no
 *      cambia la suma.
 *
 * Es idempotente: una segunda corrida no encuentra más claves viejas que migrar.
 * Por default corre en modo lectura (--dry-run); hay que pasar --execute explícito
 * para escribir. Revisar el diff impreso de --dry-run contra producción ANTES de
 * correr --execute — esto muta datos reales de usuarios y no tiene un "deshacer"
 * automático (recomendado: exportar la colección standings antes de ejecutar,
 * ver gcloud firestore export --collection-ids=standings).
 *
 * Correr DESPUÉS de desplegar el cambio de functions/src/espn.ts — si corre antes,
 * cualquier semana de pre-temporada puntuada en la ventana entre migración y
 * deploy se reescribiría con el formato viejo y quedaría "des-migrada" otra vez.
 *
 * Cómo correr (no hay ts-node en devDependencies; compilar manualmente):
 *   cd functions
 *   npx tsc scripts/migratePreseasonWeeklyBreakdown.ts --outDir scripts-lib \
 *     --module commonjs --target es2020 --esModuleInterop --skipLibCheck --resolveJsonModule
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/a/service-account.json \
 *     node scripts-lib/migratePreseasonWeeklyBreakdown.js --dry-run
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/a/service-account.json \
 *     node scripts-lib/migratePreseasonWeeklyBreakdown.js --execute
 */

import { initializeApp } from "firebase-admin/app";
import { getFirestore, WriteBatch } from "firebase-admin/firestore";

const DRY_RUN = !process.argv.includes("--execute");

// Años a revisar — la ambigüedad existe desde antes de este feature, así que se
// revisan ambos años cercanos a la fecha del cambio por seguridad.
const YEARS_TO_CHECK = [2025, 2026];
const MAX_PRESEASON_WEEK = 4; // La pre-temporada NFL es como máximo semanas 1-4.
const BATCH_WRITE_LIMIT = 400; // Firestore permite hasta 500 escrituras por batch.

interface EspnHistoricalEvent {
  id: string;
}

interface EspnHistoricalResponse {
  events: EspnHistoricalEvent[];
}

async function fetchPreseasonEventIds(year: number, maxWeek: number): Promise<Map<number, Set<string>>> {
  const eventIdsByWeek = new Map<number, Set<string>>();
  for (let week = 1; week <= maxWeek; week++) {
    const url =
      `https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard` +
      `?seasontype=1&week=${week}&dates=${year}`;
    const res = await fetch(url);
    if (!res.ok) {
      console.warn(`  ESPN ${year} semana ${week}: HTTP ${res.status}, se omite`);
      continue;
    }
    const data = (await res.json()) as EspnHistoricalResponse;
    eventIdsByWeek.set(week, new Set(data.events.map((e) => e.id)));
  }
  return eventIdsByWeek;
}

/** oldWeekId ("2026-week-01") -> newWeekId ("2026-pre-week-01") */
async function findAffectedWeekIds(): Promise<Map<string, string>> {
  const affected = new Map<string, string>();
  const preseasonByYear = new Map<number, Map<number, Set<string>>>();

  for (const year of YEARS_TO_CHECK) {
    console.log(`Consultando ESPN — pre-temporada ${year}, semanas 1-${MAX_PRESEASON_WEEK}...`);
    preseasonByYear.set(year, await fetchPreseasonEventIds(year, MAX_PRESEASON_WEEK));
  }

  const db = getFirestore();
  const groupsSnap = await db.collection("groups").get();

  for (const groupDoc of groupsSnap.docs) {
    const weeksSnap = await db.collection(`groups/${groupDoc.id}/weeks`).get();
    for (const weekDoc of weeksSnap.docs) {
      const oldWeekId = weekDoc.id;
      const match = /^(\d{4})-week-(\d{2})$/.exec(oldWeekId);
      if (!match) continue;

      const year = parseInt(match[1], 10);
      const weekNum = parseInt(match[2], 10);
      if (weekNum > MAX_PRESEASON_WEEK) continue;

      const preseasonIds = preseasonByYear.get(year)?.get(weekNum);
      if (!preseasonIds || preseasonIds.size === 0) continue;

      const games = (weekDoc.data().games ?? []) as { id: string }[];
      if (games.length === 0) continue;

      const allMatchPreseason = games.every((g) => preseasonIds.has(g.id));
      if (allMatchPreseason) {
        affected.set(oldWeekId, `${match[1]}-pre-week-${match[2]}`);
      }
    }
  }
  return affected;
}

async function migrate(affectedWeekIds: Map<string, string>) {
  if (affectedWeekIds.size === 0) {
    console.log("No se encontraron weekIds afectados — nada que migrar.");
    return;
  }
  console.log("WeekIds afectados (viejo -> nuevo):", [...affectedWeekIds.entries()]);

  const db = getFirestore();
  const groupsSnap = await db.collection("groups").get();

  let batch: WriteBatch = db.batch();
  let opsInBatch = 0;
  let totalUsersUpdated = 0;

  for (const groupDoc of groupsSnap.docs) {
    const membersSnap = await db.collection(`standings/${groupDoc.id}/members`).get();
    for (const memberDoc of membersSnap.docs) {
      const breakdown = (memberDoc.data().weeklyBreakdown ?? {}) as Record<string, number>;
      const newBreakdown: Record<string, number> = { ...breakdown };
      let changed = false;

      for (const [oldKey, newKey] of affectedWeekIds) {
        if (oldKey in newBreakdown) {
          newBreakdown[newKey] = newBreakdown[oldKey];
          delete newBreakdown[oldKey];
          changed = true;
        }
      }
      if (!changed) continue;

      totalUsersUpdated++;
      console.log(`  ${groupDoc.id}/${memberDoc.id}:`, breakdown, "->", newBreakdown);

      if (!DRY_RUN) {
        // totalPoints no se toca — renombrar una clave no cambia la suma de valores.
        batch.update(memberDoc.ref, { weeklyBreakdown: newBreakdown });
        opsInBatch++;
        if (opsInBatch >= BATCH_WRITE_LIMIT) {
          await batch.commit();
          batch = db.batch();
          opsInBatch = 0;
        }
      }
    }
  }

  if (!DRY_RUN && opsInBatch > 0) {
    await batch.commit();
  }
  console.log(`${DRY_RUN ? "[DRY RUN] Se actualizarían" : "Se actualizaron"} ${totalUsersUpdated} documentos de standings.`);
}

(async () => {
  initializeApp();
  console.log(`Modo: ${DRY_RUN ? "DRY RUN (solo lectura, sin escrituras)" : "EXECUTE (escribe en producción)"}`);
  const affected = await findAffectedWeekIds();
  await migrate(affected);
})().catch((err) => {
  console.error("Migración falló:", err);
  process.exit(1);
});
