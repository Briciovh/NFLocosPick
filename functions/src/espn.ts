const SCOREBOARD_URL =
  "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard";

export type GameStatus = "SCHEDULED" | "IN_PROGRESS" | "FINAL";

export interface Game {
  id: string;
  weekId: string; // e.g. "2025-week-12"
  homeTeamAbbr: string;
  awayTeamAbbr: string;
  homeScore: number | null;
  awayScore: number | null;
  status: GameStatus;
}

interface EspnScoreboardResponse {
  week: { number: number };
  events: EspnEvent[];
}

interface EspnEvent {
  id: string;
  date: string;
  competitions: EspnCompetition[];
}

interface EspnCompetition {
  competitors: EspnCompetitor[];
  status: { type: { name: string; completed: boolean } };
}

interface EspnCompetitor {
  homeAway: "home" | "away";
  score?: string;
  team: { abbreviation: string };
}

/**
 * Replica EspnMapper.kt: obtiene el scoreboard actual de ESPN y lo mapea a
 * la misma forma de dominio que usa el cliente Android, para que el weekId
 * y los ganadores calculados server-side coincidan exactamente.
 */
export async function fetchCurrentWeekGames(): Promise<Game[]> {
  const res = await fetch(SCOREBOARD_URL);
  if (!res.ok) {
    throw new Error(`ESPN scoreboard request failed: ${res.status}`);
  }
  const data = (await res.json()) as EspnScoreboardResponse;
  const weekNumber = data.week.number;

  const games: Game[] = [];
  for (const event of data.events) {
    try {
      games.push(eventToGame(event, weekNumber));
    } catch {
      // Evento malformado — se descarta silenciosamente, igual que en Android.
      continue;
    }
  }
  return games;
}

function eventToGame(event: EspnEvent, weekNumber: number): Game {
  const competition = event.competitions[0];
  const home = competition.competitors.find((c) => c.homeAway === "home");
  const away = competition.competitors.find((c) => c.homeAway === "away");
  if (!home || !away) throw new Error("missing home/away competitor");

  const kickoffMillis = new Date(event.date).getTime();
  const year = new Date(kickoffMillis).getUTCFullYear();
  const weekId = `${year}-week-${String(weekNumber).padStart(2, "0")}`;

  const statusType = competition.status.type;
  const status: GameStatus = statusType.completed
    ? "FINAL"
    : statusType.name === "STATUS_IN_PROGRESS"
      ? "IN_PROGRESS"
      : "SCHEDULED";

  return {
    id: event.id,
    weekId,
    homeTeamAbbr: home.team.abbreviation,
    awayTeamAbbr: away.team.abbreviation,
    homeScore: home.score != null ? parseInt(home.score, 10) : null,
    awayScore: away.score != null ? parseInt(away.score, 10) : null,
    status,
  };
}

/**
 * Determina el ganador de cada juego FINAL. null = empate (nadie acierta).
 * Replica la lógica pura de ScoreWeekPicksUseCase.kt.
 */
export function computeWinners(games: Game[]): Map<string, string | null> {
  const winners = new Map<string, string | null>();
  for (const game of games.filter((g) => g.status === "FINAL")) {
    const home = game.homeScore ?? 0;
    const away = game.awayScore ?? 0;
    const winner =
      home > away ? game.homeTeamAbbr : away > home ? game.awayTeamAbbr : null;
    winners.set(game.id, winner);
  }
  return winners;
}
