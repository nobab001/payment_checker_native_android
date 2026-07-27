# GitHub Workflow

## Branching

```
main              # production-ready
feature/<name>    # work in progress
fix/<name>        # hotfixes
```

## Agent / developer rules

- Do **not** stage/commit/push unless the user explicitly asks.  
- Prefer PRs for substantial changes.  
- Never force-push `main`.  
- Keep secrets out of commits (use `.gitignore` for `.env`, keystores, large uploads).

## Suggested PR checklist

- [ ] Docs updated if behavior/API/UI system changed  
- [ ] No secrets in diff  
- [ ] Android: debug APK built locally for UI changes  
- [ ] Backend: local server verified  
- [ ] Payment/SMS paths regression-considered  

## CI

If GitHub Actions are added later, they should run lint/tests and never deploy production without an explicit environment approval gate.

## Releases

Tag releases when publishing production APK + backend together (`vYYYY.MM.DD` or semver). Note APK file hash in release notes when possible.
