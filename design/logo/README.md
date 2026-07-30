# Brand Logo Usage & Asset Maintenance Guide

This directory contains the master source branding files, configurations, design tokens, and changelogs for the PayCheck identity system.

## Ownership & Approvals
- **Brand Owner**: Product Team
- **Figma Design System Link**: `https://www.figma.com/file/paychek-brand-system`
- **Last Approved By**: Super Admin
- **Approval Date**: 2026-07-30

## Brand Asset Specifications

### Safe Area
- **Logo Boundary Box**: 100%
- **Visible Content Area**: 64% (Aligned with Android 12+ Adaptive Icon safe zone guidelines)
- **Clear Space Margins**: 18% padding margin on all sides of the logo box to maintain spacing integrity.

### Minimum Sizes
- **Web**: 24px
- **Android UI**: 48dp
- **Print**: 12mm

---

## Brand Update Workflow

To update or alter any branding assets, follow the strict governance workflow diagrammed below:

```text
Edit SVG inside Design Tools (Figma/Illustrator)
                     ↓
      Optimize SVG via SVGO/SVGOMG tool
                     ↓
   Replace design/logo/logo-master.svg
                     ↓
  Run Node command: node tools/generate_logo_assets.js
                     ↓
Verify asset checks (git diff, checksum, size validation)
                     ↓
       Commit changes to Source Control
```

❗ **CRITICAL RULE**: Never edit generated assets inside `backend/public/` or Android `mipmap-*` folders manually. Always run the automated generation tools to ensure cross-platform consistency.
