# GTMES Backend

REST API for **GTMES** — the GramTarang Garments Manufacturing Execution System. Serves the
Flutter mobile client with production tracking, workforce management, biometric attendance
integration and generated analytical reports.

| | |
|---|---|
| **Runtime** | Java 17 · Spring Boot 3.3.1 |
| **Packaging** | WAR (`smo.war`) deployed to an external Tomcat |
| **Database** | MySQL 8, schema owned by Flyway (25 migrations) |
| **Production** | `https://smobza.thegttech.com` |

---

## Architecture

Layered Spring MVC. Requests pass through an authorization interceptor before reaching a
controller; controllers delegate to services, which own all business logic and calculation;
repositories handle persistence through Spring Data JPA.

```
HTTP request
  │
  ├─ RoleAuthorizationInterceptor   validate JWT → resolve employee → check @RequiresActivity
  ├─ Controller                     request/response mapping only
  ├─ Service                        business rules, calculations, transactions
  ├─ Repository                     Spring Data JPA
  └─ MySQL
```

Two outbound integrations sit alongside this: the **HRMS client** (biometric attendance and
employee master data, read-only) and the **report pipeline** (aggregate real data → narrate →
render PDF).

Every derived figure — efficiency, standard minutes, wages, classification — is computed
server-side. The client never calculates them, so two devices cannot disagree about a number.

### Package layout

```
src/main/java/com/antigravity/mes/
├── controller/   15 REST controllers
├── service/      12 business services
├── repository/   Spring Data JPA repositories
├── model/        JPA entities
├── dto/          request/response records
├── security/     JWT issue/validate, field encryption, password hashing
├── web/          activity catalogue, @RequiresActivity, interceptor, error codes
├── exception/    NotFound / Conflict + global handler
├── hrms/         biometric HRMS client and sync services
└── report/       aggregation, narration, PDF rendering, controller

src/main/resources/
├── application.properties
└── db/migration/ V1 … V25 Flyway migrations
```

---

## Tech stack

| Concern | Library |
|---|---|
| Framework | Spring Boot 3.3.1 (`web`, `data-jpa`) |
| Servlet container | Tomcat — scope `provided`, supplied by the deployment target |
| Database | MySQL 8 via `mysql-connector-j` |
| Migrations | Flyway (`flyway-core`, `flyway-mysql`) |
| Tokens | JJWT 0.12.6 |
| Password hashing | `spring-security-crypto` (BCrypt) |
| Report narration | `anthropic-java` 2.34.0 |
| PDF rendering | Flying Saucer 9.3.2 + jsoup 1.18.3 |

---

## Prerequisites

- **JDK 17** — the WAR targets Java 17. A newer JDK compiles, but the artifact will not run on
  the production server's JRE 17.
- **MySQL 8** with a database and user created.
- **Tomcat 10.1+** for WAR deployment only; local development uses the embedded server.

Maven is not required — the repository includes the Maven Wrapper (`./mvnw`).

## Installation

```bash
git clone <repository-url>
cd backend
./mvnw dependency:go-offline
```

## Database configuration

```sql
CREATE DATABASE smo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'smo'@'localhost' IDENTIFIED BY '<password>';
GRANT ALL PRIVILEGES ON smo.* TO 'smo'@'localhost';
FLUSH PRIVILEGES;
```

Flyway creates and populates every table on first startup. Do not create tables by hand:
`spring.jpa.hibernate.ddl-auto` is `validate`, so the application refuses to start if the
schema does not match the entities.

---

## Environment variables

