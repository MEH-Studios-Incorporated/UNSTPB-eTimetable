# Deploying the server

The server is one Docker image plus PostgreSQL, started with
`deploy/docker-compose.yml`. Where it runs is the university's choice.

## First start

```sh
cp deploy/.env.example deploy/.env    # set ETT_DB_PASSWORD to a long random value
docker compose -f deploy/docker-compose.yml up -d
curl http://localhost:8080/api/v1/health
```

The server creates and migrates its tables on every start. Set
`ETT_ADMIN_EMAIL` and `ETT_ADMIN_PASSWORD` for the first start: that account
signs in at `/admin`, changes its password and adds the other accounts.
A password shorter than 12 characters works for that first sign-in, but the
dashboard then asks for a new one before anything else.
The address itself (`/`) is a public page for students: what the app does,
the download links from `ETT_ANDROID_URL` and `ETT_IOS_URL`, the current
teaching week and a link to the dashboard.
Without a current semester, `GET /api/v1/dataset` answers 404 `no_semester`
until an admin publishes one; `docs/admin.md` explains how.

## Settings

Every setting is an environment variable listed in `deploy/.env.example`.
The compose file passes the database ones to the server.

| Variable | Default | Meaning |
| --- | --- | --- |
| `ETT_PUBLIC_PORT` | 8080 | Port on the host |
| `ETT_DB_NAME`, `ETT_DB_USER` | etimetable | PostgreSQL database and user |
| `ETT_DB_PASSWORD` | none, required | PostgreSQL password |
| `ETT_PUBLIC_URL` | empty | The address people use; `https://` makes the dashboard cookie Secure |
| `ETT_ADMIN_EMAIL`, `ETT_ADMIN_PASSWORD` | empty | First dashboard account, created only while there is none |
| `ETT_ANDROID_URL`, `ETT_IOS_URL` | empty | Download links on the public page at `/` (a store page or a hosted file); empty shows "în curând" |
| `ETT_FCM_SERVICE_ACCOUNT` | empty | Firebase service account key (JSON); empty turns push off |
| `ETT_IMAGE` | the latest release on GHCR | Image to run, for pinning a version |

## Push notifications

The server sends new announcements to Android phones through the Firebase
project the Android app is published with. In the Firebase console, open
Project settings, Service accounts, and generate a new private key. Put the
downloaded file on one line into `deploy/.env`:

```sh
echo "ETT_FCM_SERVICE_ACCOUNT='$(jq -c . service-account.json)'" >> deploy/.env
```

The log says at start whether push is on and for which project. The key
lets anyone send messages to the app's users: keep it out of version
control and delete the downloaded file once it is in `.env`.

The host's clock must be synchronized (`timedatectl set-ntp true`). Google
refuses the server's sign-in when the clock is more than a few minutes off,
and no notification goes out. The dashboard says so when an announcement is
published on such a server.

## HTTPS and reverse proxies

The server speaks plain HTTP on port 8080. Put it behind the university's
HTTPS reverse proxy; the apps only talk to an `https://` address in
release builds.

The dashboard refuses a form post unless its `Origin` (or `Referer`) names
the host the browser asked for, so the proxy must pass the original `Host`
header (`proxy_set_header Host $host;` in nginx). Five wrong passwords or
codes for one dashboard account within 15 minutes lock that account for 15
minutes; the lock is per account, not per address, for the reason below.

Sign-up and login are limited to 10 requests a minute per client address.
Behind a reverse proxy every request arrives from the proxy's address, so
the limit applies to all students together. Until the server reads the
forwarded address, raise the limit or rate-limit at the proxy instead.

## Backups

All data is in the `db-data` volume, event pictures included (they are
stored in PostgreSQL, a few hundred kilobytes each). A nightly
`docker compose exec db pg_dump -U etimetable etimetable > backup.sql` is
enough; a semester can also be published again from its CSV.
