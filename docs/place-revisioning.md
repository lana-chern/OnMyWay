# Place revisioning and repeat moderation

## Decision

A place is a stable aggregate/identity (city, owner, lifecycle timestamps and archive state). Its editable content is stored in immutable-after-approval revisions. A place may have one published revision and at most one working revision.

A published revision remains the public source of truth until a newer revision is approved. Editing a published place must never mutate the published revision or its child collections.

## Data model

- `Place`: identity, owner, city, created/updated timestamps, archived flag/state, reference to the currently published revision (nullable before first publication).
- `PlaceRevision`: place, optimistic-lock version, revision status, name, description, latitude, longitude, rejection reason, created/updated timestamps.
- `PlaceRevisionPhoto`, `PlaceRevisionContact`, `PlaceRevisionOpeningHours`: child rows owned by a revision, not by the place.
- Revision statuses: `DRAFT`, `PENDING_MODERATION`, `REJECTED`, `PUBLISHED`, `SUPERSEDED`.
- At most one working revision (`DRAFT`, `PENDING_MODERATION`, or `REJECTED`) per place. A partial unique index enforces this invariant in PostgreSQL.

## Lifecycle

### First publication

1. Organizer creates a place and its initial `DRAFT` revision.
2. Organizer edits the working revision; child collections are replaced atomically as today.
3. Organizer submits it: `DRAFT -> PENDING_MODERATION`.
4. Moderator approves: revision becomes `PUBLISHED`, and the place's published-revision pointer is set.
5. Moderator rejects: revision becomes `REJECTED`; organizer may edit the same working revision and resubmit it.

### Changes to an already published place

1. On the first edit, the service creates a new `DRAFT` revision by copying the currently published revision, including all child collections.
2. Organizer edits that working revision. The published revision and its photos, contacts and opening hours remain unchanged.
3. Organizer explicitly submits the working revision for moderation.
4. While it is pending, the organizer may continue editing it. Each edit increments the revision version, so a moderator's stale decision is rejected.
5. On approval, the previous published revision becomes `SUPERSEDED`, the candidate becomes `PUBLISHED`, and the place pointer changes atomically.
6. On rejection, the old published revision remains public; the candidate becomes `REJECTED` and can be corrected/resubmitted.

### Archive

Archiving the place hides it from public reads and prevents further organizer edits/submissions. Revisions are retained for audit/history.

## API behavior

- Public place reads resolve content only through the published-revision pointer.
- Management reads return the working revision when one exists; otherwise they return the published revision (or initial draft).
- `PUT /api/places/{id}` updates the working revision. For a published place with no working revision, it creates a copy-on-write draft first.
- `POST /api/places/{id}/submit-for-moderation` submits the working revision.
- Moderation queue returns the latest working revision, not the published content.
- Publish/reject requests carry the revision version, not a mutable place-content version.
- The response must make the revision status and revision version explicit. Public responses must not expose a pending/rejected revision or its rejection reason.

## Migration

- Add revision and revision-child tables and a published-revision reference.
- Backfill one revision for every existing place, copying scalar fields and all three child collections.
- Existing `PUBLISHED` places get a published revision and pointer.
- Existing `DRAFT`, `PENDING_MODERATION`, and `REJECTED` places get a working revision with the matching state.
- Existing `ARCHIVED` places remain archived; their content is retained in a revision but is not publicly addressable.
- Only after backfill is verified should application code stop reading/writing the legacy place content and child tables. Legacy tables/columns should be removed in a separate migration after the application cutover is proven.

## Concurrency and transaction invariants

- A revision has its own JPA `@Version`; edits and moderation decisions are serialized by optimistic locking.
- Approving a revision, superseding the previous published revision, and updating the place pointer happen in one transaction.
- Only a `PENDING_MODERATION` revision can be approved/rejected.
- The unique working-revision index prevents duplicate drafts under concurrent first edits; creation must handle a uniqueness race by reloading the existing working revision.
- Child collection replacement is atomic and increments the revision version even when scalar fields are unchanged.
- Never join-fetch multiple List child collections in one query; retain a query plan that avoids Hibernate multiple-bag issues.

## Delivery plan

1. Schema, entities, migration/backfill and repository-level migration tests.
2. Revision lifecycle service and unit/integration tests.
3. API/DTO/controller cutover for public, management and moderation reads/writes.
4. Concurrency, child collection replacement, authorization and regression tests; remove legacy place-content storage only after the cutover is green.
5. Performance work (N+1 mitigation) and paginated moderation queue as separate follow-up changes.
