import { db } from "./setup";
import { removeGroupMember, unblockGroupMember } from "../../src/groupMembers";
import { GLOBAL_GROUP_ID } from "../../src/globalGroup";

describe("groupMembers (Firestore emulator)", () => {
  describe("removeGroupMember", () => {
    it("refuses to remove members from the global group", async () => {
      await expect(
        removeGroupMember(GLOBAL_GROUP_ID, "admin", "target", false)
      ).rejects.toHaveProperty("code", "permission-denied");
    });

    it("throws not-found when the group does not exist", async () => {
      await expect(
        removeGroupMember("nonexistent_group", "admin", "target", false)
      ).rejects.toHaveProperty("code", "not-found");
    });

    it("throws permission-denied when the caller is not the creator", async () => {
      await db.collection("groups").doc("g_test").set({
        createdBy: "owner",
        memberIds: ["owner", "u2", "u3"],
      });

      await expect(
        removeGroupMember("g_test", "u2", "u3", false)
      ).rejects.toHaveProperty("code", "permission-denied");

      const groupSnap = await db.collection("groups").doc("g_test").get();
      expect(groupSnap.data()?.memberIds).toContain("u3");
    });

    it("throws invalid-argument when the admin tries to remove themselves", async () => {
      await db.collection("groups").doc("g_test2").set({
        createdBy: "owner",
        memberIds: ["owner", "u2"],
      });

      await expect(
        removeGroupMember("g_test2", "owner", "owner", false)
      ).rejects.toHaveProperty("code", "invalid-argument");
    });

    it("removes member, hides standing, and does not block when block=false", async () => {
      await db.collection("groups").doc("g_test3").set({
        createdBy: "owner",
        memberIds: ["owner", "target_uid"],
        blockedIds: [],
      });
      await db
        .doc("standings/g_test3/members/target_uid")
        .set({ totalPoints: 10, weeklyBreakdown: { w1: 10 } });

      await removeGroupMember("g_test3", "owner", "target_uid", false);

      const groupSnap = await db.collection("groups").doc("g_test3").get();
      expect(groupSnap.data()?.memberIds).not.toContain("target_uid");
      expect(groupSnap.data()?.blockedIds ?? []).not.toContain("target_uid");

      const standingSnap = await db.doc("standings/g_test3/members/target_uid").get();
      expect(standingSnap.exists).toBe(true);
      expect(standingSnap.data()?.hidden).toBe(true);
      expect(standingSnap.data()?.hiddenAt).toBeDefined();
      expect(standingSnap.data()?.totalPoints).toBe(10);
    });

    it("removes member, adds to blockedIds, and hides standing when block=true", async () => {
      await db.collection("groups").doc("g_test4").set({
        createdBy: "owner",
        memberIds: ["owner", "target_uid"],
      });
      await db
        .doc("standings/g_test4/members/target_uid")
        .set({ totalPoints: 5, weeklyBreakdown: { w1: 5 } });

      await removeGroupMember("g_test4", "owner", "target_uid", true);

      const groupSnap = await db.collection("groups").doc("g_test4").get();
      expect(groupSnap.data()?.memberIds).not.toContain("target_uid");
      expect(groupSnap.data()?.blockedIds).toContain("target_uid");

      const standingSnap = await db.doc("standings/g_test4/members/target_uid").get();
      expect(standingSnap.data()?.hidden).toBe(true);
    });

    it("succeeds when member has no standing doc", async () => {
      await db.collection("groups").doc("g_test5").set({
        createdBy: "owner",
        memberIds: ["owner", "target_no_standing"],
      });

      await removeGroupMember("g_test5", "owner", "target_no_standing", true);

      const groupSnap = await db.collection("groups").doc("g_test5").get();
      expect(groupSnap.data()?.memberIds).not.toContain("target_no_standing");
      expect(groupSnap.data()?.blockedIds).toContain("target_no_standing");
    });
  });

  describe("unblockGroupMember", () => {
    it("refuses on the global group", async () => {
      await expect(
        unblockGroupMember(GLOBAL_GROUP_ID, "admin", "target")
      ).rejects.toHaveProperty("code", "permission-denied");
    });

    it("throws not-found when the group does not exist", async () => {
      await expect(
        unblockGroupMember("nonexistent_group", "admin", "target")
      ).rejects.toHaveProperty("code", "not-found");
    });

    it("throws permission-denied when caller is not the creator", async () => {
      await db.collection("groups").doc("g_test6").set({
        createdBy: "owner",
        memberIds: ["owner", "u2"],
        blockedIds: ["blocked_user"],
      });

      await expect(
        unblockGroupMember("g_test6", "u2", "blocked_user")
      ).rejects.toHaveProperty("code", "permission-denied");
    });

    it("removes targetUid from blockedIds without altering memberIds or standings", async () => {
      await db.collection("groups").doc("g_test7").set({
        createdBy: "owner",
        memberIds: ["owner"],
        blockedIds: ["blocked_user"],
      });
      await db
        .doc("standings/g_test7/members/blocked_user")
        .set({ totalPoints: 10, hidden: true });

      await unblockGroupMember("g_test7", "owner", "blocked_user");

      const groupSnap = await db.collection("groups").doc("g_test7").get();
      expect(groupSnap.data()?.blockedIds).not.toContain("blocked_user");
      expect(groupSnap.data()?.memberIds).not.toContain("blocked_user");

      const standingSnap = await db.doc("standings/g_test7/members/blocked_user").get();
      expect(standingSnap.data()?.hidden).toBe(true);
    });
  });
});
