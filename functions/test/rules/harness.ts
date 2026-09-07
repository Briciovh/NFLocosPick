import { readFileSync } from "fs";
import { resolve } from "path";
import {
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";

export { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";

// Fixed project id — it MUST match `emulators:exec --project` so that the Storage
// emulator's cross-service `firestore.get()` in storage.rules resolves against the
// same project the seed data is written to. (The Storage emulator has a single
// project context, unlike Firestore which is namespaced per project.) The `rules`
// jest project runs with maxWorkers:1 so the two suites don't race on
// clearFirestore/clearStorage.
export const PROJECT_ID = "demo-nflocospicks";
const REPO_ROOT = resolve(__dirname, "../../..");

export async function initEnv(): Promise<RulesTestEnvironment> {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run rules tests via `npm run test:rules` (emulators not detected).");
  }
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(resolve(REPO_ROOT, "firestore.rules"), "utf8"),
    },
    storage: {
      rules: readFileSync(resolve(REPO_ROOT, "storage.rules"), "utf8"),
    },
  });
}

/** A tiny valid PNG payload for Storage upload tests. */
export const TINY_PNG = new Uint8Array([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4,
  0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
  0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae,
  0x42, 0x60, 0x82,
]);
