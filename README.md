# Cartones App — Backend API

Sistema de gestión para vendedores y cartones de bingo. API REST en Spring Boot que procesa archivos Excel, simula la distribución de cartones (Senete y Telebingo) y genera reportes PDF comprimidos en ZIP.

**Autor:** Elías González

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 + Spring Boot 3.3.1 |
| Base de datos | PostgreSQL 16 |
| Autenticación | Keycloak 26 (OAuth2 / JWT) |
| Excel | Apache POI 5.2.5 |
| PDFs | OpenPDF 1.3.30 |
| Rate limiting | Bucket4j 8.10 (in-memory, uploads) |
| Migraciones | Flyway |
| Contenedores | Docker / Docker Compose |
| Staging | Railway (backend + Postgres + Keycloak dedicados) |
| Producción | VPS Hetzner ARM64 (Cloudflare Tunnel + nginx-proxy) |
| Frontend | Next.js 16 en Vercel (staging) / VPS (prod) — `cartones-app/cartones-app-web` |
| CI / Seguridad | GitHub Actions (self-hosted runner ARM64) + CodeQL (`security-extended`) |

---

## Modelo de ramas

| Rama | Ambiente | Host | Despliegue |
|------|----------|------|-----------|
| `master` | Producción | VPS (`cartones.eliasg.uk`) | Push → workflow `deploy-vps.yml` → self-hosted runner ARM64 |
| `develop` | Staging | Railway (`backend-staging-de76.up.railway.app`) | Push → CI workflows verdes → Railway redeploya (`Wait for CI` activado) |
| `next` | Integración | — | Solo corre CI + CodeQL; sprints / features mergean acá primero |
| `feat/*`, `chore/*`, `fix/*` | Trabajo | — | mergean a `next` vía PR |

`master` lleva tags futuros (`v1.x.x`); `develop` y `next` siempre `-SNAPSHOT`.

Branch protection activa en `master` (requiere PR + linear history + conversation resolution) y `develop` (requiere PR).

---

## Arranque rápido

### Opción A — Sin Keycloak (más simple, solo para desarrollo)

Requiere una instancia de PostgreSQL local o en Docker corriendo aparte.

```bash
# Instalar dependencias y compilar
mvn clean package -DskipTests

# Arrancar con perfil local (seguridad deshabilitada)
mvn spring-boot:run -Dspring.profiles.active=local,dev
```

El perfil `local` deshabilita toda autenticación. Útil para probar endpoints rápidamente sin levantar Keycloak.

---

### Opción B — Con Docker Compose (PostgreSQL + Keycloak + Backend)

**1. Copiar y configurar variables de entorno:**

```bash
cp .env.example .env
# Editar .env con tus valores
```

**2. Configurar secretos de base de datos:**

```bash
cp secrets_store/db_user.txt.example secrets_store/db_user.txt
cp secrets_store/db_password.txt.example secrets_store/db_password.txt
# Editar ambos archivos con credenciales seguras
```

**3. Levantar todos los servicios:**

```bash
docker compose up -d --build
```

Esto levanta tres contenedores:

- `postgres_cartones_db` — PostgreSQL en el puerto `PORT_DB`
- `cartones_keycloak` — Keycloak en el puerto `PORT_KEYCLOAK` (default 8080)
- `cartones_backend` — Spring Boot en el puerto `PORT_BACKEND`

Keycloak importa automáticamente el realm `cartones` desde `keycloak/realm-cartones.json`.

**Ver logs:**

```bash
docker compose logs -f backend
docker compose logs -f keycloak
```

**Detener:**

```bash
docker compose down
```

---

### Opción C — Solo Maven (con Keycloak corriendo por separado)

```bash
# Con perfil dev (requiere Keycloak en localhost:8080 y PostgreSQL)
mvn spring-boot:run -Dspring.profiles.active=dev
```

---

## Autenticación

El backend funciona como **OAuth2 Resource Server**. Valida tokens JWT emitidos por Keycloak.

### Roles

| Rol | Acceso |
|-----|--------|
| `ADMIN` | Todo: operaciones de negocio + endpoints `/api/admin/**` + `/actuator/**` |
| `DISTRIBUIDOR` | Solo operaciones de negocio (`/api/**` excepto `/api/admin/**`) |

### Obtener un token (desarrollo)

```bash
curl -s -X POST http://localhost:8080/realms/cartones/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=frontend" \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=admin123" \
  | jq -r .access_token
```

Usuarios demo del realm (contraseñas temporales — cambiar al primer login):

- `admin` / `admin123` → rol `ADMIN`
- `distribuidor` / `distribuidor123` → rol `DISTRIBUIDOR`

### Usar el token

```bash
curl -H "Authorization: Bearer <TOKEN>" http://localhost:9001/api/vendedores/<procesoId>
```

---

## Variables de entorno

### `.env` (Docker Compose — desarrollo)

