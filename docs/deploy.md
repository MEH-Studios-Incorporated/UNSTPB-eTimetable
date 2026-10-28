# Deploying the server

The server is one Docker image plus PostgreSQL, started with
`deploy/docker-compose.yml`. Where it runs is the university's choice.

## First start

```sh
cp deploy/.env.example deploy/.env    # set ETT_DB_PASSWORD to a long random value
docker compose -f deploy/docker-compose.yml up -d
curl http://localhost:8080/api/v1/health
```

The server creates and migrates its tables on every start. Without a
current semester, `GET /api/v1/dataset` answers 404 `no_semester` until an
admin publishes one.

## Settings

Every setting is an environment variable listed in `deploy/.env.example`.
The compose file passes the database ones to the server.

| Variable | Default | Meaning |
| --- | --- | --- |
| `ETT_PUBLIC_PORT` | 8080 | Port on the host |
| `ETT_DB_NAME`, `ETT_DB_USER` | etimetable | PostgreSQL database and user |
| `ETT_DB_PASSWORD` | none, required | PostgreSQL password |
| `ETT_IMAGE` | the latest release on GHCR | Image to run, for pinning a version |

## HTTPS and reverse proxies

The server speaks plain HTTP on port 8080. Put it behind the university's
HTTPS reverse proxy; the apps only talk to an `https://` address in
release builds.

Sign-up and login are limited to 10 requests a minute per client address.
Behind a reverse proxy every request arrives from the proxy's address, so
the limit applies to all students together. Until the server reads the
forwarded address, raise the limit or rate-limit at the proxy instead.

## Backups

All data is in the `db-data` volume. A nightly
`docker compose exec db pg_dump -U etimetable etimetable > backup.sql` is
enough; a semester can also be published again from its CSV.
