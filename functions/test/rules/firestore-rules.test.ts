import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  type Firestore,
} from "firebase/firestore";
import { assertFails, assertSucceeds, initEnv } from "./harness";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initEnv();
}, 60_000); // initEnv() retries until the Storage rules-runtime is ready (up to ~20s)
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearFirestore();
});

const alice = () => env.authenticatedContext("alice").firestore();
const bob = () => env.authenticatedContext("bob").firestore();
const anon = () => env.unauthenticatedContext().firestore();

async function seed(fn: (db: Firestore) => Promise<unknown>) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await fn(ctx.firestore() as unknown as Firestore);
  });
}

/** groups/g1: created by "alice", members alice + bob. */
async function seedGroup() {
  await seed((db) =>
    setDoc(doc(db, "groups/g1"), {
      name: "Los Locos",
      inviteCode: "ABC123",
      createdBy: "alice",
      memberIds: ["alice", "bob"],
    }),
  );
}

describe("users/{userId}", () => {
  it("requires auth to read a profile", async () => {
    await assertFails(getDoc(doc(anon(), "users/alice")));
    await assertSucceeds(getDoc(doc(bob(), "users/alice")));
  });

  it("only the owner can create their own doc", async () => {
    await assertSucceeds(setDoc(doc(alice(), "users/alice"), { displayName: "Alice" }));
    await assertFails(setDoc(doc(bob(), "users/alice"), { displayName: "hijack" }));
  });

  it("owner can update normal fields but never isActive / disabledAt", async () => {
    // Seed without isActive so writing it below is a real change (setting a field
    // to its existing value doesn't show up in Firestore's affectedKeys() diff).
    await seed((db) => setDoc(doc(db, "users/alice"), { displayName: "Alice" }));

    await assertSucceeds(updateDoc(doc(alice(), "users/alice"), { displayName: "Alice A.", lastActive: 123 }));
    await assertFails(updateDoc(doc(alice(), "users/alice"), { isActive: false }));
    await assertFails(updateDoc(doc(alice(), "users/alice"), { disabledAt: 123 }));
  });

  it("a third party cannot update or delete another profile", async () => {
    await seed((db) => setDoc(doc(db, "users/alice"), { displayName: "Alice" }));
    await assertFails(updateDoc(doc(bob(), "users/alice"), { displayName: "x" }));
    await assertFails(deleteDoc(doc(bob(), "users/alice")));
    await assertSucceeds(deleteDoc(doc(alice(), "users/alice")));
  });
});

describe("usernames/{username}", () => {
  it("can only be claimed with your own uid", async () => {
    await assertSucceeds(setDoc(doc(alice(), "usernames/alice"), { userId: "alice" }));
    await assertFails(setDoc(doc(bob(), "usernames/steal"), { userId: "alice" }));
  });

  it("is never updatable, even by the owner", async () => {
    await seed((db) => setDoc(doc(db, "usernames/alice"), { userId: "alice" }));
    await assertFails(updateDoc(doc(alice(), "usernames/alice"), { userId: "alice", note: "x" }));
  });

  it("only the owner can delete their reservation", async () => {
    await seed((db) => setDoc(doc(db, "usernames/alice"), { userId: "alice" }));
    await assertFails(deleteDoc(doc(bob(), "usernames/alice")));
    await assertSucceeds(deleteDoc(doc(alice(), "usernames/alice")));
  });
});

