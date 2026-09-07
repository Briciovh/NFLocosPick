import { readFileSync } from "fs";
import { resolve } from "path";
import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { ref, uploadBytes, type FirebaseStorage } from "firebase/storage";

export { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";

// Fixed project id — it MUST match `emulators:exec --project` so that the Storage
// emulator's cross-service `firestore.get()` in storage.rules resolves against the
// same project the seed data is written to. The `rules` jest project runs with
// maxWorkers:1 so the two suites don't race on clearFirestore/clearStorage.
export const PROJECT_ID = "demo-nflocospicks";
const REPO_ROOT = resolve(__dirname, "../../..");

const RULES = {
  firestore: { rules: readFileSync(resolve(REPO_ROOT, "firestore.rules"), "utf8") },
  storage: { rules: readFileSync(resolve(REPO_ROOT, "storage.rules"), "utf8") },
} as const;

/** A tiny valid PNG payload for Storage upload tests (and the readiness probe). */
export const TINY_PNG = new Uint8Array([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4,
  0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
  0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae,
  0x42, 0x60, 0x82,
]);

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Creates the RulesTestEnvironment, retrying until the Storage rules-runtime has
 * actually loaded the ruleset.
 *
 * `firebase emulators:exec` reports the Storage emulator "ready" once its port is
 * bound, but the separate rules-runtime process may still be downloading/booting.
 * jest then starts and `initializeTestEnvironment` pushes storage.rules to a
 * not-ready runtime, so uploads fail with "no Storage ruleset is currently
 * loaded". The probe below — an owner upload that must succeed once rules are
 * live — detects that window and re-initializes (which re-pushes the rules).
 *
 * Called from each suite's `beforeAll`, which passes a 60s timeout to cover the
 * worst-case retry budget (20 * ~1s).
 */
export async function initEnv(): Promise<RulesTestEnvironment> {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run rules tests via `npm run test:rules` (emulators not detected).");
  }

  const MAX_ATTEMPTS = 20;
  let lastErr: unknown;

  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    const env = await initializeTestEnvironment({ projectId: PROJECT_ID, ...RULES });
    try {
      // Probe: an owner upload with a valid image content type. Succeeds only
      // when the Storage ruleset is loaded; otherwise throws storage/unauthorized
      // (or a connection error if the emulator isn't answering yet).
      // NOTE: this assumes the profile_photos/{userId} write rule still allows an
      // authenticated owner uploading a small image/*. If that rule is tightened,
      // update this probe or it will exhaust the retries with a false negative.
      const storage = env.authenticatedContext("probe").storage() as unknown as FirebaseStorage;
      await uploadBytes(ref(storage, "profile_photos/probe"), TINY_PNG, {
        contentType: "image/png",
      });
      await env.clearStorage();
      return env;
    } catch (err) {
      lastErr = err;
      try {
        await env.cleanup();
      } catch {
        // A half-initialized env can throw from cleanup(); ignore and retry.
      }
      await sleep(1000);
    }
  }

  throw new Error(
    `Storage emulator ruleset never became ready after ${MAX_ATTEMPTS} attempts. ` +
      `Last error: ${lastErr instanceof Error ? lastErr.message : String(lastErr)}`,
  );
}
