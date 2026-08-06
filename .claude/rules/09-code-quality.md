<!-- Synced from .cursor/rules/09-code-quality.mdc -->

# Code Quality

## Principles

- No duplicate logic — extract shared helpers/components/services.
- Small functions; one responsibility per module.
- Meaningful names; avoid abbreviations unless domain-standard (`otp`, `hmac`, `mfs`).
- Comments only for non-obvious intent (security, race conditions, payment edge cases).
- No magic numbers — use named constants / design tokens / config.
- Match existing folder structure; do not create parallel “v2” trees.

## Architecture

- Clean boundaries: UI → ViewModel → Repository → API (Android); Route → Controller → Service → DB (Backend).
- Prefer extending Prisma models / DTOs over ad-hoc maps scattered across files.
- When refactoring, keep behavior identical unless the task is a behavior change.

## Review checklist (AI self-check)

- [ ] Touches only required files
- [ ] Follows design system if UI
- [ ] Validation + auth preserved
- [ ] No secrets introduced
- [ ] Deploy follow-up applied