describe("groups/{groupId}", () => {
  it("create requires createdBy == uid and uid in memberIds", async () => {
    await assertSucceeds(
      setDoc(doc(alice(), "groups/new"), { createdBy: "alice", memberIds: ["alice"], name: "n", inviteCode: "c" }),
    );
    await assertFails(
      setDoc(doc(alice(), "groups/bad1"), { createdBy: "bob", memberIds: ["bob"], name: "n", inviteCode: "c" }),
    );
    await assertFails(
      setDoc(doc(alice(), "groups/bad2"), { createdBy: "alice", memberIds: ["bob"], name: "n", inviteCode: "c" }),
    );
  });

  it("any authenticated user can read a group (invite-code lookup)", async () => {
    await seedGroup();
    await assertSucceeds(getDoc(doc(env.authenticatedContext("carol").firestore(), "groups/g1")));
    await assertFails(getDoc(doc(anon(), "groups/g1")));
  });

  it("createdBy is immutable via update", async () => {
    await seedGroup();
    await assertFails(updateDoc(doc(alice(), "groups/g1"), { createdBy: "bob" }));
  });

  it("only the creator may change name / inviteCode / photoUrl / iconId", async () => {
    await seedGroup();
    // Distinct values so each update is a real diff (a no-op write to the same
    // value wouldn't touch affectedKeys() and the rule wouldn't trip).
    await assertFails(updateDoc(doc(bob(), "groups/g1"), { name: "Hijacked" }));
    await assertFails(updateDoc(doc(bob(), "groups/g1"), { iconId: "icon_a" }));
    await assertSucceeds(updateDoc(doc(alice(), "groups/g1"), { name: "Nuevo" }));
  });

  it("a non-member may self-join by adding only their own uid", async () => {
    await seedGroup();
    const carol = env.authenticatedContext("carol").firestore();
    await assertSucceeds(updateDoc(doc(carol, "groups/g1"), { memberIds: ["alice", "bob", "carol"] }));
  });

  it("self-join cannot drop existing members or add more than one uid", async () => {
    await seedGroup();
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(updateDoc(doc(carol, "groups/g1"), { memberIds: ["carol"] }));
    await assertFails(updateDoc(doc(carol, "groups/g1"), { memberIds: ["alice", "bob", "carol", "dave"] }));
  });

  it("the creator may remove a member", async () => {
    await seedGroup();
    await assertSucceeds(updateDoc(doc(alice(), "groups/g1"), { memberIds: ["alice"] }));
  });

  it("a blocked user cannot self-join; non-blocked can; non-creator cannot modify blockedIds", async () => {
    await seed((db) =>
      setDoc(doc(db, "groups/g1"), {
        name: "Los Locos",
        inviteCode: "ABC123",
        createdBy: "alice",
        memberIds: ["alice", "bob"],
        blockedIds: ["carol"],
      }),
    );
    const carol = env.authenticatedContext("carol").firestore();
    const dave = env.authenticatedContext("dave").firestore();

    // Blocked carol cannot self-join
    await assertFails(updateDoc(doc(carol, "groups/g1"), { memberIds: ["alice", "bob", "carol"] }));

    // Non-blocked dave can self-join
    await assertSucceeds(updateDoc(doc(dave, "groups/g1"), { memberIds: ["alice", "bob", "dave"] }));

    // Non-creator (bob) cannot touch blockedIds
    await assertFails(updateDoc(doc(bob(), "groups/g1"), { blockedIds: [] }));

    // Creator (alice) can update blockedIds
    await assertSucceeds(updateDoc(doc(alice(), "groups/g1"), { blockedIds: ["carol", "dave"] }));
  });

  it("delete is denied for everyone, including the creator", async () => {
    await seedGroup();
    await assertFails(deleteDoc(doc(alice(), "groups/g1")));
  });
});

describe("groups/{g}/weeks/{w} and picks/results", () => {
  beforeEach(seedGroup);

  it("only members can read a week; writes are limited to the 'games' key", async () => {
    await assertSucceeds(getDoc(doc(bob(), "groups/g1/weeks/w1")));
    await assertFails(getDoc(doc(env.authenticatedContext("carol").firestore(), "groups/g1/weeks/w1")));

    await assertSucceeds(setDoc(doc(bob(), "groups/g1/weeks/w1"), { games: [{ id: "1" }] }));
    await assertFails(setDoc(doc(bob(), "groups/g1/weeks/w1"), { games: [], hacked: true }));
  });

  it("a user writes only their own pick doc and MUST be a member; non-members cannot write or read", async () => {
    const carol = env.authenticatedContext("carol").firestore();
    // Bob is a member: can write his own picks
    await assertSucceeds(setDoc(doc(bob(), "groups/g1/weeks/w1/picks/bob"), { g1: { pickedTeam: "KC" } }));
    // Alice cannot write Bob's picks
    await assertFails(setDoc(doc(alice(), "groups/g1/weeks/w1/picks/bob"), { g1: { pickedTeam: "x" } }));
    // Carol is not a member: cannot write her own picks in this group (finding AGY #1)
    await assertFails(setDoc(doc(carol, "groups/g1/weeks/w1/picks/carol"), { g1: { pickedTeam: "KC" } }));
    // Members read, non-members do not
    await assertSucceeds(getDoc(doc(alice(), "groups/g1/weeks/w1/picks/bob")));
    await assertFails(getDoc(doc(carol, "groups/g1/weeks/w1/picks/bob")));
  });

  it("results are readable by members but never client-writable", async () => {
    await seed((db) => setDoc(doc(db, "groups/g1/weeks/w1/results/bob"), { g1: { isCorrect: true } }));
    await assertSucceeds(getDoc(doc(bob(), "groups/g1/weeks/w1/results/bob")));
    await assertFails(setDoc(doc(bob(), "groups/g1/weeks/w1/results/bob"), { g1: { isCorrect: true } }));
  });
});

