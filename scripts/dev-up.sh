#!/usr/bin/env bash
# Levanta el backend en dev local. Si la red externa
# `infra_keycloak_proxy_local` no existe, levanta primero el stack de
# infra-keycloak (path en INFRA_KEYCLOAK_PATH del .env).
#
# Uso: ./scripts/dev-up.sh [args extra para docker compose up]
#   ./scripts/dev-up.sh                  # up -d --build
#   ./scripts/dev-up.sh --no-cache       # forzar rebuild

set -euo pipefail

KEYCLOAK_NETWORK="infra_keycloak_proxy_local"

cd "$(dirname "$0")/.."

# Cargar .env si existe (para INFRA_KEYCLOAK_PATH). `set -a` exporta cada
# variable seteada hasta el `set +a`, así no tenemos que listarlas a mano.
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

INFRA_KEYCLOAK_PATH="${INFRA_KEYCLOAK_PATH:-../infra-keycloak}"

if ! docker network inspect "$KEYCLOAK_NETWORK" >/dev/null 2>&1; then
  echo "Red docker '$KEYCLOAK_NETWORK' no encontrada — levantando infra-keycloak..."

  if [[ ! -d "$INFRA_KEYCLOAK_PATH" ]]; then
    cat <<EOF >&2
ERROR: INFRA_KEYCLOAK_PATH apunta a "$INFRA_KEYCLOAK_PATH" pero el directorio
no existe. Cloná https://github.com/cartones-app/infra-keycloak y ajustá
INFRA_KEYCLOAK_PATH en .env si lo tenés en otra ruta.
EOF
    exit 1
  fi

  if [[ ! -f "$INFRA_KEYCLOAK_PATH/docker-compose.local.yml" ]]; then
    echo "ERROR: '$INFRA_KEYCLOAK_PATH/docker-compose.local.yml' no existe." >&2
    echo "¿El path apunta al repo correcto?" >&2
    exit 1
  fi

  (
    cd "$INFRA_KEYCLOAK_PATH"
    docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
  )

  # Verificar que la red quedó creada — sino seguir adelante no tiene sentido.
  if ! docker network inspect "$KEYCLOAK_NETWORK" >/dev/null 2>&1; then
    echo "ERROR: infra-keycloak levantó pero la red '$KEYCLOAK_NETWORK' sigue sin existir." >&2
    echo "Revisar docker-compose.local.yml de ese repo." >&2
    exit 1
  fi
  echo "infra-keycloak arriba. Continuando con backend..."
fi

exec docker compose up -d --build "$@"
