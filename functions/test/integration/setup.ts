import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

/**
 * Shared setup for the emulator-backed integration suites. These files run ONLY
 * through `npm run test:integration`, which wraps jest in
 * `firebase emulators:exec --only firestore`. That command exports
 * FIRESTORE_EMULATOR_HOST, so the Admin SDK below talks to the emulator, never
 * production.
 *
 * Only Firestore is emulated:
 *  - Auth: `deleteUserAccount`'s single `getAuth().deleteUser` call is stubbed in
 *    that suite (jest.mock) — an Auth emulator here just adds a flaky dependency.
 *  - Storage: `deleteUserAccount` / `deleteGroupCompletely` photo deletes are
 *    already try/catch'd; with no `storageBucket` configured they fail fast and
 *    locally ("Bucket name not specified") instead of hitting real GCS.
 */

// One emulator project per jest worker so parallel test files get isolated
// Firestore data (the emulator namespaces storage by project id). Without this,
// `clearEmulatorData` in one file would wipe another file's in-flight data and
// the shared firebase-admin gRPC client gets into a bad state across files.
export const PROJECT_ID = `demo-nflocospicks-w${process.env.JEST_WORKER_ID || "1"}`;

if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error(
    "Integration tests must run under `firebase emulators:exec` " +
      "(FIRESTORE_EMULATOR_HOST is not set). Use `npm run test:integration`.",
  );
}

if (getApps().length === 0) {
  initializeApp({ projectId: PROJECT_ID });
}

export const db = getFirestore();

/** Wipes the Firestore emulator. Called in `beforeEach` so suites don't leak state. */
export async function clearEmulatorData(): Promise<void> {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const res = await fetch(
    `http://${host}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  if (!res.ok) {
    throw new Error(`Failed to clear Firestore emulator: ${res.status}`);
  }
}

beforeEach(async () => {
  await clearEmulatorData();
});
