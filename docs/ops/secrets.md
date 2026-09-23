# Secrets and API keys

**No key, token, password or connection string is ever written in this repository or this site.** Not in docs, not in tests, not in an example file with a "real" value.

What we document is the *name*, the *owner*, where it is stored, and how to rotate it.

| Key name | Used for | Stored in | Rotation |
| --- | --- | --- | --- |
| DB_URL / DB_USER / DB_PASSWORD | Postgres | Server env file, GitHub Secrets for CI | On any suspicion, immediately |
| JWT_SIGNING_KEY | Auth tokens | Server env | Every 6 months; rotating invalidates sessions |
| WHATSAPP_TOKEN, WHATSAPP_PHONE_ID | WhatsApp Cloud API | Server env | Meta console, quarterly |
| SMS_API_KEY | OTP delivery | Server env | Provider console |
| R2_ACCESS_KEY / R2_SECRET | Media storage | Server env | Cloudflare dashboard |
| FCM_SERVICE_ACCOUNT | Push notifications | Server env (file) | Firebase console |
| SENTRY_DSN | Error reporting | Server env | Low risk |

Rules:

1. `.env.example` lists key **names** with empty values, and is the only env file in git.
2. `.gitignore` covers `.env`, `*.pem`, `*.p12`, `*-service-account.json`.
3. CI reads from GitHub Actions secrets. Never echo a secret in a workflow log.
4. If a key is ever committed, rotate it first, then rewrite history. Assume it is compromised.
5. Customer phone numbers and tenant documents never appear in docs, screenshots or test fixtures.
