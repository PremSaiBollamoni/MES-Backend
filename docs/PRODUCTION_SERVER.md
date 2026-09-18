# Production Server Reference

Facts about the live server that cost real debugging time to (re)discover. Keep this current —
it is the difference between a five-minute deploy and an hour of log spelunking.

**No secret values are recorded here.** This file lists paths, filenames, and key *names* only.
Actual passwords, tokens and keys live solely in `/etc/smo/application.properties` on the server.

---

## The external config file

```
/etc/smo/application.properties
```

This is the **only** file Spring loads for production secrets. It sits outside the deployed WAR,
so redeploying `smo.war` never touches or overwrites it.

**The filename must be exactly `application.properties`.** A name like
`application-secrets.properties` is read by Spring as a *profile* named `secrets`, not as an
included properties file — Spring silently ignores it and every value inside is lost. This has
happened on this project before. If `setenv.sh` or any documentation refers to
"`application-secrets.properties`", treat that as a stale comment, not the real filename — verify
against this file's actual name on disk before trusting it.

### How it's loaded

`/opt/tomcat/bin/setenv.sh` sets:

```sh
export JAVA_OPTS="$JAVA_OPTS -Dspring.config.additional-location=file:/etc/smo/"
```

Spring Boot then looks inside that directory for a file named `application.properties` and
merges it over the WAR's built-in `src/main/resources/application.properties`.

### Required keys

The WAR ships with **no default values** for these — the application will not start without them
present in `/etc/smo/application.properties`:

| Key | Consequence if missing |
|---|---|
| `spring.datasource.password` | Cannot connect to MySQL |
| `app.jwt.secret` | Cannot start — `CryptoConfig`/JWT bean initialization fails |
| `app.encryption.key` | **Cannot start** — must decode to exactly 32 bytes (AES-256). See warning below. |

Present with working defaults if unset, but should still be set explicitly in production:

| Key | Effect if unset |
|---|---|
| `app.hrms.enabled`, `app.hrms.base-url`, `app.hrms.username`, `app.hrms.password` | Blank username/password self-disables the HRMS integration — attendance sync and the present-employee lists silently return empty rather than erroring |
| `app.anthropic.api-key` | Blank key self-disables report generation and production-book scanning — both endpoints report themselves "unavailable" rather than failing |

### ⚠️ `app.encryption.key` must never be rotated casually

This key decrypts the `address_encrypted` and `emergency_contact_number_encrypted` columns in
`employee_info`. **Changing it makes every already-encrypted row permanently unreadable** — there
is no re-encryption migration. If this file is ever recreated from scratch, the encryption key
value must be the same one already in use, not a freshly generated one, unless you are certain no
employee record currently has encrypted PII (check: `SELECT COUNT(*) FROM employee_info WHERE
address_encrypted IS NOT NULL OR emergency_contact_number_encrypted IS NOT NULL;`).

The current production value is recorded (encrypted at rest, access-controlled) outside this
repository — ask whoever last deployed the encryption key change, or check the local development
`backend/local-config/application.properties` on the machine that has previously synced it.

---

## Deploying a new WAR

The external config is **never touched** by a WAR deploy. The standard sequence:

```bash
mysqldump -u smo -p smo > ~/smo-backup-$(date +%F).sql
sudo systemctl stop tomcat
sudo rm -rf /opt/tomcat/webapps/smo /opt/tomcat/webapps/smo.war /opt/tomcat/work/Catalina/localhost/smo
sudo cp /tmp/smo.war /opt/tomcat/webapps/smo.war
sudo chown tomcat:tomcat /opt/tomcat/webapps/smo.war
sudo systemctl start tomcat
sudo tail -f /opt/tomcat/logs/catalina.out
```

Removing `work/Catalina/localhost/smo` (Tomcat's expanded-WAR cache) is not optional — skipping it
has previously caused Tomcat to keep serving old compiled classes after a deploy that otherwise
looked successful.

Watch the log for `Successfully applied N migration(s)` and `Started MesApplication`, then verify:

```bash
curl -s https://smobza.thegttech.com/api/auth/health
```

---

## Verifying the config file after any edit

```bash
sudo grep -E "^(spring.datasource.password|app.jwt.secret|app.encryption.key|app.hrms|app.anthropic)" \
  /etc/smo/application.properties | sed 's/=.*/=<set>/'
```

Prints which keys are present without printing their values — safe to run and share when
diagnosing a startup failure.