Every setting is an environment variable with a fallback in `application.properties`.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3306/smo?...` | JDBC connection string |
| `DB_USERNAME` | `smo` | Database user |
| `DB_PASSWORD` | *(see note)* | Database password |
| `DB_POOL_SIZE` | `10` | HikariCP maximum pool size |
| `JPA_SHOW_SQL` | `false` | Log generated SQL |
| `LOG_PATH` | `/opt/tomcat/logs/smo-backend.log` | Log file location |
| `APP_LOG_LEVEL` | `INFO` | Level for `com.antigravity.mes` |
| `APP_JWT_SECRET` | *(see note)* | HMAC signing key for tokens |
| `APP_JWT_EXPIRATION_MINUTES` | `480` | Session token lifetime |
| `APP_ENCRYPTION_KEY` | *(see note)* | Key for encrypted entity fields |
| `HRMS_ENABLED` | `true` | Enable biometric attendance integration |
| `HRMS_BASE_URL` | `https://hrms.thegttech.com` | HRMS endpoint |
| `HRMS_USERNAME` | *(empty)* | HRMS credential — integration self-disables if unset |
| `HRMS_PASSWORD` | *(empty)* | HRMS credential |
| `ANTHROPIC_REPORTS_ENABLED` | `true` | Enable report generation |
| `ANTHROPIC_API_KEY` | *(empty)* | Report narration credential |

> **Security note.** `application.properties` is tracked in version control, so it deliberately
> carries **no** default for `DB_PASSWORD`, `APP_JWT_SECRET` or `APP_ENCRYPTION_KEY` — supply
> all three as environment variables or in external configuration. Never commit them, and never
> commit `HRMS_*` or `ANTHROPIC_API_KEY`.

Running locally therefore needs the database password supplied:

```bash
DB_PASSWORD='<password>' ./mvnw spring-boot:run
```

For deployment, place overrides in a file **named `application.properties`** outside the WAR:

```
--spring.config.additional-location=file:/opt/tomcat/conf/
```

A file named `application-secrets.properties` will **not** load — Spring reads the suffix as a
profile name and ignores the file silently. See `application.properties.example`.

---

## Running

```bash
./mvnw spring-boot:run
```

With integration credentials supplied inline:

```bash
HRMS_USERNAME=<user> HRMS_PASSWORD=<pass> ANTHROPIC_API_KEY=<key> ./mvnw spring-boot:run
```

Verify with `curl http://localhost:8080/api/auth/health`.

## Build

```bash
./mvnw clean package -DskipTests    # → target/smo.war
```

`spring-boot-starter-tomcat` is `provided` and `MesApplication` extends
`SpringBootServletInitializer`, so the one artifact both runs standalone and deploys to Tomcat.

---

## Authentication & authorization

Login is two-stage, because an employee may hold several roles.

1. `POST /api/auth/login` — returns a full session token (single role) or
   `requiresRoleSelection: true` with a short-lived `preAuthToken` and the roles held.
2. `POST /api/auth/select-role` — exchanges that token plus a `roleName` for a session token
   scoped to the chosen role.

Send it as `Authorization: Bearer <token>` on every subsequent request.

Authorization is **activity-based**, not role-based. Each role owns a JSON list of permitted
activities; controller methods declare what they need with `@RequiresActivity("OPERATIONS")`,
and `RoleAuthorizationInterceptor` rejects the request when the active role lacks it.

Activities: `SPLASH`, `LOGIN`, `DASHBOARD`, `PROFILE`, `EMPLOYEE_LIST`, `EMPLOYEE_DETAIL`,
`EMPLOYEE_FORM`, `ROLE_LIST`, `ROLE_FORM`, `ATTENDANCE`, `STATION_LIST`, `STATION_FORM`,
`OPERATIONS`, `MY_PRODUCTION`, `REPORTS`.

## API documentation

Full reference with parameters and worked examples: **[docs/API.md](docs/API.md)**.
Base path `/api`; all controllers set `@CrossOrigin(origins = "*")`.

| Group | Base | # | Group | Base | # |
|---|---|---|---|---|---|
| Authentication | `/api/auth` | 4 | Tracking | `/api/tracking` | 3 |
| Employees | `/api/employees` | 11 | Production entries | `/api/production-entries` | 3 |
| Roles | `/api/roles` | 7 | Station status | `/api/station-status` | 1 |
| Stations | `/api/stations` | 6 | Stock availability | `/api/stock-availability` | 2 |
| Attendance | `/api/attendance` | 4 | Stock position | `/api/stock-position` | 2 |
| HRMS | `/api/hrms` | 3 | WIP / bundles | `/api/wip` | 5 |
| Allocations | `/api/allocations` | 4 | Dashboard | `/api/dashboard` | 1 |
| Profile | `/api/profile` | 3 | Reports | `/api/reports` | 3 |

---

## Error handling

