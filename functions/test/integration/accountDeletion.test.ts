import { db } from "./setup";

// getAuth().deleteUser is the only non-Firestore call in deleteUserAccount — stub
// it so this suite needs just the Firestore emulator, not a flaky Auth emulator.
// (jest allows factory refs to vars prefixed `mock`.)
const mockDeleteUser = jest.fn().mockResolvedValue(undefined);
jest.mock("firebase-admin/auth", () => ({
  getAuth: () => ({ deleteUser: mockDeleteUser }),
}));

// Import AFTER the mock is registered.
// eslint-disable-next-line @typescript-eslint/no-var-requires
import { deleteUserAccount } from "../../src/accountDeletion";

const DELETED = "Usuario eliminado";

function userRef(uid: string) {
  return db.collection("users").doc(uid);
}

describe("deleteUserAccount (Firestore emulator, Auth stubbed)", () => {
  beforeEach(() => mockDeleteUser.mockClear());

  it("anonymizes the user doc, frees the username reservation and deletes the Auth record", async () => {
    await userRef("u1").set({ username: "SaulB", displayName: "Saul", email: "s@t.com" });
    await db.collection("usernames").doc("saulb").set({ userId: "u1" });

    await deleteUserAccount("u1");

    const user = (await userRef("u1").get()).data()!;
    expect(user).toMatchObject({
      displayName: DELETED,
      email: null,
      phoneNumber: null,
      photoUrl: null,
      username: null,
    });
    expect(user.deletedAt).toBeDefined();

    expect((await db.collection("usernames").doc("saulb").get()).exists).toBe(false);
    expect(mockDeleteUser).toHaveBeenCalledWith("u1");
  });

  it("removes the user from memberIds and transfers createdBy when they were the admin", async () => {
    await userRef("u1").set({ displayName: "Saul" });
    await db.collection("groups").doc("g1").set({ createdBy: "u1", memberIds: ["u1", "u2"] });

    await deleteUserAccount("u1");

    const group = (await db.collection("groups").doc("g1").get()).data()!;
    expect(group.memberIds).toEqual(["u2"]);
    expect(group.createdBy).toBe("u2");
  });

  it("removes the user from memberIds without touching createdBy when they were not the admin", async () => {
    await userRef("u1").set({ displayName: "Saul" });
    await db.collection("groups").doc("g1").set({ createdBy: "owner", memberIds: ["owner", "u1"] });

    await deleteUserAccount("u1");

    const group = (await db.collection("groups").doc("g1").get()).data()!;
    expect(group.memberIds).toEqual(["owner"]);
    expect(group.createdBy).toBe("owner");
  });

  it("leaves a group orphaned when its last member (and creator) deletes their account", async () => {
    // Documented gap (see docs/plans/test-coverage-hardening.md): remaining.length === 0
    // means createdBy is NOT reassigned, so the group keeps a dangling createdBy and
    // can no longer be deleted (deleteGroupCompletely / rules require createdBy == caller).
    await userRef("u1").set({ displayName: "Saul" });
    await db.collection("groups").doc("solo").set({ createdBy: "u1", memberIds: ["u1"] });

    await deleteUserAccount("u1");

    const group = (await db.collection("groups").doc("solo").get()).data()!;
    expect(group.memberIds).toEqual([]);
    expect(group.createdBy).toBe("u1"); // dangling — pins current behavior
  });

  it("anonymizes only the deleted user's denormalized board messages", async () => {
    await userRef("u1").set({ displayName: "Saul" });
    await db.collection("groups").doc("g1").set({ createdBy: "owner", memberIds: ["owner", "u1"] });
    await db.doc("groups/g1/board/m1").set({
      senderId: "u1",
      senderName: "Saul",
      senderPhotoUrl: "https://cdn/x.jpg",
      content: "hola",
    });
    await db.doc("groups/g1/board/m2").set({
      senderId: "owner",
      senderName: "Owner",
      senderPhotoUrl: null,
      content: "hey",
    });

    await deleteUserAccount("u1");

    const m1 = (await db.doc("groups/g1/board/m1").get()).data()!;
    expect(m1).toMatchObject({ senderName: DELETED, senderPhotoUrl: null, content: "hola" });
    const m2 = (await db.doc("groups/g1/board/m2").get()).data()!;
    expect(m2).toMatchObject({ senderName: "Owner", content: "hey" });
  });

  it("succeeds for a user with no username reservation", async () => {
    await userRef("u1").set({ displayName: "Saul" });

    await expect(deleteUserAccount("u1")).resolves.toBeUndefined();
    expect((await userRef("u1").get()).data()!.displayName).toBe(DELETED);
    expect(mockDeleteUser).toHaveBeenCalledWith("u1");
  });
});
