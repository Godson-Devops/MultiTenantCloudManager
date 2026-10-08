# MultiTenantCloudManager

A self-service portal for provisioning virtual machines (OpenStack) and pods
(Kubernetes), with per-user quotas. Plain Jakarta Servlets + MySQL, no
framework.

Each user signs in, sees only their own resources, and creates, stops, restarts
or deletes them. MySQL is the portal's own record of what should exist; a
background worker continuously reconciles it with the two providers, so changes
made outside the portal (`kubectl`, the OpenStack CLI) show up on the dashboard.

## Requirements

| | |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| MySQL | 8.0+ on `localhost:3306`, database `cloudportal` |
| Servlet container | Tomcat 11 |
| OpenStack | optional — only needed to actually create VMs |
| Kubernetes | optional — only needed to actually create pods |

The portal starts and serves its pages without OpenStack or Kubernetes. Create
requests return `502` while a provider is unreachable; everything else works.

## Build

```bash
mvn package          # target/MultiTenantCloudManager.war
```

## Run

**Eclipse:** import the existing project, then start the bundled
`Tomcat v11.0 Server at localhost`. The context path is
`/MultiTenantCloudManager`, so the portal is at
<http://localhost:8088/MultiTenantCloudManager/>.

**Command line:**

```bash
cp target/MultiTenantCloudManager.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh
```

On boot, `SyncWorkerListener` creates the MySQL tables and indexes and starts
the reconciliation worker.

### If Eclipse publishes an empty `WEB-INF/lib`

WTP has been observed publishing the webapp with **no** dependency jars, which
makes startup fail with `NoClassDefFoundError`. The dependencies are installed in
the server's shared library folder instead, where a publish cannot remove them:

```
.metadata/.plugins/org.eclipse.wst.server.core/tmp0/lib/
```

To restore them after Eclipse regenerates the server:

```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:copy-dependencies \
  -DoutputDirectory=../.metadata/.plugins/org.eclipse.wst.server.core/tmp0/lib
```

## Configuration

Defaults live in `src/main/resources/portal.properties`. Every key can be
overridden by an environment variable: upper-case the key and replace `.` with
`_`.

| Variable | Meaning |
|---|---|
| `MYSQL_URL` | default `jdbc:mysql://localhost:3306/cloudportal` |
| `MYSQL_USER`, `MYSQL_PASSWORD` | database credentials, default `root` / empty |
| `QUOTA_DEFAULT_MAX_VM`, `QUOTA_DEFAULT_MAX_POD` | defaults for new accounts |
| `SYNC_PERIOD_SECONDS` | reconciliation interval, default `30` |
| `OPENSTACK_AUTH_URL`, `OPENSTACK_USERNAME`, `OPENSTACK_PASSWORD` | required to create VMs |
| `OPENSTACK_DOMAIN`, `OPENSTACK_PROJECT` | default `Default` / `service` |
| `K8S_MASTER_URL`, `K8S_TOKEN` | required to create pods |
| `K8S_NAMESPACE_PREFIX` | pods are created in `<prefix><userId>` |
| `PROMETHEUS_URL`, `PROMETHEUS_NODE_EXPORTER_PORT` | metrics source |
| `POD_LOGS_MAX_DURATION_MS` | log stream cap, default `60000` |

Passwords and tokens are read from the environment precisely so they never end
up in source control.

## Endpoints

All replies are JSON. Everything except `/register`, `/login` and `/logout`
requires a session and answers `401` without one; a resource owned by another
user answers `403`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/register` | create an account (quota defaults applied) |
| POST | `/login` | sign in, establishes the session |
| GET | `/logout` | end the session |
| POST | `/vm/create` | create a VM — quota is reserved before the provider call |
| GET | `/vm/list`, `/vm/details`, `/vm/metrics` | read VMs and their metrics |
| POST | `/vm/action` | start / stop / restart / delete |
| POST | `/pod/create` | create a pod in the user's namespace |
| GET | `/pod/list`, `/pod/details`, `/pod/metrics` | read pods and their metrics |
| POST | `/pod/action` | start / stop / restart / delete |
| GET | `/pod/logs` | stream pod logs as Server-Sent Events |

`429` means the caller exceeded the login rate limit (10 attempts a minute per
address). `409` means the name is already taken.

## Status vocabulary

One enum, `com.portal.model.Status`, shared by the database, the providers and
every page. Each constant owns the exact string that is stored and sent:

`creating` · `running` · `stopped` · `error` · `deleted`

The DAOs store these codes, so the database holds `running` rather than
`RUNNING`. A value the vocabulary does not contain reads back as `error` — the
row is inconsistent, and saying so beats guessing a state the UI cannot render.

## Layout

```
src/main/java/com/portal/
  servlet/   15 servlets: HTTP in, JSON out, session and ownership checks
  dao/       MySQL access (users, vm_table, pod_table) and schema creation
  model/     User, VmDetails, PodDetails, Status
  service/   OpenStackService, KubernetesService, PrometheusService, ServiceRegistry
  util/      JdbcConnection, AppConfig, RateLimiter, WebUtil
  listener/  SyncWorkerListener: schema creation + reconciliation worker
src/main/webapp/   11 JSP pages and js/portal.js
```

Provider clients are built once by `ServiceRegistry` and shared; a client that is
down at boot is not cached, so it recovers when the provider returns.

## Tests

```bash
mvn test
```

The DAO tests run against a real MySQL because atomic conditional updates are
exactly what cannot be verified with a mock. They write only to
`cloudportal_test` and skip themselves when no MySQL is listening.