import { Timestamp } from "firebase-admin/firestore";
import { db } from "./setup";
import { deactivateInactiveUsers, reactivateUser } from "../../src/inactivity";

const YEAR_MS = 365 * 24 * 60 * 60 * 1000;
const twoYearsAgo = () => Timestamp.fromMillis(Date.now() - 2 * YEAR_MS);

function userRef(uid: string) {
  return db.collection("users").doc(uid);
}
function standingRef(groupId: string, uid: string) {
  return db.collection("standings").doc(groupId).collection("members").doc(uid);
}

describe("deactivateInactiveUsers (Firestore emulator)", () => {
  // NOTE: deactivateInactiveUsers reads every stale user with a single unbounded
  // .get() — fine at this app's scale (a few dozen users), not paginated. See
  // docs/plans/test-coverage-hardening.md.

  it("disables a long-inactive user and hides (does not delete) their standings", async () => {
    await userRef("old").set({ lastActive: twoYearsAgo(), isActive: true });
    await db.collection("groups").doc("g1").set({ memberIds: ["old"] });
    await standingRef("g1", "old").set({ totalPoints: 5, weeklyBreakdown: { "2025-week-01": 5 } });

    const count = await deactivateInactiveUsers();

    expect(count).toBe(1);
    const user = (await userRef("old").get()).data()!;
    expect(user.isActive).toBe(false);
    expect(user.disabledAt).toBeDefined();

    const standing = (await standingRef("g1", "old").get()).data()!;
    expect(standing.hidden).toBe(true);
    expect(standing.hiddenAt).toBeDefined();
    expect(standing.totalPoints).toBe(5); // history preserved
  });

  it("leaves a recently-active user untouched", async () => {
    await userRef("fresh").set({ lastActive: Timestamp.now(), isActive: true });

    const count = await deactivateInactiveUsers();

    expect(count).toBe(0);
    expect((await userRef("fresh").get()).data()!.isActive).toBe(true);
  });

  it("skips a user that is already disabled", async () => {
    await userRef("gone").set({ lastActive: twoYearsAgo(), isActive: false });

    const count = await deactivateInactiveUsers();

    expect(count).toBe(0);
  });

  it("does not create a standing in a group where the user never had one", async () => {
    await userRef("old").set({ lastActive: twoYearsAgo(), isActive: true });
    await db.collection("groups").doc("withStanding").set({ memberIds: ["old"] });
    await db.collection("groups").doc("noStanding").set({ memberIds: ["old"] });
    await standingRef("withStanding", "old").set({ totalPoints: 1, weeklyBreakdown: {} });

    await deactivateInactiveUsers();

    expect((await standingRef("withStanding", "old").get()).data()!.hidden).toBe(true);
    expect((await standingRef("noStanding", "old").get()).exists).toBe(false);
  });
});

describe("reactivateUser (Firestore emulator)", () => {
  it("is a no-op when the account is already active", async () => {
    await userRef("u1").set({ isActive: true });

    await reactivateUser("u1");

    const user = (await userRef("u1").get()).data()!;
    expect(user.isActive).toBe(true);
    expect(user.disabledAt).toBeUndefined();
  });

  it("is a no-op when the user doc is missing", async () => {
    await expect(reactivateUser("nobody")).resolves.toBeUndefined();
  });

  it("re-enables the account, clears disabledAt and un-hides standings with history intact", async () => {
    await userRef("u1").set({ isActive: false, disabledAt: Timestamp.now() });
    await db.collection("groups").doc("g1").set({ memberIds: ["u1"] });
    await standingRef("g1", "u1").set({
      totalPoints: 5,
      weeklyBreakdown: { "2025-week-01": 5 },
      hidden: true,
      hiddenAt: Timestamp.now(),
    });

    await reactivateUser("u1");

    const user = (await userRef("u1").get()).data()!;
    expect(user.isActive).toBe(true);
    expect(user.disabledAt).toBeUndefined();

    const standing = (await standingRef("g1", "u1").get()).data()!;
    expect(standing.hidden).toBeUndefined();
    expect(standing.hiddenAt).toBeUndefined();
    expect(standing.totalPoints).toBe(5);
  });
});
