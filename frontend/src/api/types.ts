// Friendly aliases for the generated OpenAPI types. Regenerate schema.gen.ts with `npm run api:types`
// (backend running) whenever a controller or DTO changes; never edit schema.gen.ts by hand.
// Note: optional properties (`x?: T`) come from @Nullable fields; the JSON may contain `null` for them,
// so test them with `x != null`, not `x !== undefined`.
import type { components } from './schema.gen.ts'

type Schemas = components['schemas']

export type AppInfo = Schemas['AppInfo']

// accounts (FR-001..FR-003)
export type Me = Schemas['MeResponse']
export type SessionResponse = Schemas['SessionResponse']
export type Role = Me['role']
export type LoginRequest = Schemas['LoginRequest']
export type ChangePasswordRequest = Schemas['ChangePasswordRequest']
export type User = Schemas['UserResponse']
export type UserPage = Schemas['UserPage']
export type CreateUserRequest = Schemas['CreateUserRequest']
export type ResetPasswordRequest = Schemas['ResetPasswordRequest']

// import (FR-004..FR-008)
export type ImportBatch = Schemas['ImportBatchResponse']
export type ImportBatchPage = Schemas['ImportBatchPage']
export type StartImportRequest = Schemas['StartImportRequest']
export type CheckRequest = Schemas['CheckRequest']
export type CheckResponse = Schemas['CheckResponse']
export type UploadResponse = Schemas['UploadResponse']

// library (FR-009)
export type PhotoSummary = Schemas['PhotoSummary']
export type PhotoPage = Schemas['PhotoPage']
export type PhotoDetails = Schemas['PhotoDetails']
export type ProcessingState = PhotoSummary['processingState']
