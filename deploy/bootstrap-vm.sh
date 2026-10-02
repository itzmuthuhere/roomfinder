#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 22.04/24.04 VM. Run as root: sudo ./bootstrap-vm.sh
# Installs Docker, opens only ports 22/80/443, creates the 'deploy' user the CI workflow logs in as,
# and lays out /srv/roomfinder. It does NOT write secrets or touch sshd - see the printed next steps.
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "run as root (sudo)"; exit 1; }
SRC="$(cd "$(dirname "$0")" && pwd)"

apt-get update
apt-get install -y ca-certificates curl ufw unattended-upgrades

if ! command -v docker > /dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
systemctl enable --now docker

# Default-deny inbound, then only SSH and web. Postgres is never published, so it isn't listed.
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

# Membership in the docker group is effectively root on this machine - acceptable for a
# single-purpose VM, but it's why this user gets no password and logs in with a key only.
id deploy > /dev/null 2>&1 || adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

# App layout. Media is written by the api container (uid 10001, see api/Dockerfile).
install -d -m 755 -o deploy -g deploy /srv/roomfinder
install -d -m 750 -o 10001 -g 10001 /srv/roomfinder/media
install -m 755 -o deploy -g deploy "$SRC"/*.sh /srv/roomfinder/
install -m 644 -o deploy -g deploy "$SRC"/docker-compose.prod.yml /srv/roomfinder/

# Nightly backup + monthly restore test.
install -m 644 "$SRC"/systemd/*.service "$SRC"/systemd/*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable roomfinder-backup.timer roomfinder-restore-test.timer

cat <<'EOF'

Bootstrap done. Still to do by hand (none of this is automated on purpose):

  1. Add the CI deploy key's PUBLIC half to /home/deploy/.ssh/authorized_keys
  2. Create /srv/roomfinder/.env (owner deploy, chmod 600) - key names are in the repo's
     .env.example under "production server only". Generate AUTH_JWT_SECRET with:  openssl rand -base64 48
  3. Point DNS (an A record for your DOMAIN) at this VM's public IP.
  4. Start the backup timers once you've filled in the BACKUP_R2_* keys:
        systemctl start roomfinder-backup.timer roomfinder-restore-test.timer
  5. Consider disabling SSH password login (PasswordAuthentication no in /etc/ssh/sshd_config.d/)
     once you've confirmed key login works - left to you so a typo can't lock you out of a new VM.
EOF
