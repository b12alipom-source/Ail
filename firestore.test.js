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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("videoProjects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("studyNotebooks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("folders").doc("fld_1").set({
      syncId: "fld_1",
      name: "Physics",
      accentKey: "sapphire",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Folders: owner create, partial update, list, and reject shadow/tampered fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const folderRef = aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("fld_1");

  await assertSucceeds(
    folderRef.set({
      syncId: "fld_1",
      name: "محاضرات الفيزياء",
      accentKey: "sapphire",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    folderRef.update({
      name: "فيزياء الفصل الأول",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").get()
  );

  // Reject immutable createdAt tamper
  await assertFails(
    folderRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("fld_2").set({
      syncId: "fld_2",
      name: "Biology",
      accentKey: "emerald",
      isAdmin: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("VideoProjects & CueChunks: parent-gated chunk writes, updates, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 10000);

  const projRef = aliceDb.collection("users").doc(ALICE_UID).collection("videoProjects").doc("proj_1");
  const chunkRef = projRef.collection("cueChunks").doc("chunk_0");

  // Orphaned chunk write before parent exists must fail
  await assertFails(
    chunkRef.set({
      chunkIndex: 0,
      cues: [{ orderIndex: 0, startMs: 0, endMs: 1200, originalText: "Hello", translatedText: "مرحباً" }],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid parent project creation (no optional folderSyncId because null values omitted)
  await assertSucceeds(
    projRef.set({
      syncId: "proj_1",
      title: "Lecture 1 - Quantum Mechanics",
      durationMs: 185000,
      sourceLanguageCode: "en",
      sourceLanguageName: "الإنجليزية",
      targetLanguageCode: "ar",
      targetLanguageName: "العربية",
      summary: "مقدمة حول ميكانيكا الكم",
      cueCount: 2,
      hasArchivedVideo: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Chunk creation now succeeds
  await assertSucceeds(
    chunkRef.set({
      chunkIndex: 0,
      cues: [
        { orderIndex: 0, startMs: 0, endMs: 1200, originalText: "Hello", translatedText: "مرحباً" },
        { orderIndex: 1, startMs: 1300, endMs: 2500, originalText: "Welcome", translatedText: "أهلاً بكم" },
      ],
      createdAt: past,
      updatedAt: past,
    })
  );

  // Chunk update preserves chunkIndex & createdAt
  await assertSucceeds(
    chunkRef.update({
      cues: [
        { orderIndex: 0, startMs: 0, endMs: 1200, originalText: "Hello", translatedText: "أهلاً" },
      ],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List subcollections allowed for owner (including empty queries)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("videoProjects").get());
  await assertSucceeds(projRef.collection("cueChunks").get());

  // Cross-user isolation: Bob cannot read or modify Alice's project or chunks
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("videoProjects").doc("proj_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("videoProjects").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("videoProjects").doc("proj_1")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );

  // Reject future timestamp tamper
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("videoProjects").doc("proj_future").set({
      syncId: "proj_future",
      title: "Future",
      durationMs: 1000,
      sourceLanguageCode: "en",
      sourceLanguageName: "الإنجليزية",
      targetLanguageCode: "ar",
      targetLanguageName: "العربية",
      summary: "",
      cueCount: 0,
      hasArchivedVideo: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("StudyNotebooks & VoiceTranslations: full create/update/list validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 8000);

  const studyRef = aliceDb.collection("users").doc(ALICE_UID).collection("studyNotebooks").doc("nb_1");
  await assertSucceeds(
    studyRef.set({
      syncId: "nb_1",
      title: "الأحياء - الخلية",
      preferredMethod: "FOCUSED_SUMMARY",
      dailyMinutes: 60,
      totalDays: 5,
      summaryMarkdown: "## ملخص الخلية\nالميتوكوندريا مصنع الطاقة.",
      keyPointsText: "• الميتوكوندريا",
      dossierJson: "{\"overview\":\"ملخص\"}",
      mindMapJson: "{\"mindMapRootTitle\":\"الخلية\"}",
      lastEngineLabel: "سحابي • Gemini 3.1 Pro",
      attachments: [
        { displayName: "احياء.pdf", attachmentType: "pdf", mimeType: "application/pdf", extractedText: "نص مستخلص" },
      ],
      scheduleItems: [
        { dayNumber: 1, dayLabel: "اليوم 1", timeSlot: "صباحاً", taskTitle: "مراجعة", taskDetail: "تلخيص", durationMinutes: 40, completed: false },
      ],
      messages: [
        { userRole: true, messageText: "اشرح الميتوكوندريا", offlineGenerated: false, attachmentName: "", createdAtMs: Date.now() - 5000 },
      ],
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    studyRef.update({
      summaryMarkdown: "## ملخص محدث",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const voiceRef = aliceDb.collection("users").doc(ALICE_UID).collection("voiceTranslations").doc("v_1");
  await assertSucceeds(
    voiceRef.set({
      syncId: "v_1",
      sourceLanguageCode: "en",
      sourceLanguageName: "الإنجليزية",
      targetLanguageCode: "ar",
      targetLanguageName: "العربية",
      originalText: "Good morning",
      translatedText: "صباح الخير",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("studyNotebooks").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("voiceTranslations").get());

  // Cross-user denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("studyNotebooks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("voiceTranslations").get());
});
