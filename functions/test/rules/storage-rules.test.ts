import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { ref, uploadBytes, getBytes, type FirebaseStorage } from "firebase/storage";
import { assertFails, assertSucceeds, initEnv, TINY_PNG } from "./harness";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initEnv();
}, 60_000); // initEnv() retries until the Storage rules-runtime is ready (up to ~20s)
afterAll(async () => {
  await env.cleanup();
});
beforeEach(async () => {
  await env.clearStorage();
});

const IMG = { contentType: "image/png" };
const aliceStore = () => env.authenticatedContext("alice").storage() as unknown as FirebaseStorage;
const bobStore = () => env.authenticatedContext("bob").storage() as unknown as FirebaseStorage;
const anonStore = () => env.unauthenticatedContext().storage() as unknown as FirebaseStorage;

describe("profile_photos/{userId}", () => {
  it("the owner may upload a real image under 5 MiB", async () => {
    await assertSucceeds(uploadBytes(ref(aliceStore(), "profile_photos/alice"), TINY_PNG, IMG));
  });

  it("a non-owner cannot upload to someone else's path", async () => {
    await assertFails(uploadBytes(ref(bobStore(), "profile_photos/alice"), TINY_PNG, IMG));
  });

  it("rejects an oversized file", async () => {
    await assertFails(
      uploadBytes(ref(aliceStore(), "profile_photos/alice"), new Uint8Array(5 * 1024 * 1024 + 1), IMG),
    );
  });

  it("rejects a non-image content type", async () => {
    await assertFails(
      uploadBytes(ref(aliceStore(), "profile_photos/alice"), TINY_PNG, { contentType: "text/plain" }),
    );
  });

  it("any authenticated user can read a profile photo; anonymous cannot", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await uploadBytes(
        ref(ctx.storage() as unknown as FirebaseStorage, "profile_photos/alice"),
        TINY_PNG,
        IMG,
      );
    });
    await assertSucceeds(getBytes(ref(bobStore(), "profile_photos/alice")));
    await assertFails(getBytes(ref(anonStore(), "profile_photos/alice")));
  });
});

describe("group_photos/{groupId}", () => {
  it("requires authentication", async () => {
    await assertFails(uploadBytes(ref(anonStore(), "group_photos/g1"), TINY_PNG, IMG));
  });

  // The rest of the rule gates on
  //   firestore.get(/databases/(default)/documents/groups/$(groupId)).data.createdBy == request.auth.uid
  // The Storage rules runtime is a separate process and its cross-service
  // firestore.get() races with the seed write: across repeated local runs it
  // intermittently sees no doc ("Null value error" at storage.rules L24), even
  // with a fixed project id, maxWorkers:1 and a settle delay. A ~1-in-3 flaky
  // test is worse than none in CI, so this stays a todo. The equivalent
  // creator-only permission — only the creator may set photoUrl / iconId on the
  // group *document* — IS covered deterministically in firestore-rules.test.ts
  // › "only the creator may change name / inviteCode / photoUrl / iconId".
  it.todo("creator-only upload via firestore.get — flaky under rules-unit-testing (see comment)");
});
