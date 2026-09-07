import {
  buildWeekId,
  computeWinners,
  fetchCurrentWeekGames,
  toSeasonType,
  type Game,
} from "../src/espn";
import scoreboardFixture from "./fixtures/espn_scoreboard.json";

/**
 * Pure-logic tests for functions/src/espn.ts — no emulator, no Admin SDK.
 *
 * `buildWeekId` and `toSeasonType` MUST stay byte-for-byte identical to the
 * Android side (app/.../data/remote/espn/EspnMapper.kt, covered by EspnMapperTest):
 * picks are written client-side and scored here against the same weekId.
 */

function game(overrides: Partial<Game> = {}): Game {
  return {
    id: "g1",
    weekId: "2025-week-12",
    seasonType: "REGULAR",
    homeTeamAbbr: "KC",
    awayTeamAbbr: "LV",
    homeScore: 0,
    awayScore: 0,
    status: "FINAL",
    ...overrides,
  };
}

describe("toSeasonType", () => {
  it("maps ESPN season.type codes", () => {
    expect(toSeasonType(1)).toBe("PRESEASON");
    expect(toSeasonType(2)).toBe("REGULAR");
    expect(toSeasonType(3)).toBe("POSTSEASON");
  });

  it("falls back to REGULAR for any other code", () => {
    expect(toSeasonType(0)).toBe("REGULAR");
    expect(toSeasonType(4)).toBe("REGULAR");
    expect(toSeasonType(99)).toBe("REGULAR");
  });
});

describe("buildWeekId (parity with Android EspnMapper.buildWeekId)", () => {
  it("regular season: {year}-week-{NN}", () => {
    expect(buildWeekId(2025, 12, "REGULAR")).toBe("2025-week-12");
  });

  it("pads the week number to two digits", () => {
    expect(buildWeekId(2025, 1, "REGULAR")).toBe("2025-week-01");
    expect(buildWeekId(2025, 9, "REGULAR")).toBe("2025-week-09");
  });

  it("preseason gets the pre- segment", () => {
    expect(buildWeekId(2025, 1, "PRESEASON")).toBe("2025-pre-week-01");
    expect(buildWeekId(2025, 4, "PRESEASON")).toBe("2025-pre-week-04");
  });

  it("postseason keeps the unprefixed format (documented ambiguity, matches Android)", () => {
    expect(buildWeekId(2025, 1, "POSTSEASON")).toBe("2025-week-01");
  });
});

describe("computeWinners (parity with ScoreWeekPicksUseCase)", () => {
  it("returns the home team when the home score is higher", () => {
    const winners = computeWinners([game({ id: "h", homeScore: 28, awayScore: 14 })]);
    expect(winners.get("h")).toBe("KC");
  });

  it("returns the away team when the away score is higher", () => {
    const winners = computeWinners([game({ id: "a", homeScore: 10, awayScore: 24 })]);
    expect(winners.get("a")).toBe("LV");
  });

  it("returns null for a tie so nobody is credited", () => {
    const winners = computeWinners([game({ id: "t", homeScore: 17, awayScore: 17 })]);
    expect(winners.has("t")).toBe(true);
    expect(winners.get("t")).toBeNull();
  });

  it("ignores games that are not FINAL", () => {
    const winners = computeWinners([
      game({ id: "final", homeScore: 21, awayScore: 7, status: "FINAL" }),
      game({ id: "live", homeScore: 21, awayScore: 7, status: "IN_PROGRESS" }),
      game({ id: "sched", homeScore: null, awayScore: null, status: "SCHEDULED" }),
    ]);
    expect([...winners.keys()]).toEqual(["final"]);
  });

  it("treats a missing score as 0", () => {
    const winners = computeWinners([game({ id: "n", homeScore: 3, awayScore: null })]);
    expect(winners.get("n")).toBe("KC");
  });
});

