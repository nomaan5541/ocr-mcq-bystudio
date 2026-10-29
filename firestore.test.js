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

function validPayload(uid = ALICE_UID, overrides = {}) {
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

test("1. Unauthenticated user cannot list or get solvedQuestions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("q_1").get()
  );
});

test("2. Authenticated owner can create, get, list (matching userId filter), bookmark-update, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions").doc("q_valid_1");

  await assertSucceeds(docRef.set(validPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("solvedQuestions")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    docRef.update({
      bookmarked: true,
      mistakeDeck: true,
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("3. Cross-user isolation: Alice cannot read, create, update, or delete Bob's solvedQuestions", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("solvedQuestions").doc("bob_q1").set(validPayload(BOB_UID))
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDocAsAlice = aliceDb.collection("users").doc(BOB_UID).collection("solvedQuestions").doc("bob_q1");

  await assertFails(bobDocAsAlice.get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("solvedQuestions").where("userId", "==", BOB_UID).get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("solvedQuestions").doc("spoof").set(validPayload(ALICE_UID))
  );
  await assertFails(
    bobDocAsAlice.update({
      bookmarked: true,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(bobDocAsAlice.delete());
});

test("4. Identity spoofing & shadow ghost fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("solvedQuestions");

  // Spoof userId inside Alice's own subcollection
  await assertFails(col.doc("spoof_uid").set(validPayload(BOB_UID)));

  // Ghost field on create
  await assertFails(col.doc("ghost_create").set(validPayload(ALICE_UID, { isAdmin: true })));

  // Valid create, then ghost field or value poisoning on update
  await assertSucceeds(col.doc("q_base").set(validPayload(ALICE_UID)));
  await assertFails(
    col.doc("q_base").update({
      bookmarked: true,
      isVerified: true,
      updatedAt: nowTimestamp(),
    })
  );
  await assertFails(
    col.doc("q_base").update({
      confidence: 150,
      updatedAt: nowTimestamp(),
    })
  );
});
