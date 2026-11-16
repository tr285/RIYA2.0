const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const ts = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write user profile or memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem1")
      .set({
        userId: ALICE_UID,
        category: "general",
        key: "color",
        value: "blue",
        createdAt: ts(),
        updatedAt: ts(),
      })
  );
});

test("Owner can create and read own profile, memory, task, permission rule, and audit log", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      themeMode: "dark",
      defaultStartupMode: "text",
      voiceLanguage: "en",
      responseStyle: "friendly",
      waveformAnimationsEnabled: true,
      autoMemoryRecallEnabled: true,
      voiceResponsesEnabled: true,
      preferredModel: "gemini-3.8-flash",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-4-argon",
      updatedAt: ts(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-1.5-pro",
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        category: "preferences",
        key: "editor",
        value: "Neovim",
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_logs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        permissionKey: "memory.write",
        decision: "allow",
        reason: "Explicit user remember",
        createdAt: ts(),
      })
  );
});

test("Cross-user access is strictly denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set({
        userId: ALICE_UID,
        title: "Prepare demo",
        description: "Test RIYA",
        priority: "high",
        status: "pending",
        category: "work",
        dueDateLabel: "Today",
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_2")
      .set({
        userId: ALICE_UID,
        title: "Spoof",
        description: "",
        priority: "low",
        status: "pending",
        category: "general",
        dueDateLabel: "",
        createdAt: ts(),
        updatedAt: ts(),
      })
  );
});

test("Shadow fields, invalid enums, and audit log mutation are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_ghost")
      .set({
        userId: ALICE_UID,
        category: "general",
        key: "test",
        value: "val",
        isAdmin: true,
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Invalid category rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_badcat")
      .set({
        userId: ALICE_UID,
        category: "invalid_cat",
        key: "test",
        value: "val",
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Audit logs cannot be updated or deleted
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_logs")
      .doc("log_immutable")
      .set({
        userId: ALICE_UID,
        permissionKey: "system.info",
        decision: "allow",
        reason: "Checked status",
        createdAt: ts(),
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_logs")
      .doc("log_immutable")
      .update({ decision: "deny" })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("audit_logs")
      .doc("log_immutable")
      .delete()
  );
});

test("Autonomous run terminal state locking prevents updates after completion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const runRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("autonomous_runs")
    .doc("run_1");

  await assertSucceeds(
    runRef.set({
      userId: ALICE_UID,
      goal: "Organize weekly summary",
      status: "running",
      stepsSummary: "Step 1 running",
      stepsCompleted: 0,
      stepsTotal: 2,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    runRef.update({
      status: "completed",
      stepsSummary: "Step 1 & 2 done",
      stepsCompleted: 2,
      updatedAt: ts(),
    })
  );

  // Terminal state cannot be mutated
  await assertFails(
    runRef.update({
      status: "running",
      updatedAt: ts(),
    })
  );
});
