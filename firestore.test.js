const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 500);
}

function futureTimestamp() {
  return new Date(Date.now() + 3600 * 1000);
}

function validSolvedPayload(uid = ALICE_UID, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId: uid,
    questionText: "What is the derivative of sin(x)?",
    answerBadge: "B",
    shortAnswer: "cos(x)",
    explanation: "By standard trigonometric differentiation, d/dx[sin(x)] = cos(x).",
    questionType: "MCQ",
    subject: "MATH",
    confidence: 99,
    language: "English",
    bookmarked: false,
    mistakeDeck: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validProfilePayload(uid = ALICE_UID, overrides = {}) {
  const ts = nowTimestamp();
  return {
    userId: uid,
    displayName: "Alice Student",
    email: "alice@example.com",
    appVersion: "3.5.2",
    totalRequests: 4,
    firebaseRequests: 2,
    cloudflareRequests: 1,
    cacheHits: 1,
    failedRequests: 0,
    totalLatencyMs: 6800,
    avgLatencyMs: 1700,
    totalActiveSeconds: 185,
    activeSessionsCount: 3,
    lastFeatureUsed: "DYNAMIC_ISLAND",
    lastBackendUsed: "FIREBASE_AI",
    lastActiveAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validRequestLogPayload(uid = ALICE_UID, overrides = {}) {
  return {
    userId: uid,
    userEmail: "alice@example.com",
    feature: "DYNAMIC_ISLAND",
    backend: "FIREBASE_AI",
    modelUsed: "gemini-3.5-flash-lite",
    latencyMs: 1840,
    status: "SUCCESS",
    questionPreview: "Which cell organelle generates ATP?",
    answerBadge: "B",
    createdAt: nowTimestamp(),
    ...overrides,
  };
}

function validSessionPayload(uid = ALICE_UID, overrides = {}) {
  return {
    userId: uid,
    userEmail: "alice@example.com",
    source: "DYNAMIC_ISLAND",
    durationSeconds: 120,
    requestsCount: 3,
    deviceModel: "Pixel 8",
    appVersion: "3.5.2",
    endedAt: nowTimestamp(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("requestLogs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("activeSessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
});

test("2. SolvedQuestions: owner CRUD works and cross-user/spoof/ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("q_valid_1");

  await assertSucceeds(docRef.set(validSolvedPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").get());
  await assertSucceeds(docRef.update({ bookmarked: true, mistakeDeck: true, updatedAt: nowTimestamp() }));

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("q_valid_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("q_valid_1").delete());

  // Ghost field & spoof
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("spoof").set(validSolvedPayload(BOB_UID)));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("ghost").set(validSolvedPayload(ALICE_UID, { isAdmin: true })));

  await assertSucceeds(docRef.delete());
});

test("3. UserActivityProfile: owner create/get/update works, list/cross-user/future-time/ghost fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      totalRequests: 5,
      firebaseRequests: 3,
      totalActiveSeconds: 240,
      lastFeatureUsed: "GOOGLE_FORMS",
      lastBackendUsed: "CLOUDFLARE_EDGE",
      lastActiveAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Listing all users is denied to clients (viewable via Firebase Console / Admin SDK)
  await assertFails(aliceDb.collection("users").get());
  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ totalRequests: 99, updatedAt: nowTimestamp() }));
  // Future timestamp rejected
  await assertFails(profileRef.update({ updatedAt: futureTimestamp() }));
  // Ghost field rejected
  await assertFails(profileRef.update({ role: "admin", updatedAt: nowTimestamp() }));
  // Immutable createdAt cannot be mutated
  await assertFails(profileRef.update({ createdAt: nowTimestamp(), updatedAt: nowTimestamp() }));
});

test("4. UserRequestLog: owner create/get/list/delete works, update/cross-user/oversized/bad-enum fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const logsCol = aliceDb.collection("users").doc(ALICE_UID).collection("requestLogs");
  const logRef = logsCol.doc("req_001");

  await assertSucceeds(logRef.set(validRequestLogPayload(ALICE_UID)));
  await assertSucceeds(logRef.get());
  await assertSucceeds(logsCol.get());

  // Immutable log: update denied
  await assertFails(logRef.update({ latencyMs: 100 }));
  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("requestLogs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("requestLogs").doc("req_002").set(validRequestLogPayload(BOB_UID)));
  // Bad enum & oversized preview
  await assertFails(logsCol.doc("req_bad").set(validRequestLogPayload(ALICE_UID, { backend: "OPENAI_KEY" })));
  await assertFails(logsCol.doc("req_big").set(validRequestLogPayload(ALICE_UID, { questionPreview: "x".repeat(300) })));

  await assertSucceeds(logRef.delete());
});

test("5. UserActiveSession: owner create/get/list/delete works, invalid duration/cross-user/update fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const sessionsCol = aliceDb.collection("users").doc(ALICE_UID).collection("activeSessions");
  const sessionRef = sessionsCol.doc("sess_001");

  await assertSucceeds(sessionRef.set(validSessionPayload(ALICE_UID)));
  await assertSucceeds(sessionRef.get());
  await assertSucceeds(sessionsCol.get());

  // Update denied (append-only sessions)
  await assertFails(sessionRef.update({ durationSeconds: 999 }));
  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("activeSessions").get());
  // Zero duration rejected
  await assertFails(sessionsCol.doc("sess_zero").set(validSessionPayload(ALICE_UID, { durationSeconds: 0 })));
  // Ghost field rejected
  await assertFails(sessionsCol.doc("sess_ghost").set(validSessionPayload(ALICE_UID, { extra: true })));

  await assertSucceeds(sessionRef.delete());
});

test("6. SharedQuizCache allows authenticated get & hash-keyed create, rejects list/spoof/update/delete", async () => {
  const validHash = "a".repeat(64);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const payload = {
    questionHash: validHash,
    createdByUid: ALICE_UID,
    answerBadge: "B",
    shortAnswer: "Mitochondria",
    explanation: "Mitochondria generate ATP via oxidative phosphorylation.",
    subject: "SCIENCE",
    confidence: 95,
    createdAt: nowTimestamp(),
  };

  await assertFails(unauthDb.collection("sharedQuizCache").doc(validHash).get());
  await assertSucceeds(aliceDb.collection("sharedQuizCache").doc(validHash).set(payload));
  await assertSucceeds(bobDb.collection("sharedQuizCache").doc(validHash).get());
  await assertFails(bobDb.collection("sharedQuizCache").get());
  await assertFails(bobDb.collection("sharedQuizCache").doc(validHash).update({ answerBadge: "A" }));
  await assertFails(bobDb.collection("sharedQuizCache").doc(validHash).delete());
  await assertFails(
    bobDb.collection("sharedQuizCache").doc("b".repeat(64)).set({
      ...payload,
      questionHash: "b".repeat(64),
      createdByUid: ALICE_UID,
    })
  );
});
