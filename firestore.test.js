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

test("7. UserDailyUsage: owner monotonic increment works; decrement, delete, list, cross-user, and bad dateKey fail", async () => {
  const dateKey = "2026-11-21";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const usageRef = aliceDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc(dateKey);

  const initialPayload = {
    userId: ALICE_UID,
    dateKey,
    overlayMcqCount: 1,
    askTextCount: 0,
    askPhotoCount: 0,
    askPdfCount: 0,
    googleFormsCount: 0,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc(dateKey).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc(dateKey).set(initialPayload));

  // Owner create & get succeed
  await assertSucceeds(usageRef.set(initialPayload));
  await assertSucceeds(usageRef.get());

  // List denied (only direct doc get allowed)
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("dailyUsage").get());

  // Monotonic increment update succeeds
  await assertSucceeds(
    usageRef.update({
      overlayMcqCount: 2,
      askPhotoCount: 1,
      updatedAt: nowTimestamp(),
    })
  );

  // Decrement / quota reset attempt MUST fail
  await assertFails(
    usageRef.update({
      overlayMcqCount: 0,
      updatedAt: nowTimestamp(),
    })
  );

  // Delete attempt to wipe daily usage MUST fail
  await assertFails(usageRef.delete());

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc(dateKey).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc(dateKey).update({
      overlayMcqCount: 99,
      updatedAt: nowTimestamp(),
    })
  );

  // Bad dateKey format & ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyUsage").doc("today").set({
      ...initialPayload,
      dateKey: "today",
    })
  );
  await assertFails(
    usageRef.update({
      isUnlimited: true,
      updatedAt: nowTimestamp(),
    })
  );
});