| Variable | Ejemplo | Descripción |
|----------|---------|-------------|
| `APP_PROFILE` | `dev` | Perfil de Spring Boot |
| `DB_DDL_AUTO` | `update` | DDL de Hibernate (`update` dev / `validate` prod) |
| `DB_SHOW_SQL` | `true` | Log de SQL |
| `LOG_LEVEL` | `DEBUG` | Nivel de log de la app |
| `OPEN_IN_VIEW` | `true` | Open-in-view de JPA |
| `FRONTEND_URL` | `http://localhost:3000` | Origen CORS del frontend |
| `POSTGRES_DB` | `cartones` | Nombre de la base de datos |
| `PORT_DB` | `5432` | Puerto PostgreSQL expuesto en host |
| `PORT_BACKEND` | `9001` | Puerto del backend expuesto en host |
| `PORT_KEYCLOAK` | `8080` | Puerto de Keycloak expuesto en host |
| `KEYCLOAK_ADMIN_USER` | `admin` | Usuario admin de Keycloak |
| `KEYCLOAK_ADMIN_PASSWORD` | `*****` | Contraseña admin de Keycloak |
| `APP_UPLOADS_RATE_LIMIT_RPM` | `10` | Rate limit por usuario en endpoints de upload |

### Railway (staging)

Proyecto: `cartones-staging`. Tres servicios:

- `postgres` — `ghcr.io/railwayapp-templates/postgres-ssl:16`, volumen persistente.
- `keycloak` — `quay.io/keycloak/keycloak:26.1` modo `start-dev`, backend Postgres compartido. URL pública: `https://keycloak-staging-085a.up.railway.app`.
- `backend` — linkeado al repo en rama `develop`, perfil `prod`. URL pública: `https://backend-staging-de76.up.railway.app`.

Variables del servicio `backend` (referencias a otros servicios via `${{servicio.VAR}}`):

| Variable | Valor |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SERVER_PORT` | `8080` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/${{postgres.POSTGRES_DB}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{postgres.POSTGRES_USER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{postgres.POSTGRES_PASSWORD}}` |
| `APP_CORS_ORIGINS` | `https://cartones-app-web.vercel.app` |
| `APP_DDL_AUTO` | `update` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75.0` |
| `KEYCLOAK_ISSUER_URI` | `https://keycloak-staging-085a.up.railway.app/realms/cartones` (público — coincide con claim `iss` del JWT) |
| `KEYCLOAK_JWK_SET_URI` | `http://${{keycloak.RAILWAY_PRIVATE_DOMAIN}}:8080/realms/cartones/protocol/openid-connect/certs` (red privada Railway, sin pasar por edge) |

### VPS (producción, configurado vía GitHub Variables)

El workflow `.github/workflows/deploy-vps.yml` arma el `.env` del compose en cada deploy combinando **Variables del repo** + **secretos de DB en `/srv/cartones-secrets/`** del host.

GitHub `Settings → Secrets and variables → Actions` (Variables, no sensibles):

| Variable | Ejemplo | Descripción |
|----------|---------|-------------|
| `FRONTEND_URL` | `https://rgq-cartones.eliasg.uk,https://rgq-web.vercel.app` | Lista de orígenes CORS (split por coma) |
| `POSTGRES_DB` | `cartones_prod` | Nombre de la base de datos |
| `HEALTH_URL` | `https://cartones.eliasg.uk/api/vendedores/test` | (Opcional) smoke test post-deploy |

Secretos de DB en el VPS: `/srv/cartones-secrets/db_user.txt` y `db_password.txt` (`chmod 600`, owner del runner).

Variables que el workflow inyecta automáticamente con valores fijos para prod:

| Variable | Valor en prod |
|----------|--------------|
| `APP_PROFILE` | `prod` |
| `DB_DDL_AUTO` | `update` (master, código sin Flyway) o `validate` (develop, con Flyway) |
| `LOG_LEVEL` | `INFO` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Leídas de `/srv/cartones-secrets/*` |
| `KEYCLOAK_ISSUER_URI` / `_JWK_SET_URI` | (Cuando se promueva develop) URL pública del Keycloak en VPS |

---

## Endpoints

Todos con prefijo `/api`. Requieren token JWT excepto donde se indica.

### Vendedores

| Método | Ruta | Descripción | Rol mínimo |
|--------|------|-------------|-----------|
| `POST` | `/api/vendedores/carga` | Carga Excel de vendedores, crea `procesoId` | `DISTRIBUIDOR` |
| `GET` | `/api/vendedores/{procesoId}` | Lista vendedores válidos del proceso | `DISTRIBUIDOR` |

### Distribución (PDFs)

| Método | Ruta | Descripción | Rol mínimo |
|--------|------|-------------|-----------|
| `GET` | `/api/distribuciones` | Lista los procesos creados por el usuario actual (filtrado por `sub` del JWT) | `DISTRIBUIDOR` |
| `POST` | `/api/distribuciones/{procesoId}/simular` | Simula distribución de cartones | `DISTRIBUIDOR` |
| `GET` | `/api/distribuciones/{procesoId}/pdfs` | Descarga ZIP con PDFs (valida ownership: 404 si no es dueño) | `DISTRIBUIDOR` |

