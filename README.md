# Leave Space — Backend

Spring Boot 3.4 (Java 21), Spring Security, JWT. Schema managed by Flyway.

## Local development

```bash
cp .env.example .env         # then edit JWT_SECRET
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Flyway applies the schema and seed data automatically on first boot.

Generate a JWT secret:

```bash
openssl rand -base64 48
```

The app **refuses to start** if `JWT_SECRET` is missing or shorter than 32 bytes, so a weak or absent key fails immediately with a clear message instead of surfacing later as confusing signature errors.

## Databases

Local development uses MySQL. Railway deployment uses PostgreSQL. Both are supported: Flyway's
`locations: classpath:db/migration/{vendor}` resolves to `mysql` or `postgresql` at runtime, so each
database runs its own dialect-specific migrations. Hibernate never alters the schema.

## Environment variables

### Required

| Variable | Description |
|---|---|
| `JWT_SECRET` | HS256 signing key, ≥ 32 bytes. Generate with `openssl rand -base64 48`. |
| `SPRING_PROFILES_ACTIVE` | Set to `railway` in production. Loads `application-railway.yml`, which assembles the JDBC URL from Railway's `PG*` variables and switches the driver to PostgreSQL. Without it the app falls back to the MySQL default. |

### Database

| Variable | Description |
|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | Local MySQL connection. Defaults suit local development. |
| `PGHOST` / `PGPORT` / `PGDATABASE` / `PGUSER` / `PGPASSWORD` | Railway's PostgreSQL plugin injects these automatically. No need to set them by hand. |

### Optional

| Variable | Description | Default |
|---|---|---|
| `JWT_TTL_HOURS` | Token lifetime in hours. | `12` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins. Only needed for direct cross-origin calls — the Next.js route handlers proxy server-side, so this can stay at the default. | `http://localhost:3000` |
| `SPRING_DATASOURCE_URL` | Point at a PostgreSQL server manually instead of using the `PG*` variables. | — |

## Deployment (Railway)

Railway deploys from the repository root, so **do not set a Root Directory**.

`railway.json` uses the multi-stage `Dockerfile` (Maven build, JRE-only runtime image) and starts
with `java -jar app.jar`. The healthcheck path is `/actuator/health`.

Setup:
1. Add a PostgreSQL plugin — it injects the `PG*` variables into this service automatically
2. Set `SPRING_PROFILES_ACTIVE=railway`
3. Set `JWT_SECRET` to the output of `openssl rand -base64 48`
4. Generate a domain and verify `https://<domain>/actuator/health` reports `UP`

## Demo accounts

Seeded by the Flyway migrations. Passwords are BCrypt-hashed; these are the plaintext values.

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@test.com` | `admin123` |