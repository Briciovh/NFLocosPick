import { db } from "./setup";
import { GLOBAL_GROUP_ID, seedGlobalStanding } from "../../src/globalGroup";

function standingRef(uid: string) {
  return db.collection("standings").doc(GLOBAL_GROUP_ID).collection("members").doc(uid);
}

describe("seedGlobalStanding (Firestore emulator)", () => {
  it("creates a zero-point standing when the user has none", async () => {
    await seedGlobalStanding("u1");

    expect((await standingRef("u1").get()).data()).toEqual({
      totalPoints: 0,
      weeklyBreakdown: {},
    });
  });

  it("does not overwrite an existing standing (idempotent, points preserved)", async () => {
    await standingRef("u1").set({ totalPoints: 7, weeklyBreakdown: { "2025-week-01": 7 } });

    await seedGlobalStanding("u1");

    expect((await standingRef("u1").get()).data()).toEqual({
      totalPoints: 7,
      weeklyBreakdown: { "2025-week-01": 7 },
    });
  });
});
