# Project-Scoped Rules for Payment Checker

## 1. Context Loading on Startup
- At the start of a new chat session or when requested, always read the master blueprint file [payment_checker_blueprint.md](file:///d:/payment_checker_native_android/payment_checker_blueprint.md) to understand the architecture, database schema, APIs, and screen structures.
- If requested to load previous conversation history, read the consolidated file [active_chat_history.md](file:///d:/payment_checker_native_android/active_chat_history.md).

## 2. Blueprint Maintenance (Living Document)
- Whenever you make changes to the codebase (such as adding, modifying, or deleting screens, UI elements like buttons, API routes, database fields, or configuration keys), you MUST update the corresponding sections in [payment_checker_blueprint.md](file:///d:/payment_checker_native_android/payment_checker_blueprint.md) to reflect the exact new state.
- **Overwriting/Updating Pattern**:
  - If a new button, function, or API is added, append its details to the respective section in the blueprint.
  - If an existing element (e.g., button, column, route) is updated, modify its description to match the new behavior.
  - If an element is removed, delete its description entirely from the blueprint.
- Do not write summary changelogs at the end of the blueprint; keep it as a clean, up-to-date technical design blueprint representing the *current* state of the repository.

## 3. Post-Implementation Pipeline (Git Push & APK Build)
- After successfully implementing and verifying code changes, always stage all changes, commit them with a descriptive message, and push to GitHub.
- Run `build-apk.bat` in the workspace root to rebuild the APK so the client can download and test the latest compiled binary.

