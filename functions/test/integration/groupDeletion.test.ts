import { db } from "./setup";
import { deleteGroupCompletely } from "../../src/groupDeletion";
import { GLOBAL_GROUP_ID } from "../../src/globalGroup";

describe("deleteGroupCompletely (Firestore emulator)", () => {
  it("refuses to delete the global group", async () => {
    await expect(deleteGroupCompletely(GLOBAL_GROUP_ID, "u1")).rejects.toHaveProperty(
      "code",
      "permission-denied",
    );
  });

  it("throws not-found for a group that does not exist", async () => {
    await expect(deleteGroupCompletely("ghost", "u1")).rejects.toHaveProperty("code", "not-found");
  });

  it("throws permission-denied when the caller is not the creator", async () => {
    await db.collection("groups").doc("g2").set({ createdBy: "owner", memberIds: ["owner", "u2"] });

    await expect(deleteGroupCompletely("g2", "u2")).rejects.toHaveProperty(
      "code",
      "permission-denied",
    );

    // Nothing was deleted.
    expect((await db.collection("groups").doc("g2").get()).exists).toBe(true);
  });

  it("wipes the group doc, its subcollections and its standings tree for the creator", async () => {
    await db.collection("groups").doc("g2").set({ createdBy: "u1", memberIds: ["u1"] });
    await db.doc("groups/g2/weeks/w1/picks/u1").set({ g1: { pickedTeam: "KC" } });
    await db.doc("groups/g2/weeks/w1/results/u1").set({ g1: { isCorrect: true } });
    await db.doc("groups/g2/board/m1").set({ senderId: "u1", content: "hi" });
    await db.doc("standings/g2/members/u1").set({ totalPoints: 4, weeklyBreakdown: { w1: 4 } });

    await deleteGroupCompletely("g2", "u1");

    expect((await db.collection("groups").doc("g2").get()).exists).toBe(false);
    expect((await db.doc("groups/g2/weeks/w1/picks/u1").get()).exists).toBe(false);
    expect((await db.doc("groups/g2/weeks/w1/results/u1").get()).exists).toBe(false);
    expect((await db.doc("groups/g2/board/m1").get()).exists).toBe(false);
    expect((await db.doc("standings/g2/members/u1").get()).exists).toBe(false);
  });
});
