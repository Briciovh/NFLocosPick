// Pre-fetches the Firestore + Storage rules-runtime emulator JARs into the
// firebase-tools emulator cache BEFORE `firebase emulators:exec` runs.
//
// Why: on a cold cache (every CI run so far — the cache never got saved because
// the job kept failing), `emulators:exec` starts the Storage emulator, begins
// downloading cloud-storage-rules-runtime-*.jar, and runs jest ~1.5s later
// WITHOUT waiting for that runtime to be ready. @firebase/rules-unit-testing
// then pushes storage.rules to a not-ready runtime and the suite fails with
// "no Storage ruleset is currently loaded". Downloading the JARs up front takes
// them off the critical path.
//
// Idempotent: skips a binary that's already cached, so local `npm run test:rules`
// isn't slowed by a ~18s re-download every time (`firebase setup:emulators:*`
// itself always re-downloads — it skips the cache check — hence this guard).
//
// Known limitation: the presence check is by service, not by exact version. Right
// after a firebase-tools bump the cache may hold an older JAR; this guard would
// skip and `emulators:exec` would re-download the new one (bringing back the
// race). Mitigated by the CI cache key being hashed on functions/package-lock.json
// (a bump busts the cache) and, once added, the retry loop in harness.ts.

import { execSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { delimiter, join } from "node:path";

// firebase-tools resolves the cache dir as FIREBASE_EMULATORS_PATH || ~/.cache/firebase/emulators
const cacheDir =
  process.env.FIREBASE_EMULATORS_PATH || join(homedir(), ".cache", "firebase", "emulators");
const cached = existsSync(cacheDir) ? readdirSync(cacheDir) : [];
const missing = (fragment) => !cached.some((f) => f.includes(fragment));

const steps = [];
if (missing("firestore-emulator")) steps.push("setup:emulators:firestore");
if (missing("storage-rules-runtime")) steps.push("setup:emulators:storage");

if (steps.length === 0) {
  console.log(`Firebase emulator binaries already cached in ${cacheDir} — skipping pre-fetch.`);
} else {
  // Force the LOCAL firebase-tools (devDep) regardless of how this script was
  // invoked, without relying on `npx` (its --no-install flag is deprecated).
  const localBin = join(import.meta.dirname, "..", "node_modules", ".bin");
  const env = { ...process.env, PATH: `${localBin}${delimiter}${process.env.PATH ?? ""}` };
  for (const step of steps) {
    console.log(`> firebase ${step}`);
    execSync(`firebase ${step}`, { stdio: "inherit", env });
  }
}
