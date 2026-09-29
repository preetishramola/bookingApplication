#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 24.04 EC2 instance. Run on the server as the `ubuntu` user:
#   curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/deploy/setup-server.sh | bash
# or copy it over with scp and run: bash setup-server.sh
set -euo pipefail

echo "==> Installing Docker Engine + Compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

echo "==> Adding 2 GB swap (a 2 GB instance runs Java + Postgres; swap prevents OOM kills during startup)"
if ! sudo swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf >/dev/null
  sudo sysctl -p /etc/sysctl.d/99-swappiness.conf >/dev/null
fi

echo "==> Enabling automatic security updates"
sudo apt-get update -y -qq
sudo apt-get install -y -qq unattended-upgrades >/dev/null

echo "==> Creating ~/hotelbooking (the deploy workflow copies compose files here)"
mkdir -p ~/hotelbooking/deploy

echo
echo "Done. Log out and back in (so the docker group applies), then create ~/hotelbooking/.env"
echo "from deploy/.env.example as described in the runbook."
