/** @type {import('jest').Config} */
const tsPreset = {
  preset: "ts-jest",
  testEnvironment: "node",
  clearMocks: true,
  transform: {
    "^.+\\.tsx?$": ["ts-jest", { tsconfig: "<rootDir>/tsconfig.test.json" }],
  },
};

module.exports = {
  projects: [
    {
      ...tsPreset,
      displayName: "unit",
      // Pure-logic suites — no emulator. `npm test` runs only these.
      testMatch: ["<rootDir>/test/*.test.ts"],
    },
    {
      ...tsPreset,
      displayName: "integration",
      // Firestore-emulator suites. Run via `npm run test:integration`
      // (wrapped in `firebase emulators:exec`). Each jest worker uses its own
      // emulator project id (see setup.ts) so test files can run in parallel
      // without wiping each other's data.
      testMatch: ["<rootDir>/test/integration/*.test.ts"],
      setupFilesAfterEnv: ["<rootDir>/test/integration/setup.ts"],
    },
    {
      ...tsPreset,
      displayName: "rules",
      // firestore.rules / storage.rules suites via @firebase/rules-unit-testing.
      // Run via `npm run test:rules` (wraps `firebase emulators:exec` with the
      // firestore + storage emulators). Serial: both suites share the one
      // emulator project (needed for storage.rules' cross-service firestore.get)
      // and clear it between tests.
      testMatch: ["<rootDir>/test/rules/*.test.ts"],
      maxWorkers: 1,
    },
  ],
};