describe("fetchCurrentWeekGames", () => {
  const realFetch = global.fetch;
  afterEach(() => {
    global.fetch = realFetch;
  });

  function mockFetch(impl: () => Partial<Response>) {
    global.fetch = jest.fn(async () => impl() as Response) as typeof fetch;
  }

  it("maps the ESPN scoreboard payload to domain games", async () => {
    mockFetch(() => ({ ok: true, json: async () => scoreboardFixture }));

    const games = await fetchCurrentWeekGames();

    expect(games).toHaveLength(2);
    const final = games.find((g) => g.id === "401671876")!;
    expect(final).toMatchObject({
      weekId: "2025-week-12",
      seasonType: "REGULAR",
      homeTeamAbbr: "KC",
      awayTeamAbbr: "LV",
      homeScore: 28,
      awayScore: 14,
      status: "FINAL",
    });
    const scheduled = games.find((g) => g.id === "401671877")!;
    expect(scheduled).toMatchObject({
      homeTeamAbbr: "SF",
      awayTeamAbbr: "DAL",
      homeScore: null,
      awayScore: null,
      status: "SCHEDULED",
    });
  });

  it("feeds computeWinners a consistent gameId so scoring lines up", async () => {
    mockFetch(() => ({ ok: true, json: async () => scoreboardFixture }));
    const winners = computeWinners(await fetchCurrentWeekGames());
    expect(winners.get("401671876")).toBe("KC");
    expect(winners.has("401671877")).toBe(false);
  });

  it("throws when ESPN responds with a non-ok status", async () => {
    mockFetch(() => ({ ok: false, status: 503 }));
    await expect(fetchCurrentWeekGames()).rejects.toThrow("ESPN scoreboard request failed: 503");
  });

  it("reads week/season from the ROOT of the response, not per-event (diverges from Android)", async () => {
    // Documented divergence: EspnMapper.kt (Android) derives weekNumber/seasonType
    // from each event (event.week.number / event.season.type); espn.ts reads the
    // top-level data.week / data.season. For the current-week scoreboard these
    // agree, so scoring is unaffected — this test pins the current behavior so the
    // divergence is visible, not silent. A real fix belongs in its own PR.
    const payload = {
      week: { number: 7 },
      season: { type: 1 }, // root says PRESEASON week 7
      events: [
        {
          id: "e1",
          date: "2025-08-15T17:00Z",
          season: { type: 2 }, // per-event says REGULAR week 3 — ignored by espn.ts
          week: { number: 3 },
          competitions: [
            {
              competitors: [
                { homeAway: "home", score: null, team: { abbreviation: "KC" } },
                { homeAway: "away", score: null, team: { abbreviation: "LV" } },
              ],
              status: { type: { name: "STATUS_SCHEDULED", completed: false } },
            },
          ],
        },
      ],
    };
    mockFetch(() => ({ ok: true, json: async () => payload }));

    const [g] = await fetchCurrentWeekGames();

    expect(g.seasonType).toBe("PRESEASON");
    expect(g.weekId).toBe("2025-pre-week-07");
  });

  it("silently drops malformed events instead of failing the whole fetch", async () => {
    const payload = {
      week: { number: 5 },
      season: { type: 2 },
      events: [
        {
          id: "ok",
          date: "2025-10-05T17:00Z",
          competitions: [
            {
              competitors: [
                { homeAway: "home", score: "20", team: { abbreviation: "KC" } },
                { homeAway: "away", score: "17", team: { abbreviation: "DEN" } },
              ],
              status: { type: { name: "STATUS_FINAL", completed: true } },
            },
          ],
        },
        { id: "broken", date: "2025-10-05T20:00Z", competitions: [] },
      ],
    };
    mockFetch(() => ({ ok: true, json: async () => payload }));

    const games = await fetchCurrentWeekGames();

    expect(games.map((g) => g.id)).toEqual(["ok"]);
    expect(games[0].weekId).toBe("2025-week-05");
  });
});