`GlobalExceptionHandler` maps exceptions to a consistent JSON body:

| Exception | Status |
|---|---|
| `NotFoundException` | 404 |
| `ConflictException` | 409 |
| `IllegalArgumentException` | 400 |
| any other `Exception` | 500 |

Validation failures carry a stable code from `ErrorCodes`, so the client can translate rather
than display server English:

```json
{ "code": "ERR_TIME_OVERLAP", "error": "This time overlaps another entry already logged for this operator" }
```

Codes: `ERR_NO_ACCESS`, `ERR_SESSION_INVALID`, `ERR_PIECES_NEGATIVE`, `ERR_TIME_NOT_POSITIVE`,
`ERR_TIME_REQUIRED`, `ERR_END_BEFORE_START`, `ERR_TIME_OVERLAP`, `ERR_EMPLOYEE_NOT_FOUND`,
`ERR_STATION_NOT_FOUND`, `ERR_HRMS_NOT_CONFIGURED`, `ERR_HRMS_UNREACHABLE`,
`ERR_HRMS_REJECTED`, `ERR_REPORTS_DISABLED`, `ERR_REPORT_FAILED`. Responses without a `code`
carry `error` only.

## Logging

SLF4J over Spring Boot's Logback. Writes to `LOG_PATH` and the console. Root `INFO`,
application level from `APP_LOG_LEVEL`, Flyway at `INFO` so applied migrations are always
visible at startup.

---

## Deployment

See [docs/PRODUCTION_SERVER.md](docs/PRODUCTION_SERVER.md) for the external config file's real
path, required keys, and the encryption-key rotation warning — worth reading before touching
`/etc/smo/` for the first time.

Build the WAR, then on the server:

```bash
mysqldump -u smo -p smo > ~/smo-backup-$(date +%F).sql   # migrations may alter the schema
sudo systemctl stop tomcat
sudo rm -rf /opt/tomcat/webapps/smo /opt/tomcat/webapps/smo.war
sudo rm -rf /opt/tomcat/work/Catalina/localhost/smo      # required — see troubleshooting
sudo cp smo.war /opt/tomcat/webapps/smo.war
sudo chown tomcat:tomcat /opt/tomcat/webapps/smo.war
sudo systemctl start tomcat
sudo tail -f /opt/tomcat/logs/catalina.out
```

Look for `Successfully applied N migration(s)` and `Started MesApplication`. External
configuration lives at `/opt/tomcat/conf/application.properties` and is untouched by a
redeploy. In production, nginx proxies the domain root onto the `/smo` context, so clients call
the bare domain with no path suffix.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Connects to the wrong database | A stale `DB_URL` exported in `/opt/tomcat/bin/setenv.sh` overrides the config file. |
| WAR deploys, nothing responds | Built with a newer JDK than the server JRE. Check `<java.version>` and `java -version`. |
| Old behaviour after redeploy | Tomcat served a cached expansion. Remove `webapps/smo/` **and** `work/Catalina/localhost/smo/`. |
| External config ignored | Named `application-<x>.properties`, read as a profile. Rename to `application.properties`. |
| `401` on HRMS-backed endpoints | Blank `HRMS_USERNAME`/`HRMS_PASSWORD`; the client reports itself disabled. |
| HRMS unreachable from the server | Public DNS may not resolve internally — add a hosts entry for the private address. |
| Startup fails validating schema | `ddl-auto=validate` found a mismatch. Let Flyway migrate; never alter tables by hand. |
| Flyway `max_user_connections` | Lower `DB_POOL_SIZE` to fit the hosting plan's limit. |

---

## Contributing

- **Keep files small and single-purpose** — split before writing, not by refactoring after.
- **Controllers stay thin.** Business logic and all calculation belong in services.
- **Schema changes are migrations.** Add `V<n>__description.sql`; never edit an applied
  migration and never alter tables manually.
- **A new screen is a new activity.** Add it to `ActivityCatalog`, grant it to roles in the
  same migration, and annotate the endpoints with `@RequiresActivity`.
- **User-facing validation uses `ErrorCodes`,** never hardcoded English.
- **No secrets in committed files.**
- Run `./mvnw clean package` before opening a pull request.
