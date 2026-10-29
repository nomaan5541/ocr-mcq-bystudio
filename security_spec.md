# Prism AI Firestore Security Specification

## 1. Data Invariants
- Every solved study record lives at `/users/{userId}/solvedQuestions/{questionId}` and belongs exclusively to `userId`.
- Unauthenticated callers (`request.auth == null`) cannot read, list, create, update, or delete any document.
- Cross-user reads, queries, writes, and deletes (`request.auth.uid != userId` or `resource.data.userId != request.auth.uid`) are strictly rejected.
- `userId` and `createdAt` are immutable after document creation.
- All strings have explicit `.size()` bounds; all document IDs match `^[a-zA-Z0-9_\-]+$` (1..128 chars).
- Timestamps (`createdAt`, `updatedAt`) must be server-resolved `timestamp` values `<= request.time`.
- Zero PII (`email`, `phone`, `address`) is stored in `solvedQuestions`.

## 2. Dirty Dozen Adversarial Payloads
1. **Unauthenticated List**: `unauthDb.collection("users/alice/solvedQuestions").get()` -> `PERMISSION_DENIED`
2. **Cross-User Get**: Alice reads `users/bob/solvedQuestions/q1` -> `PERMISSION_DENIED`
3. **Cross-User Create (Path Spoof)**: Alice creates `users/bob/solvedQuestions/q1` -> `PERMISSION_DENIED`
4. **Identity Spoof in Body**: Alice creates `users/alice/solvedQuestions/q1` with `userId: "bob"` -> `PERMISSION_DENIED`
5. **Shadow Create (Ghost Key)**: Create payload includes `isAdmin: true` -> `PERMISSION_DENIED`
6. **Shadow Update (Ghost Key)**: Update payload includes `isVerified: true` -> `PERMISSION_DENIED`
7. **Immortal Field Mutation (`createdAt`)**: Update changes `createdAt` -> `PERMISSION_DENIED`
8. **Immortal Field Mutation (`userId`)**: Update changes `userId` -> `PERMISSION_DENIED`
9. **Value Poisoning (`confidence`)**: Update sets `confidence: 150` or `"high"` -> `PERMISSION_DENIED`
10. **Enum Poisoning (`questionType`)**: Create sets `questionType: "HACKED"` -> `PERMISSION_DENIED`
11. **Oversized String DoW**: Create sets `questionText` to 5,000 chars -> `PERMISSION_DENIED`
12. **Unfiltered List Query**: Query without `.where("userId", "==", uid)` when mismatched data exists or cross-tenant path -> `PERMISSION_DENIED`
