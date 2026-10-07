# MiniCloud — Claude Code Project Rules

## Objective

Build a minimal private web application for authenticated file upload/download.

Primary goals:
- Very low CPU and RAM usage.
- One application container.
- Simple Railway deployment.
- Persistent filesystem under `/data`.
- Strong file-handling security.
- Fast, deterministic unit tests.
- No unnecessary infrastructure.

## Stack

- Java 21+
- Spring Boot
- Spring Security
- Thymeleaf
- HTMX only when useful
- Gradle
- Docker
- Railway Persistent Volume

Do NOT introduce PostgreSQL, Redis, Kafka, Kubernetes, a SPA, or additional services unless a concrete requirement justifies them.

## Architecture

Preferred:

Browser
-> HTTPS
-> Spring Boot
-> filesystem `/data/files`

Temporary uploads should use:

`/data/quarantine`

Storage must never depend on the application classpath.

Controllers must be thin:

Controller -> Service -> Storage/Validation

Use constructor injection.

## Security

All client-controlled values are untrusted:
- filenames
- extensions
- MIME types
- paths
- IDs
- request sizes

Uploads must:
1. Require authentication.
2. Enforce configurable size limits.
3. Validate filename/path safely.
4. Prevent path traversal.
5. Avoid using client filenames as trusted storage paths.
6. Prefer server-generated storage IDs.
7. Validate MIME/signatures when applicable.
8. Calculate SHA-256 when practical.
9. Never execute uploaded files.
10. Keep untrusted files outside application resources.
11. Use quarantine before final storage when scanning is enabled.

Downloads must:
1. Require authentication.
2. Verify authorization.
3. Resolve only server-controlled file identifiers.
4. Ensure the resulting path remains below the storage root.
5. Stream the file.
6. Prefer `Content-Disposition: attachment`.
7. Use `X-Content-Type-Options: nosniff`.

Never claim an antivirus scan makes a file 100% safe.

## Resource usage

Prefer streaming over `byte[]`.

Do not use `Files.readAllBytes()` for arbitrary uploads/downloads.

Avoid:
- unnecessary copies
- Base64 file encoding
- large frontend bundles
- background workers for simple operations
- permanent antivirus services unless required
- databases when filesystem storage is sufficient

Before adding a dependency/service ask:

> Can this requirement be solved with Java/Spring or the existing project more simply?

If yes, do that.

## Malware scanning

Malware scanning is optional.

Create an abstraction such as:

`MalwareScanner`

Possible implementations:
- `NoOpMalwareScanner`
- `ClamAvMalwareScanner`

Do not make ClamAV mandatory for the lightweight deployment.

Preferred flow:

upload -> quarantine -> validation -> scan -> final storage

## Testing

Use JUnit 5 and AssertJ.

Use Mockito only when mocking adds value.

Prioritize unit tests for:
- path traversal
- filename validation
- size limits
- MIME/signature validation
- SHA-256
- storage isolation
- missing files
- authorization
- scanner rejection/errors
- safe downloads

Do not test framework behavior or trivial getters/setters.

Run:

`./gradlew test`

and, when appropriate:

`./gradlew build`

Never claim tests passed unless they were actually executed.

## Change discipline

- Inspect existing code before modifying it.
- Make the smallest coherent change.
- Do not rewrite unrelated code.
- Do not weaken security to make tests pass.
- Do not add speculative abstractions.
- Do not add infrastructure just because it is popular.
