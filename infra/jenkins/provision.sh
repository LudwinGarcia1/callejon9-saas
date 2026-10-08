#!/usr/bin/env bash
# Provisions the RestoHub Jenkins VM. cloud-init runs it on every new VM (CAL-86).
# Inputs come from /etc/restohub/ci.env, written by Terraform through cloud-init.
set -euo pipefail

JENKINS_DIR=/opt/restohub/jenkins
DATA_MOUNT=/srv/jenkins
CONTROLLER_UID=1000 # uid of the jenkins user inside jenkins/jenkins images
APT_GET=(apt-get -y -q -o DPkg::Lock::Timeout=600)

log() { echo "[provision] $*"; }

find_data_disk() {
  local candidate
  for candidate in /dev/disk/azure/scsi1/lun0 /dev/disk/azure/data/by-lun/0; do
    if [[ -e "$candidate" ]]; then
      readlink -f "$candidate"
      return 0
    fi
  done
  return 1
}

# Terraform attaches the data disk after the VM exists, so it may appear after boot.
mount_data_disk() {
  local device="" attempt
  for attempt in $(seq 1 60); do
    device=$(find_data_disk) && break
    log "waiting for the JENKINS_HOME data disk ($attempt/60)"
    sleep 10
  done
  [[ -n "$device" ]] || { log "data disk not found"; exit 1; }

  # Never format a disk that already has a filesystem: it holds JENKINS_HOME.
  if [[ -z "$(blkid -o value -s TYPE "$device" || true)" ]]; then
    log "formatting empty data disk $device"
    mkfs.ext4 -q -L jenkins-data "$device"
  fi

  local uuid
  uuid=$(blkid -o value -s UUID "$device")
  mkdir -p "$DATA_MOUNT"
  grep -q "$uuid" /etc/fstab || echo "UUID=$uuid $DATA_MOUNT ext4 defaults,nofail 0 2" >> /etc/fstab
  mountpoint -q "$DATA_MOUNT" || mount "$DATA_MOUNT"
}

# Tailscale keeps its node identity on the data disk, so a recreated VM rejoins as the same node.
persist_tailscale_state() {
  mkdir -p "$DATA_MOUNT/tailscale" /var/lib/tailscale
  grep -q " /var/lib/tailscale " /etc/fstab ||
    echo "$DATA_MOUNT/tailscale /var/lib/tailscale none bind,nofail,x-systemd.requires-mounts-for=$DATA_MOUNT 0 0" >> /etc/fstab
  mountpoint -q /var/lib/tailscale || mount /var/lib/tailscale
}

add_apt_repository() {
  local name=$1 key_url=$2 source_line=$3 key_file
  key_file=$(mktemp)
  curl -fsSL "$key_url" -o "$key_file"
  if grep -q -- "-----BEGIN PGP" "$key_file"; then
    gpg --dearmor --yes -o "/usr/share/keyrings/$name.gpg" "$key_file"
  else
    install -m 0644 "$key_file" "/usr/share/keyrings/$name.gpg"
  fi
  rm -f "$key_file"
  echo "deb [signed-by=/usr/share/keyrings/$name.gpg] $source_line" > "/etc/apt/sources.list.d/$name.list"
}

install_packages() {
  local codename
  # shellcheck source=/dev/null
  codename=$(. /etc/os-release && echo "$VERSION_CODENAME")
  "${APT_GET[@]}" update
  "${APT_GET[@]}" install ca-certificates curl gnupg jq git

  add_apt_repository docker https://download.docker.com/linux/ubuntu/gpg \
    "https://download.docker.com/linux/ubuntu $codename stable"
  add_apt_repository adoptium https://packages.adoptium.net/artifactory/api/gpg/key/public \
    "https://packages.adoptium.net/artifactory/deb $codename main"
  add_apt_repository nodesource https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
    "https://deb.nodesource.com/node_24.x nodistro main"
  add_apt_repository tailscale "https://pkgs.tailscale.com/stable/ubuntu/$codename.noarmor.gpg" \
    "https://pkgs.tailscale.com/stable/ubuntu $codename main"

  "${APT_GET[@]}" update
  "${APT_GET[@]}" install docker-ce docker-ce-cli containerd.io docker-buildx-plugin \
    temurin-21-jdk nodejs tailscale

  # pnpm comes from corepack, so the version is the one pinned in frontend/package.json.
  corepack enable pnpm
}

# Reads a Key Vault secret with the VM managed identity; retries while the role assignment propagates.
key_vault_secret() {
  local name=$1 token attempt
  for attempt in $(seq 1 30); do
    token=$(curl -fsS -H Metadata:true \
      "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fvault.azure.net" |
      jq -r .access_token) &&
      curl -fsS -H "Authorization: Bearer $token" "${KEY_VAULT_URI}secrets/$name?api-version=7.4" | jq -re .value &&
      return 0
    log "secret $name not readable yet ($attempt/30)" >&2
    sleep 10
  done
  return 1
}

prepare_jenkins_dirs() {
  id jenkins-agent > /dev/null 2>&1 ||
    useradd --system --create-home --home-dir /var/lib/jenkins-agent --shell /usr/sbin/nologin jenkins-agent
  usermod -aG docker jenkins-agent

  install -d -o "$CONTROLLER_UID" -g "$CONTROLLER_UID" -m 0750 "$DATA_MOUNT/home"
  install -d -o "$CONTROLLER_UID" -g jenkins-agent -m 0750 "$DATA_MOUNT/agent-secret"
}

start_jenkins() {
  # JCasC silently turns a missing secret into an empty password; refuse to start without it.
  key_vault_secret jenkins-admin-password > /dev/null

  install -d -m 0755 /etc/restohub
  cat > /etc/restohub/jenkins.env <<EOF
JENKINS_LOCATION_URL=$JENKINS_LOCATION_URL
AZURE_KEYVAULT_URL=$KEY_VAULT_URI
AZURE_KEYVAULT_UAMI_ENABLED=true
EOF

  docker build -q -t restohub/jenkins-controller:local "$JENKINS_DIR"
  install -m 0644 "$JENKINS_DIR/systemd/jenkins-controller.service" /etc/systemd/system/
  install -m 0644 "$JENKINS_DIR/systemd/jenkins-agent.service" /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable --now jenkins-controller.service jenkins-agent.service
}

join_tailnet() {
  if tailscale status --json | jq -e '.BackendState == "Running"' > /dev/null; then
    log "tailscale node already authenticated"
  else
    local auth_key
    auth_key=$(key_vault_secret tailscale-auth-key)
    tailscale up --auth-key="$auth_key" --hostname="$TAILSCALE_HOSTNAME" --ssh
  fi
  # Jenkins is only published on localhost; tailscale serve exposes it to the tailnet over HTTPS.
  tailscale serve --bg 8080
}

main() {
  # shellcheck source=/dev/null
  source /etc/restohub/ci.env
  : "${KEY_VAULT_URI:?}" "${TAILSCALE_HOSTNAME:?}" "${JENKINS_LOCATION_URL:?}"

  mount_data_disk
  persist_tailscale_state
  install_packages
  prepare_jenkins_dirs
  start_jenkins
  join_tailnet
  log "done"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
