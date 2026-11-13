# Intelligent Cloud Document Analyst (Spring Boot)

A Spring Boot rebuild of my Assignment 4 n8n pipeline, as a portfolio piece.
The n8n version (`../Assignment 4`) is still the course submission. This
version is intended to do everything that one does, bonuses included: Gemini
Vision, failure alerting, and retry using Google's suggested delay.

> **Status:** steps 0–2 of 17 done (configuration, core result types, file check). Step 3 (text extraction) next. This README grows with the build.

## What it will do

You drop a PDF, Word or text file into `incoming/`. Each document is:

1. claimed: moved into `work/` and published as one RabbitMQ message (D4, D5)
2. checked: empty, oversized and unsupported files are rejected, with the type taken from the bytes
3. extracted with Tika. Scanned pages and charts are rendered with PDFBox for Gemini Vision
4. analysed by Gemini (`gemini-3.8-flash`) against a fixed response schema (D2)
5. enriched by the Metadata API's business rules
6. alerted on if it is confidential. The alert gives the reasons and none of the contents
7. recorded as one row per document in Google Sheets, plus a JSON and a Markdown file
8. reported in a notification email

Any failure is recorded in the sheet's Errors tab and in an error file. The
message is parked for replay, and a failure email is sent (D4).

## Design

Why it is built this way is in [`docs/decisions.md`](docs/decisions.md).
In short:

- **Business logic is plain Java** behind ports. Spring is only at the edges (D3)
- **Outcomes are sealed types**, so a case that isn't handled is a compile error (D3)
- **Parallel processing without lost sheet rows** (D6, to be proven at step 15)

## Stack

Java 25 · Spring Boot 4.1.1 · Maven · Spring AI 2.0 (Google GenAI) · Apache Tika and PDFBox ·
RabbitMQ · Resilience4j · Google Sheets API (service account) · Spring Mail · docker-compose

## Running it locally

### Prerequisites
- JDK 25, Maven (or the included `mvnw`), Docker Desktop
- Eclipse 2025-12 or later, with the project's JRE set to JDK 25
- The n8n project in `../Assignment 4`, because its `metadata_api` is built from there

### Start the infrastructure
The n8n stack also uses port 8000, so stop it first (`docker compose stop` in `Assignment 4`). Then, from this folder:

```
docker compose up -d --build
```

| Service | Address | What it is for |
|---|---|---|
| RabbitMQ | `localhost:5672`, management UI at <http://localhost:15672> (guest/guest) | Document messages and failure handling (D4) |
| Metadata API | <http://localhost:8000/health> | Categories and business rules (`/categories`, `/enrich`) |
| Mailpit | SMTP `localhost:1025`, inbox at <http://localhost:8025> | Catches every email during development |

### Start the app
Run `DocumentAnalystApplication` from Eclipse, or `./mvnw spring-boot:run`.
<http://localhost:8080/actuator/health> should show `UP`, with `rabbit` and `mail` UP.

## Build log

| Step | What | Result |
|---|---|---|
| 0 | Scaffold (Boot 4.1.1, Java 25), docker-compose, health check | Done 9 Oct 2026: all services up, `rabbit` and `mail` UP |
| 1a | `AnalystProperties`, validated at startup (D7) | Done 9 Oct 2026: `max-attempts: 0` refused at startup |
| 1b | `Stage`, `IncomingDocument`, sealed `Outcome` (D3) | Done 10 Oct 2026 |
| 1c | `OutcomeFailureTest` (10), `OutcomeSwitchTest` (1), exhaustiveness check | Done 10 Oct 2026: adding a third `Outcome` type broke the build (D3) |
| 2a | `DocumentType`, `FileFacts`, sealed `FileCheckResult`, `FileCheck` rules (D8) | Done 10 Oct 2026: `FileCheckTest` 9/9 green, including the limit boundary and empty-before-type ordering |
| 2b | `FileInspector` port, `TikaFileInspector` (size, SHA-256, type from the bytes) | Done 10 Oct 2026: 29/29 green; a renamed xlsx is still rejected; T01's hash gives n8n's `document_id` |
