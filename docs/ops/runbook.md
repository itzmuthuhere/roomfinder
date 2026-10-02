# Runbook

| Symptom | First check | Fix |
| --- | --- | --- |
| Two seekers got the same bed | `bed_state_log` for that bed | Should be impossible; the conditional update or a transaction boundary is broken. Write the failing test first |
| Holds never expire | Scheduler logs, job table | Restart app; confirm the expiry job runs every minute |
| WhatsApp messages not delivered | Meta console template status, token expiry | Re-approve template or rotate token |
| Owner says beds show wrong | `bed_state_log`, last owner activity | Call the owner; if repeated, drop them from Instant Book |
| Search returns nothing in an area | Listings live count, PostGIS index | Check `status='LIVE'` and the radius used |
| A deploy failed | The *deploy* job log: `deploy.sh` prints the API's last 80 log lines | The previous release was restored automatically. Fix forward and merge; if it rolled back and the site is down anyway, `ssh deploy@vm`, `cd /srv/roomfinder`, `docker compose -f docker-compose.prod.yml --env-file .env --env-file .deploy logs api` |
| Need to undo a release | — | Actions → *deploy* → Run workflow with an older sha (see [Deployment](deployment.md#rollback)) |
| Site up but HTTPS fails | `docker compose ... logs web` (Caddy) | DNS `A` record wrong, or ports 80/443 closed — Caddy can't get a certificate without both |
| Uploads fail with 413 | Caddy's 60MB limit, then Spring's | Both are set to 60MB; a bigger file was sent |
| Backup didn't run | `journalctl -u roomfinder-backup`, `systemctl list-timers` | Missing `BACKUP_R2_*` in `.env`, or R2 token expired |

Backups: nightly `pg_dump` plus media to R2 (dumps retained 30 days), see [Deployment](deployment.md#backups).
Test a restore monthly — an untested backup is not a backup. `restore-test.sh` does it automatically
on the 1st; check `journalctl -u roomfinder-restore-test`.

See also: [Local setup and environment errors](local-setup.md).
