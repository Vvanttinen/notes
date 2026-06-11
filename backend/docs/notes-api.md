# Private Notes API

The Notes API is a protected Microsoft Entra resource-server API. Every route
requires an authenticated access token with the `access_as_user` scope. Notes
are private to the resolved local user; cross-user, missing, and deleted notes
all return the same `404 Not Found` response.

## Resource

Active note responses contain:

```json
{
  "id": "00000000-0000-0000-0000-000000000001",
  "title": "Example title",
  "body": "Example body",
  "createdAt": "2026-06-11T12:00:00Z",
  "updatedAt": "2026-06-11T12:00:00Z",
  "revision": 0
}
```

Owner identifiers and deletion timestamps are never exposed.

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/api/notes` | Creates a note from a client-generated UUID. Returns `201 Created`, `Location`, a strong `ETag`, and the note. |
| `GET` | `/api/notes` | Lists the current user's active notes ordered by `updatedAt DESC, id ASC`. |
| `GET` | `/api/notes/{noteId}` | Returns an active owned note and its strong `ETag`. |
| `PUT` | `/api/notes/{noteId}` | Replaces title and body when `If-Match` contains the current ETag. |
| `DELETE` | `/api/notes/{noteId}` | Soft-deletes the note when `If-Match` contains the current ETag. |

Creation requires non-null `id`, `title`, and `body`. Updates require non-null
`title` and `body`. Titles may contain at most 255 characters. Empty title and
body strings are accepted and content is not trimmed or rewritten.

A note UUID can never be reused, including after soft deletion. UUID collisions
return a generic `409 Conflict`.

## Revisions and ETags

The JPA optimistic-lock revision is exposed as a strong decimal ETag. Revision
zero is represented exactly as `"0"`.

`PUT` and `DELETE` require exactly one quoted, strong, non-negative decimal
`If-Match` value. Missing headers return `428 Precondition Required`. Weak tags,
wildcards, negative values, overflows, unquoted values, and tag lists return
`400 Bad Request`. A valid stale revision returns `412 Precondition Failed`
without applying the mutation.

## Errors

Notes API errors use sanitized RFC 9457-style `application/problem+json`
responses. Expected statuses are:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Invalid DTO or malformed `If-Match`. |
| `404 Not Found` | Missing, cross-user, or soft-deleted note. |
| `409 Conflict` | Note UUID already exists. |
| `412 Precondition Failed` | Valid but stale revision. |
| `428 Precondition Required` | Required `If-Match` is missing. |

Responses do not disclose owner IDs, Entra claims, tokens, database details, or
tombstone state.

## Deferred Work

Synchronization cursors, batch synchronization, conflict-resolution workflows,
restore, search, pagination, Android networking, and frontend integration are
not part of this API slice.
