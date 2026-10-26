/**
 * Session + permission model returned by the server for the current actor.
 *
 * SECURITY NOTE (Req 17.1): everything here is a UX affordance only. The server
 * is the single authority for authorization and enforces it on every protected
 * operation. The client uses these permissions purely to decide which
 * navigation entries and action controls to render — hiding a control NEVER
 * substitutes for server-side enforcement, and the client never sends a society
 * id to scope data (tenant scope is derived from the session — Req 1.2, BR1).
 */

/** The well-known roles in the platform (coarse-grained; permissions are authoritative). */
export type Role =
  | 'PLATFORM_ADMIN'
  | 'SOCIETY_ADMIN'
  | 'TREASURER'
  | 'ACCOUNTANT'
  | 'COMMITTEE_MEMBER'
  | 'SECURITY'
  | 'RESIDENT'
  | 'AUDITOR';

/**
 * Permission keys are opaque strings minted by the server (e.g.
 * "billing:read", "invoice:create"). The client treats them as an allow-list
 * and must not infer new permissions from roles on its own.
 */
export type Permission = string;

/** The authenticated society/tenant the session is scoped to, if any. */
export interface SessionSociety {
  readonly id: string;
  readonly name: string;
}

export interface SessionUser {
  readonly id: string;
  readonly displayName: string;
  readonly email?: string;
}

/** Shape returned by the session endpoint for an authenticated actor. */
export interface Session {
  readonly user: SessionUser;
  readonly roles: ReadonlyArray<Role>;
  readonly permissions: ReadonlyArray<Permission>;
  /** The tenant the session operates within; absent for platform-scope actors. */
  readonly society?: SessionSociety;
}
