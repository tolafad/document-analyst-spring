# Decisions log: Intelligent Cloud Document Analyst (Spring Boot)

A Spring Boot rebuild of the Assignment 4 n8n pipeline, as a portfolio piece.
The n8n build is still the course submission. Its decision log
(`../Assignment 4/docs/decisions.md`) is cited here as **n8n D1–D20**.
This log starts again at D1.

Each entry has a **status**:
- **Agreed:** decided, and not yet shown in running code.
- **Proven:** shown in running code or tests. The entry says where.
- **Open:** a hypothesis that a named step will test.

---

## D1: Stack

**Status:** Agreed, 9 October 2026.

| Concern | Choice | Notes |
|---|---|---|
| Language and build | Java 25 (LTS), Maven | 25 is the current LTS release. A non-LTS release (26, 27) is supported for six months, and libraries that rewrite bytecode (Mockito/ByteBuddy, Eclipse's compiler) lag behind each new JDK. Virtual threads stopped pinning carrier threads on `synchronized` in JDK 24 (JEP 491), which suits listener threads that wait on Gemini and on the per-document lock (D6). Imports into Eclipse as an existing Maven project |
| Framework | Spring Boot 4.1.1 (Spring Framework 7) | The latest release on start.spring.io on 9 Oct 2026. Spring AI 2.0 supports Boot 4.0 and 4.1. Jackson 3 uses `tools.jackson.*` packages |
| Gemini | Spring AI 2.0 Google GenAI, if the step-7 test passes (D2) | Model `gemini-3.8-flash`, thinking `high`, as in n8n D2 and D20 |
| Type detection and text | Apache Tika, PDF and Microsoft parser modules only | Type is detected from the bytes, not the extension |
| Scans and embedded images | PDFBox, which Tika already brings in | Tika cannot render a page to an image. Needed for T06 and T07 |
| Business rules | The existing `metadata_api` (`/categories`, `/enrich`), called with `RestClient` | One copy of the rules. `/extract` is no longer used |
| Messaging | RabbitMQ: one message per document, and a parking queue for failures (D4) | |
| Retry | `resilience4j-retry` core, configured in code, without the Spring starter | `intervalBiFunction` takes the wait from Google's reply. The rules are plain Java (D3) |
| Results sheet | Google Sheets API with a service account | No 7-day OAuth expiry. The sheet is shared with the service account's email |
| Email | Spring Mail over SMTP. Gmail with an app password when live, Mailpit in development | A service account cannot send as a personal Gmail address; that needs Workspace domain-wide delegation |
| Local infrastructure | docker-compose: RabbitMQ, `metadata-api`, Mailpit | The app runs from Eclipse |

**Why:** each choice follows the same rules as the n8n build. Where Spring AI
might not support one of those rules, step 7 tests it (D2).

---

## D2: Gemini through Spring AI, settled by a test

**Status:** Open, settled at step 7.

**The question:** the Spring AI 2.0 Google GenAI docs show API-key mode and
`thinking-level`. They do not show four things the design depends on:

1. **A custom response schema.** The classification enum comes from
   `/categories` at runtime (n8n D11). A schema generated from a record
   cannot hold that.
2. **A configurable base URL.** `gemini_fault_proxy.py` has to be put in
   front of Gemini for P3b and P3c.
3. **The HTTP status and Google's `RetryInfo.retryDelay` / `Retry-After`.**
   The retry rules cannot work without them (n8n D12).
4. **`finishReason`, the thinking-token count and the model version.** D13's
   checks need these, and so does the sheet's `model_version` column.

Spring AI also has its own retry layer, and a structured-output advisor that
asks the model again when the JSON does not validate. Neither may run here:
retries belong to D12's rules, and a bad answer is recorded with every
problem listed, not quietly asked again (n8n D13).

**The plan:** step 7 is a short test against the real API. It checks the four
points above, and that Spring AI's own retries can be switched off.
- If all pass, Spring AI is the Gemini adapter.
- If some fail, Spring AI still makes the call, and only the missing piece is
  handled outside it.
- Only if the retry rules cannot be supported at all does the whole call move
  to a `RestClient` adapter.

Whichever way it goes, the result and its evidence are recorded here, and the
`GeminiClient` interface keeps the change to one class.

---

## D3: Layering: plain Java in the core, Spring at the edges

**Status:** Agreed, 9 October 2026. The compile-time half was proven at step 1c (10 October 2026).

**Decision:**

- **Core** (`..analyst.core`): the pipeline, its rules and all formatting.
  It contains no Spring annotations and no I/O. It covers file check, text
  cleaning, the injection check, the Gemini request, the retry decision, the
  parse checks, the sheet row, output files, alert and notification text, and
  the failure record.
- **Ports:** core interfaces for everything outside the process:
  `TextExtractor`, `GeminiClient`, `MetadataApi`, `ResultSheet`,
  `OutputStore`, `Mailer` and `Clock`.
- **Adapters** (`..analyst.adapter.*`): Spring beans that implement the ports.
  They are Tika/PDFBox, Spring AI or RestClient, Google Sheets, the file
  system and Spring Mail.
- **Edges:** the folder watcher, the RabbitMQ listeners and the configuration.

**Failures are values, not exceptions.** `DocumentPipeline.process()` returns
a sealed `Outcome`: `Success`, or `Failure(stage, reason, detail,
completedSteps)`. The retry decision is also sealed: `Ok`, `Retry(delay)`
or `Fatal(reason)`. Callers handle these with a `switch` that has no
`default`, so an outcome that isn't handled is a compile error.

**Why:** n8n D12 found that the retry and fatal rules never matched. Items
fell through to a Fallback output, and the tests passed for the wrong reason.
Here the compiler makes every outcome explicit, and the decision rules are
unit-tested with one case per status code. Keeping the core free of Spring
means it is tested with in-memory fakes, with no Gemini, Google or RabbitMQ.

**Evidence (step 1c, 10 October 2026):** `OutcomeSwitchTest` switches over
`Outcome` with no `default`. A third type, `record Skipped()`, was added to
`permits` on purpose. Eclipse's compiler then reported *"A switch expression
should have a default case"* on that switch. The type was removed again and
the test went back to green.

Eclipse still let the test *run*, and it failed at runtime with
`java.lang.Error: Unresolved compilation problem`. That is how Eclipse treats
code with errors, not how Java does: `javac`, and therefore `./mvnw test` and
any CI build, refuses to compile the class at all. Eclipse is set to stop
before launching when there are errors (Run/Debug → Launching), so a broken
switch cannot run there either.

**Also settled in step 1:** core types check their own values in a compact
constructor, not with Bean Validation annotations. Nothing would run the
annotations on an object the code creates with `new`. Annotations are used
only where Spring validates, at the edges (D7).

---

## D4: Messaging and failure handling

**Status:** Agreed, 9 October 2026. Built at step 13.

**Decision:**

```
FolderWatcher ─► exchange analyst ─► queue documents (prefetch 1, concurrency 4, DLX ► analyst.failed)
                                          │
                                   DocumentListener ─► DocumentPipeline
                                          │ Success: ack
                                          │ Failure: FailureRecorder, then republish to analyst.failed
                                          │          with stage, reason and recorded=true, then ack
                                          │ Exception: RepublishMessageRecoverer ─► analyst.failed (+ x-exception headers)
analyst.failed (fanout) ─► documents.failed   parked, can be replayed
                       └─► failure-alerts ─► FailureAlertListener: "recorded" or "Unrecorded" email
```

**How this maps to n8n:**

| n8n | Spring |
|---|---|
| Record failure: an Errors row and an error file (n8n D17) | `FailureRecorder` |
| Stop on failure: the execution is marked failed | The message is parked in `documents.failed` |
| The separate failure-alerts workflow (n8n D18) | `FailureAlertListener` on its own queue, fed by the fanout |
| "Unrecorded" failure (no node after the crash) | An exception that never reached `FailureRecorder` arrives without `recorded=true` |

**Why a fanout:** parking and alerting are separate concerns. A queue that an
alert consumer empties cannot also be the place messages are kept for replay.

**Gemini retries happen in the process,** with Resilience4j inside the
listener, not through RabbitMQ redelivery. The longest wait is 60 s × 3 per
document, which a consumer thread can afford at this volume.
*Rejected for now:* retry queues with a per-message TTL. They don't block a
thread and they survive a restart, but they add topology and repeat
extraction on every attempt. They can be added later if volume grows.

---

## D5: File lifecycle: incoming → work → processed / failed

**Status:** Agreed, 9 October 2026. Built at step 14.

**Decision:** the documents root sits outside OneDrive (default
`C:\analyst-docs`), so the sync client never handles files that are being
processed.

1. `FolderWatcher` waits until a file in `incoming/` has stopped changing,
   which is the equivalent of n8n's Await Write Finish. It skips Word lock
   files (`~$*`).
2. It **moves** the file into `work/`, renamed with a UUID prefix so names
   can't clash. Only then does it publish the message, which carries the
   `work/` path.
3. When processing ends, the file is moved to `processed/` or `failed/`.

**Why:** the move is the claim. A file is picked up once without any
accept-once state in memory, a restart doesn't re-process anything, and
the file stays on disk for as long as its message exists, so a parked message
can be replayed. Re-running a document means dropping it into `incoming/`
again. That replaces n8n's "delete it and copy it back in" (n8n README).

---

## D6: Sheet writes under parallel processing

**Status:** Open, settled at step 15.

**Problem carried over:** n8n D19. With *Append or Update*, parallel runs
read the sheet, worked out the same "next empty row" and overwrote each
other. n8n fixed it with one run at a time.

**Proposal:** `ResultSheet.upsert(row)`:
1. Look up `document_id` in column A.
2. If it is found, update that row by its range.
3. If not, call `values.append` with `INSERT_ROWS` and take the row number
   from the response's `updatedRange`. That gives a confirmed row with no
   second lookup, replacing *Get row(s) in sheet*.

A lock per `document_id` stops two runs for the same file from both appending.

**Hypothesis to test:** Sheets applies appends to one spreadsheet one at a
time, so different documents can run fully in parallel. Step 15 writes the
prediction down first, then runs all 13 documents at listener concurrency 4,
and stress-tests appends from parallel threads.

**If it is disproved:** put only the sheet write behind one lock. Gemini,
which takes most of the ~15 s per document, still runs in parallel, so the
design goal still holds.

---

## D7: Configuration: one properties record, no secrets in it

**Status:** Proven, 9 October 2026 (step 1a).

**Decision:** everything n8n kept in its Config node goes in one
`@ConfigurationProperties("analyst")` record, `AnalystProperties`, with a
nested record for each concern: folders, file limits, the Metadata API,
Gemini, the sheet and notifications. It is bound from `application.yaml`
and validated at startup with `@Validated` and Jakarta constraints. A
missing or invalid value stops the app from starting, rather than surfacing
mid-run on the first document.

**Rules:**
- **Secrets are not in this record and not in `application.yaml`.** The
  Gemini API key, the service-account JSON path and the SMTP app password
  come from environment variables, which are kept out of git.
- **The core never sees `AnalystProperties`.** Core classes are given the
  plain values they need through their constructors (D3), so tests build
  them without Spring.
- **The defaults are visible.** They are written in `application.yaml`, not
  hidden in code, so the YAML reads like n8n's Config table (n8n README).

**Implementation note:** a record component cannot be called `notify`. Its
accessor would be `notify()`, which clashes with the final `Object.notify()`.
The recipient therefore sits under `analyst.mail.to`, in the record `Mail`.

**Evidence (step 1a):**
- With `analyst.gemini.max-attempts: 0` the app refused to start:
  `Property: analyst.gemini.maxAttempts, Value: "0", Reason: must be greater than 0`.
- While the nested records had no components yet, the app refused to start
  with `analyst.files … must not be null` and the same for four other
  sections. A section that binds to nothing becomes null, so `@NotNull` on
  each nested record is what catches it.

**Why:** n8n D12's lesson applies to configuration as well. A value that
silently falls back to a default is a rule nobody sees. Checking at startup
turns a wrong setting into an immediate, named error.

---

## D8: File check: facts gathered at the edge, rules decided in the core

**Status:** Proven, 10 October 2026 (steps 2a and 2b).

**Carried over from n8n D9:** empty, oversized and unsupported files are
rejected with a stated reason before the Metadata API or Gemini is called.

**Decision:** the check is split in two.
1. **`FileInspector`** (a port, implemented with Tika in the adapter layer)
   reads the file once and returns `FileFacts`: the exact size in bytes, the
   SHA-256, the type Tika detects from the content, and the original file
   name.
2. **`FileCheck`** (core, plain Java) applies the rules to those facts and
   returns a sealed `FileCheckResult`: `Accepted(type, sizeBytes, sha256)` or
   `Rejected(reason, detail)`.

**Rules, in order:**

| Check | Reason code | Detail |
|---|---|---|
| size is 0 | `empty_file` | `0 bytes` |
| size above `analyst.files.max-size` | `file_too_large` | the size and the limit, in bytes |
| detected type is not PDF, DOCX or plain text | `unsupported_type` | the detected media type and the file name |

**What changes from n8n:**
- **The size is exact.** The n8n Code node could only see n8n's rounded
  label (`"2.75 kB"`, n8n D9). Here it is `Files.size`.
- **The type comes from the bytes, not the extension.** n8n routed on the
  extension, so an `.xlsx` renamed to `.pdf` would have gone to the PDF
  extractor and failed there as `unreadable_pdf`. Here it is rejected at the
  file check as `unsupported_type`, with Tika's detected type in the detail.
  The file name is still passed to Tika as a hint, which it needs for plain
  text. Plain text has no magic bytes.

**Why the split:** the rules are the part worth unit testing at every
boundary (0 bytes, exactly the limit, one byte over, each type), and as plain
Java they need no files to test. Reading files and running Tika belongs at
the edge (D3), tested once against the real `sample_docs`.

**Trade-off:** the inspector hashes the whole file before the size rule runs,
so an oversized file is hashed too. That costs a fraction of a second at the
sizes expected here, and the Errors tab gets a hash for every rejected file,
as in n8n.

**Evidence (steps 2a and 2b, 10 October 2026):**
- `FileCheckTest`, plain Java with no files: empty file, empty file with an
  unsupported type (empty wins), exactly the limit (accepted), one byte over
  (`file_too_large`), each supported type (`@EnumSource` over
  `DocumentType`), xlsx (`unsupported_type`), rejection to a `FILE_CHECK`
  failure. All green.
- `TikaFileInspectorTest`, against the n8n `sample_docs`: T01 detected as
  `application/pdf`, T03 as DOCX, T05 as `text/plain`, B3 as the
  spreadsheetml type. B1 has size 0 and the SHA-256 of zero bytes
  (`e3b0c442…b855`), the constant n8n D9 relied on.
- **The extension does not decide.** `B3_renamed.pdf` (the xlsx bytes under
  a `.pdf` name) was still detected as spreadsheetml, and `FileCheck`
  rejected it as `unsupported_type`. In the n8n build that file would have
  gone to the PDF extractor.
- **The hash agrees with outside tools and with n8n.** T01's SHA-256 from
  `TikaFileInspector` is
  `0f5076fd36033825f0966f96fe771c6755964b2511d5c20e8e75b359f70047ff`, the
  same value `sha256sum` gives for the file. Fed through the Metadata API's
  UUID-5 rule, it gives `967cc61f-c84e-5f50-a65f-7b6cbefe2381`, the
  `document_id` n8n D13 recorded for T01. So this build will update the
  same sheet row for the same file.
- The project ran 29/29 on Eclipse Temurin JDK 25.0.4.
