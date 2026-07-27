# Backend Folder Structure

```
backend/
├── app.js                 # Express app assembly
├── server bootstrap       # HTTP listen / workers start (as configured)
├── .env.example           # Documented env keys (no secrets)
├── prisma/
│   └── schema.prisma      # Data model
├── routes/                # HTTP route tables
├── controllers/           # Request handlers
├── services/              # Domain services, callbacks, cache, policy
├── payment/               # Payment flow engine & session utils
├── workers/               # Background workers (e.g. SMS)
├── db/                    # ensure-*.js additive schema helpers
├── official-website/      # Marketing/demo site server bits
├── public/                # Static assets
│   ├── js/checkout/       # Checkout SPA-like scripts
│   ├── css/
│   ├── test/
│   ├── docs/
│   ├── downloads/         # paycheck.apk (prod upload target)
│   └── uploads/           # logos, avatars, branding (gitignored patterns vary)
├── scripts/               # Ops / one-off scripts
└── docs/                  # Backend-specific notes
```

## Conventions

- **routes** register paths only; avoid fat logic.
- **controllers** parse/validate and call services.
- **services** own transactions and side effects.
- **payment/** stays cohesive — checkout regressions are high severity.
- Static checkout changes require browser verification of init → pay → callback path.

## Naming

- Files: `camelCase.js` matching existing style.
- Ensure scripts: `ensure-<feature>.js`.
- Prefer explicit exports; avoid silent global mutation.
