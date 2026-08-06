# Sync Cursor rules -> Claude rules
# Run from repo root: python sync-claude-rules.py

from pathlib import Path

root = Path(__file__).resolve().parent
src = root / ".cursor" / "rules"
dst = root / ".claude" / "rules"
dst.mkdir(parents=True, exist_ok=True)

def strip_frontmatter(text: str) -> str:
    if text.startswith("---"):
        parts = text.split("---", 2)
        if len(parts) >= 3:
            return parts[2].lstrip("\n")
    return text

for f in sorted(src.glob("*.mdc")):
    body = strip_frontmatter(f.read_text(encoding="utf-8"))
    out = dst / (f.stem + ".md")
    out.write_text(
        f"<!-- Synced from .cursor/rules/{f.name} -->\n\n" + body,
        encoding="utf-8",
    )
    print("synced", out.name)

cr = root / ".cursorrules"
if cr.exists():
    (dst / "00-cursorrules-deploy.md").write_text(
        "<!-- Synced from .cursorrules -->\n\n" + cr.read_text(encoding="utf-8"),
        encoding="utf-8",
    )
    print("synced 00-cursorrules-deploy.md")

print("done ->", dst)
