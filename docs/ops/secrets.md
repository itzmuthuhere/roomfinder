# Secrets and API keys

**No key, token, password or connection string is ever written in this repository or this site.** Not in docs, not in tests, not in an example file with a "real" value.

What we document is the *name*, the *owner*, where it is stored, and how to rotate it.

| Key name | Used for | Stored in | Rotation |
| --- | --- | --- | --- |
| DB_PASSWORD (prod/staging) | Postgres | `/srv/roomfinder/.env` on the VM only | On any suspicion, immediately |
| AUTH_JWT_SECRET | Auth tokens | Server env | Every 6 months; rotating invalidates sessions |
| WHATSAPP_TOKEN, WHATSAPP_PHONE_ID | WhatsApp Cloud API | Server env | Meta console, quarterly |
| OTP_PROVIDER, MSG91_AUTH_KEY, MSG91_TEMPLATE_ID | OTP delivery | Server env | Provider console |
| R2_ACCESS_KEY / R2_SECRET | Media storage | Server env | Cloudflare dashboard |
| BACKUP_R2_ACCOUNT_ID / _ACCESS_KEY_ID / _SECRET_ACCESS_KEY / _BUCKET | Nightly backup upload | VM `.env` | Cloudflare dashboard; give it a token scoped to the backup bucket only |
| BACKUP_PING_URL | Optional "backup ran" heartbeat | VM `.env` | Low risk |
| DEPLOY_HOST / DEPLOY_USER / DEPLOY_SSH_KEY / DEPLOY_KNOWN_HOSTS | CI → VM deploy | GitHub Environment secrets (per environment) | Regenerate the key pair whenever someone with repo admin leaves |
| FCM_SERVICE_ACCOUNT | Push notifications | Server env (file) | Firebase console |
| SENTRY_DSN | Error reporting | Server env | Low risk |

Rules:

1. `.env.example` lists key **names** with empty values, and is the only env file in git. The
   application secrets (DB, JWT, SMS, backup keys) live only in `/srv/roomfinder/.env` on each VM;
   the deploy pipeline holds SSH access, not those secrets.
2. `.gitignore` covers `.env`, `*.pem`, `*.p12`, `*-service-account.json`.
3. CI reads from GitHub Actions secrets. Never echo a secret in a workflow log.
4. If a key is ever committed, rotate it first, then rewrite history. Assume it is compromised.
5. Customer phone numbers and tenant documents never appear in docs, screenshots or test fixtures.