### Ruta (recorrido de cobro)

| Método | Ruta | Descripción | Rol mínimo |
|--------|------|-------------|-----------|
| `POST` | `/api/ruta/carga` | Carga Excel de ruta, crea sesión | `DISTRIBUIDOR` |
| `POST` | `/api/ruta/{sesionId}/registros` | Filtra registros por fechas | `DISTRIBUIDOR` |
| `POST` | `/api/ruta/{sesionId}/exportar` | Exporta Excel con valores completados | `DISTRIBUIDOR` |

### Admin — Distribuciones

| Método | Ruta | Descripción | Rol |
|--------|------|-------------|-----|
| `GET` | `/api/admin/distribuciones` | Lista todos los procesos del sistema (vista global) | `ADMIN` |
| `GET` | `/api/admin/distribuciones/{procesoId}/pdfs` | Descarga ZIP de cualquier proceso (sin ownership) | `ADMIN` |

### Admin — Sesiones de Ruta

| Método | Ruta | Descripción | Rol |
|--------|------|-------------|-----|
| `GET` | `/api/admin/ruta/sesiones` | Lista todas las sesiones | `ADMIN` |
| `GET` | `/api/admin/ruta/sesiones/{sesionId}` | Detalle de una sesión | `ADMIN` |
| `GET` | `/api/admin/ruta/sesiones/{sesionId}/registros` | Registros de una sesión | `ADMIN` |
| `DELETE` | `/api/admin/ruta/sesiones/{sesionId}` | Elimina una sesión | `ADMIN` |
| `DELETE` | `/api/admin/ruta/sesiones` | Elimina sesiones en lote | `ADMIN` |
| `DELETE` | `/api/admin/ruta/sesiones/{sesionId}/registros/{registroId}` | Elimina un registro | `ADMIN` |

### Admin — Exclusiones de Ruta

| Método | Ruta | Descripción | Rol |
|--------|------|-------------|-----|
| `GET` | `/api/admin/ruta/exclusiones` | Lista exclusiones de vendedores | `ADMIN` |
| `POST` | `/api/admin/ruta/exclusiones` | Crea exclusión | `ADMIN` |
| `PUT` | `/api/admin/ruta/exclusiones/{id}` | Actualiza exclusión | `ADMIN` |
| `DELETE` | `/api/admin/ruta/exclusiones/{id}` | Elimina exclusión | `ADMIN` |

### Actuator (observabilidad)

| Ruta | Acceso |
|------|--------|
| `GET /actuator/health` | Público |
| `GET /actuator/metrics` | `ADMIN` |
| `GET /actuator/prometheus` | `ADMIN` |

---

## Build y tests

```bash
# Compilar sin tests
mvn clean package -DskipTests

# Ejecutar todos los tests
mvn test

# Ejecutar una clase de test específica
mvn test -Dtest=NombreDeClase
```

---

## Despliegue (CI/CD)

### Producción — VPS (`master`)

Push a `master` dispara `.github/workflows/deploy-vps.yml`:

1. Self-hosted runner en el VPS (`cartones-runner`, labels `[self-hosted, linux, arm64]`).
2. Checkout, copia secretos de DB desde `/srv/cartones-secrets/`.
3. Genera `.env` desde GitHub Variables.
4. `docker compose up -d --build` con BuildKit cache `--mount=type=cache,target=/root/.m2` (preserva `~/.m2` entre builds).
5. Espera health del contenedor.
6. Smoke test público vía `HEALTH_URL` con reintentos (cubre toda la cadena Cloudflare → tunnel → nginx → backend).
7. Prune de imágenes viejas.

Topología en VPS: `Internet → Cloudflare (cartones.eliasg.uk) → cloudflared tunnel → nginx-proxy → cartones_backend:9001`.
El backend joinea la network externa `proxy` (creada por `~/infra-claude/vps`) y no expone puertos al host.

### Staging — Railway (`develop`)

1. Push a `develop` → GitHub Actions corre `ci.yml` (Spotless + build) y `codeql.yml`.
2. Railway escucha el check-suite de GitHub (`Wait for CI = true`).
3. Cuando los checks pasan, Railway construye con el Dockerfile y despliega.
4. Healthcheck: `/actuator/health` (timeout 300s, restart on failure x3).

El Dockerfile usa build multi-stage (Maven → Alpine JRE) con `-DskipTests` y `-XX:MaxRAMPercentage=60.0` (override en Railway: `75.0` via `JAVA_TOOL_OPTIONS`).

### Integración continua (PRs y push a `next`/`develop`)

- `.github/workflows/ci.yml`: corre `mvn spotless:check + verify` en `ubuntu-latest` GitHub-hosted (no carga el self-hosted).
- `.github/workflows/codeql.yml`: análisis estático Java con queries `security-extended`. PRs + pushes + cron lunes.

Spotless con `ratchetFrom origin/next` solo verifica archivos cambiados desde la rama de integración (no toca código existente).
Para corregir formato local: `mvn spotless:apply`.
