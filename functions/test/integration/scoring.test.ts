import { db } from "./setup";
import { scoreGroupForWeek } from "../../src/scoring";

const GROUP = "g1";
const WEEK = "2025-week-12";

function picksRef(uid: string) {
  return db.collection("groups").doc(GROUP).collection("weeks").doc(WEEK).collection("picks").doc(uid);
}
function resultsRef(uid: string) {
  return db.collection("groups").doc(GROUP).collection("weeks").doc(WEEK).collection("results").doc(uid);
}
function standingRef(uid: string) {
  return db.collection("standings").doc(GROUP).collection("members").doc(uid);
}

describe("scoreGroupForWeek (Firestore emulator)", () => {
  it("writes settled results into results/{uid} and never mutates picks/{uid}", async () => {
    await picksRef("u1").set({ g1: { pickedTeam: "KC" }, g2: { pickedTeam: "SF" } });
    const winners = new Map<string, string | null>([
      ["g1", "KC"],
      ["g2", "DAL"],
    ]);

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners);

    expect(scored).toBe(2);
    const results = (await resultsRef("u1").get()).data()!;
    expect(results.g1).toMatchObject({ isCorrect: true, winnerTeamAbbr: "KC" });
    expect(results.g2).toMatchObject({ isCorrect: false, winnerTeamAbbr: "DAL" });
    expect(typeof results.g1.scoredAt).toBe("number");

    // picks doc still holds only the user's picks — no isCorrect leaked in.
    expect((await picksRef("u1").get()).data()).toEqual({
      g1: { pickedTeam: "KC" },
      g2: { pickedTeam: "SF" },
    });
  });

  it("recomputes weeklyBreakdown[weekId] and totalPoints as the sum of all weeks", async () => {
    await standingRef("u1").set({ totalPoints: 3, weeklyBreakdown: { "2025-week-11": 3 } });
    await picksRef("u1").set({ g1: { pickedTeam: "KC" }, g2: { pickedTeam: "GB" } });
    const winners = new Map<string, string | null>([
      ["g1", "KC"],
      ["g2", "GB"],
    ]);

    await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners);

    const standing = (await standingRef("u1").get()).data()!;
    expect(standing.weeklyBreakdown).toEqual({ "2025-week-11": 3, [WEEK]: 2 });
    expect(standing.totalPoints).toBe(5);
  });

  it("is idempotent — a second run scores nothing and leaves results/standings untouched", async () => {
    await picksRef("u1").set({ g1: { pickedTeam: "KC" } });
    const winners = new Map<string, string | null>([["g1", "KC"]]);

    expect(await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners)).toBe(1);
    const resultsAfterFirst = (await resultsRef("u1").get()).data();
    const standingAfterFirst = (await standingRef("u1").get()).data();

    expect(await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners)).toBe(0);
    expect((await resultsRef("u1").get()).data()).toEqual(resultsAfterFirst);
    expect((await standingRef("u1").get()).data()).toEqual(standingAfterFirst);
  });

  it("credits no point for a tie (winner === null)", async () => {
    await picksRef("u1").set({ g1: { pickedTeam: "KC" } });
    const winners = new Map<string, string | null>([["g1", null]]);

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners);

    expect(scored).toBe(1);
    const results = (await resultsRef("u1").get()).data()!;
    expect(results.g1).toMatchObject({ isCorrect: false, winnerTeamAbbr: "" });
    expect((await standingRef("u1").get()).data()!.weeklyBreakdown[WEEK]).toBe(0);
  });

  it("skips picks that already have a result and keeps their points in the week total", async () => {
    await resultsRef("u1").set({ g1: { isCorrect: true, scoredAt: 1, winnerTeamAbbr: "KC" } });
    await picksRef("u1").set({ g1: { pickedTeam: "KC" }, g2: { pickedTeam: "SF" } });
    const winners = new Map<string, string | null>([
      ["g1", "KC"],
      ["g2", "SF"],
    ]);

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners);

    expect(scored).toBe(1); // only g2 is newly scored
    // week total = already-correct (g1) + newly-correct (g2)
    expect((await standingRef("u1").get()).data()!.weeklyBreakdown[WEEK]).toBe(2);
    // g1's original result is preserved (merge, not overwrite)
    expect((await resultsRef("u1").get()).data()!.g1.scoredAt).toBe(1);
  });

  it("does nothing for a member with no picks doc", async () => {
    const winners = new Map<string, string | null>([["g1", "KC"]]);

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["missing"], winners);

    expect(scored).toBe(0);
    expect((await resultsRef("missing").get()).exists).toBe(false);
    expect((await standingRef("missing").get()).exists).toBe(false);
  });

  it("leaves a pick unsettled when the winner for that game is not yet known", async () => {
    await picksRef("u1").set({ g1: { pickedTeam: "KC" }, g3: { pickedTeam: "NE" } });
    const winners = new Map<string, string | null>([["g1", "KC"]]); // g3 not final yet

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["u1"], winners);

    expect(scored).toBe(1);
    const results = (await resultsRef("u1").get()).data()!;
    expect(results.g1).toBeDefined();
    expect(results.g3).toBeUndefined();
  });

  it("scores multiple members in one commit (NOTE: 2 writes/member — >250 members would exceed Firestore's 500-write batch cap; not chunked, see docs/plans/test-coverage-hardening.md)", async () => {
    await picksRef("a").set({ g1: { pickedTeam: "KC" } });
    await picksRef("b").set({ g1: { pickedTeam: "DEN" } });
    await picksRef("c").set({ g1: { pickedTeam: "KC" } });
    const winners = new Map<string, string | null>([["g1", "KC"]]);

    const scored = await scoreGroupForWeek(GROUP, WEEK, ["a", "b", "c"], winners);

    expect(scored).toBe(3);
    expect((await standingRef("a").get()).data()!.totalPoints).toBe(1);
    expect((await standingRef("b").get()).data()!.totalPoints).toBe(0);
    expect((await standingRef("c").get()).data()!.totalPoints).toBe(1);
  });
});