test("8. DeviceGuard: enforces monotonic strikes, irreversible ban, same-day counter monotonicity, and denies list/delete/unauth", async () => {
  const deviceHash = "d".repeat(64);
  const day1 = "2026-11-21";
  const day2 = "2026-11-22";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const guardRefAlice = aliceDb.collection("deviceGuards").doc(deviceHash);
  const guardRefBob = bobDb.collection("deviceGuards").doc(deviceHash);

  const initialGuard = {
    deviceHash,
    dateKey: day1,
    lastUid: ALICE_UID,
    seenUidCountToday: 1,
    overlayMcqCount: 5,
    askTextCount: 2,
    askPhotoCount: 1,
    askPdfCount: 0,
    googleFormsCount: 1,
    strikeCount: 0,
    banned: false,
    lastStrikeReason: "",
    lastDeletedAccountAtMs: 0,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("deviceGuards").doc(deviceHash).get());
  await assertFails(unauthDb.collection("deviceGuards").doc(deviceHash).set(initialGuard));

  // Create & get succeed for signed-in user
  await assertSucceeds(guardRefAlice.set(initialGuard));
  await assertSucceeds(guardRefAlice.get());

  // Listing all devices is strictly denied
  await assertFails(aliceDb.collection("deviceGuards").get());

  // Second account (Bob) on same device triggers Strike 1 warning + increments seenUidCountToday
  await assertSucceeds(
    guardRefBob.update({
      lastUid: BOB_UID,
      seenUidCountToday: 2,
      strikeCount: 1,
      lastStrikeReason: "Account switch after daily usage on same device",
      updatedAt: nowTimestamp(),
    })
  );

  // Decrementing counters or strikes on same day MUST fail
  await assertFails(
    guardRefBob.update({
      overlayMcqCount: 0,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    guardRefBob.update({
      strikeCount: 0,
      updatedAt: nowTimestamp(),
    })
  );

  // Strike 2 sets banned = true
  await assertSucceeds(
    guardRefAlice.update({
      lastUid: ALICE_UID,
      strikeCount: 2,
      banned: true,
      lastStrikeReason: "Repeated account switching to bypass daily limits",
      updatedAt: nowTimestamp(),
    })
  );

  // Unbanning (banned: false) MUST fail permanently, even on a new UTC day!
  await assertFails(
    guardRefAlice.update({
      dateKey: day2,
      overlayMcqCount: 0,
      banned: false,
      updatedAt: nowTimestamp(),
    })
  );

  // New UTC day rollover succeeds while preserving strikeCount & banned=true
  await assertSucceeds(
    guardRefAlice.update({
      dateKey: day2,
      lastUid: ALICE_UID,
      seenUidCountToday: 1,
      overlayMcqCount: 0,
      askTextCount: 0,
      askPhotoCount: 0,
      askPdfCount: 0,
      googleFormsCount: 0,
      updatedAt: nowTimestamp(),
    })
  );

  // Rolling clock backward to day1 MUST fail
  await assertFails(
    guardRefAlice.update({
      dateKey: day1,
      updatedAt: nowTimestamp(),
    })
  );

  // Deleting device guard MUST fail
  await assertFails(guardRefAlice.delete());
});

test("9. SharedFormSolution: classmate get & self-healing re-solve update succeed; list, delete, revision rollback, and unauth fail", async () => {
  const formHash = "f".repeat(64);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const formRefAlice = aliceDb.collection("sharedFormSolutions").doc(formHash);
  const formRefBob = bobDb.collection("sharedFormSolutions").doc(formHash);

  const initialEntries = JSON.stringify([
    {
      k: "which gas do plants absorb during photosynthesis",
      a: ["Carbon Dioxide"],
      m: false,
      s: false,
      e: "Plants take in CO2 during photosynthesis.",
    },
  ]);

  const initialPayload = {
    formHash,
    formTitle: "BTech Semester 4 Quiz",
    createdByUid: ALICE_UID,
    updatedByUid: ALICE_UID,
    questionCount: 1,
    revision: 1,
    entriesJson: initialEntries,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };

  // Unauthenticated read/write denied
  await assertFails(unauthDb.collection("sharedFormSolutions").doc(formHash).get());
  await assertFails(unauthDb.collection("sharedFormSolutions").doc(formHash).set(initialPayload));

  // First student (Alice) caches solved Google Form
  await assertSucceeds(formRefAlice.set(initialPayload));

  // Classmate (Bob) reads instantly with zero AI calls
  await assertSucceeds(formRefBob.get());

  // Listing all cached forms is strictly denied
  await assertFails(bobDb.collection("sharedFormSolutions").get());

  // Classmate (Bob) re-solves a wrong question -> self-healing update with revision 2 succeeds
  const healedEntries = JSON.stringify([
    {
      k: "which gas do plants absorb during photosynthesis",
      a: ["Carbon Dioxide (CO2)"],
      m: false,
      s: false,
      e: "Re-verified: Plants absorb CO2.",
    },
  ]);
  await assertSucceeds(
    formRefBob.update({
      formTitle: "BTech Semester 4 Quiz",
      updatedByUid: BOB_UID,
      questionCount: 1,
      revision: 2,
      entriesJson: healedEntries,
      updatedAt: nowTimestamp(),
    })
  );

  // Rolling back revision MUST fail
  await assertFails(
    formRefAlice.update({
      updatedByUid: ALICE_UID,
      revision: 1,
      updatedAt: nowTimestamp(),
    })
  );

  // Tampering immutable createdByUid MUST fail
  await assertFails(
    formRefBob.update({
      createdByUid: BOB_UID,
      updatedByUid: BOB_UID,
      revision: 3,
      updatedAt: nowTimestamp(),
    })
  );

  // Spoofing updatedByUid as another user MUST fail
  await assertFails(
    formRefBob.update({
      updatedByUid: ALICE_UID,
      revision: 3,
      updatedAt: nowTimestamp(),
    })
  );

  // Deleting a class's cached form MUST fail
  await assertFails(formRefAlice.delete());
  await assertFails(formRefBob.delete());
});

test("Live Class Rooms: max 40 cap, host-locked quizUrl, pending/host-approval members, and host-only answer broadcast", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const roomCode = "K7M29Q";
  const expiresAtMs = Date.now() + 24 * 60 * 60 * 1000;
  const roomRefAlice = aliceDb.collection("liveClassRooms").doc(roomCode);
  const roomRefBob = bobDb.collection("liveClassRooms").doc(roomCode);

  const roomPayload = {
    roomCode,
    roomName: "CSE-B Networks Quiz",
    subject: "CODING",
    hostUid: ALICE_UID,
    hostName: "Alice",
    hostPhotoUrl: "https://example.com/alice.png",
    quizUrl: "https://docs.google.com/forms/d/e/1FAIpQLSe/viewform",
    maxMembers: 40,
    memberCount: 1,
    questionCount: 0,
    active: true,
    expiresAtMs,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("liveClassRooms").doc(roomCode).set(roomPayload));
  await assertFails(unauthDb.collection("liveClassRooms").doc(roomCode).get());

  // Listing all rooms is strictly denied
  await assertFails(aliceDb.collection("liveClassRooms").get());

  // Host (Alice) creates room with valid 6-char code and maxMembers = 40
  await assertSucceeds(roomRefAlice.set(roomPayload));

  // Creating a room with maxMembers > 40 MUST fail
  await assertFails(
    aliceDb.collection("liveClassRooms").doc("Z9X8C7").set({
      ...roomPayload,
      roomCode: "Z9X8C7",
      maxMembers: 50,
    })
  );

  // Host registers herself as approved host in members subcollection
  const aliceMemberRef = roomRefAlice.collection("members").doc(ALICE_UID);
  await assertSucceeds(
    aliceMemberRef.set({
      uid: ALICE_UID,
      roomCode,
      displayName: "Alice",
      photoUrl: "https://example.com/alice.png",
      role: "host",
      status: "approved",
      joinedAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Classmate (Bob) fetches room by code
  await assertSucceeds(roomRefBob.get());

  // Non-host (Bob) CANNOT change the locked quizUrl or memberCount or close room
  await assertFails(
    roomRefBob.update({
      quizUrl: "https://evil.example.com/quiz",
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    roomRefBob.update({
      memberCount: 2,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    roomRefBob.update({
      active: false,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(roomRefBob.delete());

  // Classmate (Bob) CANNOT self-approve when requesting to join
  const bobMemberRefBob = roomRefBob.collection("members").doc(BOB_UID);
  const bobMemberRefAlice = roomRefAlice.collection("members").doc(BOB_UID);
  await assertFails(
    bobMemberRefBob.set({
      uid: BOB_UID,
      roomCode,
      displayName: "Bob",
      photoUrl: "https://example.com/bob.png",
      role: "member",
      status: "approved",
      joinedAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Privacy guard: ghost PII fields like email are strictly rejected in members
  await assertFails(
    bobMemberRefBob.set({
      uid: BOB_UID,
      roomCode,
      displayName: "Bob",
      photoUrl: "",
      email: "bob@example.com",
      role: "member",
      status: "pending",
      joinedAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Classmate (Bob) submits valid pending join request
  await assertSucceeds(
    bobMemberRefBob.set({
      uid: BOB_UID,
      roomCode,
      displayName: "Bob",
      photoUrl: "https://example.com/bob.png",
      role: "member",
      status: "pending",
      joinedAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Bob CANNOT self-approve via update
  await assertFails(
    bobMemberRefBob.update({
      status: "approved",
      updatedAt: nowTimestamp(),
    })
  );

  // Host (Alice) approves Bob and increments memberCount to 2 (<= 40)
  await assertSucceeds(
    bobMemberRefAlice.update({
      status: "approved",
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(
    roomRefAlice.update({
      memberCount: 2,
      updatedAt: nowTimestamp(),
    })
  );

  // Host CANNOT exceed 40 members
  await assertFails(
    roomRefAlice.update({
      memberCount: 41,
      updatedAt: nowTimestamp(),
    })
  );

  // Host (Alice) CAN update locked quizUrl for the whole room
  await assertSucceeds(
    roomRefAlice.update({
      quizUrl: "https://forms.office.com/r/newexam123",
      updatedAt: nowTimestamp(),
    })
  );

  // Non-host (Bob) CANNOT broadcast answers directly
  const ansRefAlice = roomRefAlice.collection("answers").doc("q_1_abc123");
  const ansRefBob = roomRefBob.collection("answers").doc("q_1_abc123");
  const answerPayload = {
    roomCode,
    questionNumber: 1,
    questionText: "Which protocol is connectionless?",
    optionsJson: JSON.stringify(["TCP", "UDP", "HTTP", "FTP"]),
    answerBadge: "B",
    shortAnswer: "UDP",
    explanation: "UDP sends datagrams without a handshake.",
    sourceLabel: "Host Solved",
    sharedByUid: ALICE_UID,
    sharedByName: "Alice",
    confirmCount: 1,
    disputeCount: 0,
    verifiedByAi: false,
    updatedByUid: ALICE_UID,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
  await assertFails(
    ansRefBob.set({
      ...answerPayload,
      sharedByUid: BOB_UID,
      sharedByName: "Bob",
      updatedByUid: BOB_UID,
    })
  );

  // Host (Alice) publishes answer and updates questionCount
  await assertSucceeds(ansRefAlice.set(answerPayload));
  await assertSucceeds(
    roomRefAlice.update({
      questionCount: 1,
      updatedAt: nowTimestamp(),
    })
  );

  // Classmate (Bob) reads live answers and members list (only displayName + photoUrl)
  await assertSucceeds(roomRefBob.collection("answers").orderBy("questionNumber").get());
  await assertSucceeds(roomRefBob.collection("members").get());

  // Classmate (Bob) can vote Confirm, but CANNOT alter answerBadge/shortAnswer
  await assertSucceeds(
    ansRefBob.update({
      confirmCount: 2,
      updatedByUid: BOB_UID,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    ansRefBob.update({
      answerBadge: "A",
      shortAnswer: "TCP",
      updatedByUid: BOB_UID,
      updatedAt: nowTimestamp(),
    })
  );

  // Host (Alice) CAN AI-verify and update answer text, remove a member, and close room
  await assertSucceeds(
    ansRefAlice.update({
      answerBadge: "B",
      shortAnswer: "UDP",
      explanation: "AI Verified: UDP is connectionless.",
      sourceLabel: "AI Verified",
      verifiedByAi: true,
      updatedByUid: ALICE_UID,
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(bobMemberRefAlice.delete());
  await assertSucceeds(
    roomRefAlice.update({
      active: false,
      memberCount: 1,
      updatedAt: nowTimestamp(),
    })
  );
});



