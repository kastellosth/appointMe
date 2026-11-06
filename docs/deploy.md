# Deployment

One server running Docker Compose: PostgreSQL, the application, and a reverse proxy for HTTPS.

```text
internet --HTTPS--> reverse proxy --> 127.0.0.1:8080 app --> db (no published port)
```

## Configuration

Copy `.env.prod.example` to `.env.prod` and generate each value with `openssl rand -hex 32`. `.env.prod` is git-ignored.

The `prod` profile has no default values, so a missing setting stops the application at startup. The image selects that profile itself.

## Start and update

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
bash scripts/smoke-test.sh http://localhost:8080
bash scripts/demo-seed.sh http://localhost:8080   # optional: a demo business
```

API documentation is public at `/swagger-ui.html`.

To update: pull the code, run `./mvnw test` on a machine with Docker, then run the same `up -d --build`. Flyway applies new migrations at startup, and a lock keeps two instances from migrating at once.

**Rollback:** an older image can't undo a migration. Take a backup before every update that includes one, and keep new migrations compatible with the previous version of the code.

## Database roles

| Role | Used by | Can |
|---|---|---|
| `postgres` | Backups and emergencies | Everything. Not used by the application |
| `appointme_owner` | Flyway only | Own and change tables |
| `appointme_app` | The running application | Read and change rows. No schema changes, no migration history, no `UPDATE` or `DELETE` on `payments` and `audit_events` |

`scripts/day27-app-role-tests.sql` tries to break these limits.

## HTTPS

The application speaks plain HTTP and listens only on `127.0.0.1`. Passwords and tokens must not cross the internet without HTTPS, so run a reverse proxy on the server. For Caddy, which gets certificates automatically, the whole `Caddyfile` is:

```text
your-domain.example {
    reverse_proxy 127.0.0.1:8080
}
```

## Hosted option: Render and Neon

This needs no server of your own. HTTPS is included, and the app is at `https://<name>.onrender.com`. Neon hosts the database because Render's free database is deleted after about 44 days.

1. **Create the Neon project** (region: Frankfurt, PostgreSQL 16). Copy the **direct** connection details, not the ones with `-pooler` in the host: Flyway needs a session-level lock, which a connection pooler can break.
2. **Create the application role.** Run `deploy/managed-db-roles.sql` in Neon's SQL editor, with the password replaced by the output of `openssl rand -hex 32`.
3. **Create the Render service.** Choose **New > Blueprint**, select this repository, and enter:

   | Setting | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://<direct-host>/<database>?sslmode=require` |
   | `DATABASE_PASSWORD` | the password from step 2 |
   | `FLYWAY_USERNAME` | the Neon owner role |
   | `FLYWAY_PASSWORD` | the Neon owner password |

4. **Check it**, once the deploy has finished:

   ```bash
   bash scripts/smoke-test.sh https://<name>.onrender.com
   bash scripts/demo-seed.sh https://<name>.onrender.com
   ```

Limits of the free plans:

- **Slow first request.** The app sleeps after 15 minutes without traffic. Waking is a full Java start: 41 seconds at 0.5 CPU and 271 seconds at 0.1 CPU when measured locally with 512 MB. Open the URL before showing it to anyone.
- **Reminders stop while it sleeps.** Scheduled jobs run only while the app is awake, and they catch up when it wakes.
- **Monthly quotas.** Render suspends the service after 750 instance hours, and Neon suspends after 100 compute hours.

## Backup and restore

The database and its volume sit on one disk, so copy each backup to another machine.

```bash
# Backup
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T db \
    pg_dump -U postgres -Fc appointme > backup.dump

# Restore into a new, empty stack, before the application starts
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d db
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T db \
    pg_restore -U postgres -d appointme --clean --if-exists < backup.dump
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

Run `docker compose ... down -v` only if you mean it: `-v` deletes the database volume.

## Running two instances

Every job is safe to run twice at once: `ReminderRunner` claims rows with `FOR UPDATE SKIP LOCKED`. Run with two instances, 200 reminders were each sent exactly once, split 100 and 100.

The compose file starts one instance because a second would also need a load balancer. Keep the same `JWT_SECRET` in both, or tokens from one are refused by the other.

## Known limits

- **No rate limiting.** `/auth/login` and `/auth/register` are public. Add a limit at the proxy before real use.
- **Anyone can register** and create a business.
- **Reminders aren't sent.** `LoggingReminderSender` only writes a log line.
- **Changing `JWT_SECRET` logs everyone out**, because existing tokens stop being valid.
- **One server means one point of failure.** There is no automatic failover.
- **Backups aren't automatic.** Schedule the backup command and copy the file off the server.
- **Base images use moving tags** (`21-jre-alpine`, `postgres:16`), so a rebuild can bring newer patch versions.