describe("groups/{g}/board/{messageId}", () => {
  beforeEach(seedGroup);

  it("members create with their own senderId; non-members cannot", async () => {
    await assertSucceeds(setDoc(doc(bob(), "groups/g1/board/m1"), { senderId: "bob", content: "hi" }));
    await assertFails(setDoc(doc(bob(), "groups/g1/board/m2"), { senderId: "alice", content: "spoof" }));
    await assertFails(
      setDoc(doc(env.authenticatedContext("carol").firestore(), "groups/g1/board/m3"), {
        senderId: "carol",
        content: "hi",
      }),
    );
  });

  it("only the admin (createdBy) may flip isAnnouncement", async () => {
    await seed((db) => setDoc(doc(db, "groups/g1/board/m1"), { senderId: "bob", content: "hi", isAnnouncement: false }));
    // author (non-admin) editing content is fine…
    await assertSucceeds(updateDoc(doc(bob(), "groups/g1/board/m1"), { content: "edit" }));
    // …but not toggling the announcement flag
    await assertFails(updateDoc(doc(bob(), "groups/g1/board/m1"), { isAnnouncement: true }));
    await assertSucceeds(updateDoc(doc(alice(), "groups/g1/board/m1"), { isAnnouncement: true }));
  });

  it("delete is allowed for the author or the admin, nobody else", async () => {
    await seed((db) => setDoc(doc(db, "groups/g1/board/m1"), { senderId: "bob", content: "hi" }));
    await assertFails(
      deleteDoc(doc(env.authenticatedContext("carol").firestore(), "groups/g1/board/m1")),
    );
    await assertSucceeds(deleteDoc(doc(alice(), "groups/g1/board/m1"))); // admin
  });
});

describe("standings/{g}/members/{uid}", () => {
  beforeEach(seedGroup);

  it("members read; non-members do not; client create and delete are denied", async () => {
    await seed((db) => setDoc(doc(db, "standings/g1/members/bob"), { totalPoints: 3, weeklyBreakdown: {} }));
    await assertSucceeds(getDoc(doc(bob(), "standings/g1/members/bob")));
    await assertFails(getDoc(doc(env.authenticatedContext("carol").firestore(), "standings/g1/members/bob")));
    await assertFails(setDoc(doc(bob(), "standings/g1/members/bob"), { totalPoints: 999, weeklyBreakdown: {} }));
    await assertFails(deleteDoc(doc(bob(), "standings/g1/members/bob")));
  });

  it("a member can only update hidden and hiddenAt on their own standing (unhide carve-out)", async () => {
    await seed((db) =>
      setDoc(doc(db, "standings/g1/members/bob"), {
        totalPoints: 10,
        weeklyBreakdown: { w1: 10 },
        hidden: true,
        hiddenAt: 12345,
      }),
    );

    // Bob (member and owner) can update hidden / hiddenAt
    await assertSucceeds(
      updateDoc(doc(bob(), "standings/g1/members/bob"), {
        hidden: false,
        hiddenAt: 67890,
      }),
    );

    // Bob cannot modify totalPoints or weeklyBreakdown
    await assertFails(
      updateDoc(doc(bob(), "standings/g1/members/bob"), {
        totalPoints: 999,
      }),
    );

    // Alice (admin/other user) cannot update Bob's standing
    await assertFails(
      updateDoc(doc(alice(), "standings/g1/members/bob"), {
        hidden: false,
      }),
    );

    // Carol (non-member) cannot update even if it's her uid
    await seed((db) =>
      setDoc(doc(db, "standings/g1/members/carol"), {
        totalPoints: 0,
        hidden: true,
      }),
    );
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(
      updateDoc(doc(carol, "standings/g1/members/carol"), {
        hidden: false,
      }),
    );
  });
});
