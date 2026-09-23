# Runbook

| Symptom | First check | Fix |
| --- | --- | --- |
| Two seekers got the same bed | `bed_state_log` for that bed | Should be impossible; the conditional update or a transaction boundary is broken. Write the failing test first |
| Holds never expire | Scheduler logs, job table | Restart app; confirm the expiry job runs every minute |
| WhatsApp messages not delivered | Meta console template status, token expiry | Re-approve template or rotate token |
| Owner says beds show wrong | `bed_state_log`, last owner activity | Call the owner; if repeated, drop them from Instant Book |
| Search returns nothing in an area | Listings live count, PostGIS index | Check `status='LIVE'` and the radius used |

Backups: nightly `pg_dump` to R2, retained 30 days. Test a restore monthly — an untested backup is not a backup.

See also: [Local setup and environment errors](local-setup.md).
