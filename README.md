# pluto-core

Pluto-Core is the central piece of the new, distributed Production Lifecycle Utilities & Tools system used by Guardian News & Media to manage multimedia production and assets.

It's a standard frontend-backend webapp design, utilising Play framework on Scala 2.13 for the backend and ReactJS with javascript/typescript for the frontend.

## Packaging

pluto-core is intended to run in a containerised system such as Docker, Kubernetes, Rancher etc.  Packages are automatically built by Gitlab from all merge requests and the master branch and can be found at https://hub.docker.com/repository/docker/guardianmultimedia/pluto-core.  All images are tagged by the build number, which can be found by examining the logs of the "deploy" CI stage.

### Building the docker image locally

Sometimes during development you may want to build an image from your local repo, e.g. to use in the context of prexit-local.  In order to do this:
- make sure that you have a valid UI build:
```
$ cd frontend/
$ yarn build
$ cd ..
```
- if building for prexit-local, make sure you are in the minikube docker context (consult the prexit-local documentation for this)
- build the docker image via sbt:
```
$ sbt docker:publishLocal
```
- this should create a local docker image called `guardianmultimedia/pluto-core:DEV`.  If done from the minikube context it will immediately be available for use in the cluster.

## Session cookie setup

pluto-core is still under active development and still uses a session cookie. This is controlled by the `play.http.session` section of `application.conf`.

When deploying, you should ensure that the `domain =` setting is configured to be the domain within which you are deploying,
to prevent cookie theft. It's also recommended to serve via https and set `secure = true` (but this could be problematic if you're
only implementing https to the loadbalancer)

## Authentication setup

Projectlocker, the forerunner of pluto-core, was intended to run against an ldap-based authentication system, such as Active Directory.  pluto-core still uses this mechanism in addition to JWT specifically for integration with the plutohelperagent desktop app (https://github.com/guardian/plutohelperagent).
LDAP is configured in `application.conf` and it can be turned off during development.  When turned off, all accesses are treated as authenticated accesses by a user called `noldap`.
It also supports bearer-token and server->server shared secret (HMAC) auth, see "Authentication Precedence" below.

### oauth2
pluto-core is intended to be configured with OAuth2 authentication for single sign-on.  In order to complete this configuration, you will need to get a copy of the public key or signing certificate from your ID provider and paste it into the `application.conf` file under `auth.tokenSigningCert`.  You need to include the `------BEGIN {thing}--------` header/footer lines and should preserve the newlines.

Oauth2 allows the sharing of user profile data through arbitrary key-value pairs included in the token, known as "claims".  You should configure your ID provider to
set a claim key to any string value if the user should be treated as an administrator.  Then, put the name of that key into the `auth.adminClaimName` field in `application.conf`.

pluto-core has no provision for asking the user to log in, this is done by pluto-start.  The frontend simply expects the token to be present in the browser's session storage and will use this value to build an Authorization header for any requests to the backend.
If the backend responds with a 403 then the frontend will attempt to refresh the token and redirect back to the server root if it can't.


### ldaps

Secure ldap is recommended, as it not only encrypts the connection but protects against man-in-the-middle attacks.
In order to configure this, you will need to have a copy of the server's certificate and to create a trust store with it.
If your certificate is called `certificate.cer`, then the following commands will create a keystore:

```
$ mkdir -p /usr/share/projectlocker/conf
$ keytool -import -keystore /usr/share/projectlocker/conf/keystore.jks -file certificate.cer
[keytool will prompt for a secure passphrase for the keystore and confirmation to add the cert]
```

`keytool` should be provided by your java runtime environment.

In order to configure this, you need to adjust the `ldap` section in `application.conf`:

```
  ldapProtocol = "ldaps"
  ldapUseKeystore = true
  ldapPort = 636
  ldapHost0 = "adhost1.myorg.int"
  ldapHost1 = "adhost2.myorg.int"
  serverAddresses = ["adhost1.myorg.int","adhost2.myorg.int"]
  serverPorts = [ldapPort,ldapPort]
  bindDN = "aduser"
  bindPass = "adpassword"
  poolSize = 3
  roleBaseDN = "DC=myorg,DC=com"
  userBaseDN = "DC=myorg,DC=com"
  trustStore = "/usr/share/projectlocker/conf/keystore.jks"
  trustStorePass = "YourPassphraseHere"
  trustStoreType = "JKS"
  ldapCacheDuration = 600
  acg1 = "acg-name-1"
```

Replace `adhost*.myorg.int` with the names of your AD servers, `aduser` and `adpassword` with the username and password
to log into AD, and your DNs in `roleBaseDN` and `userBaseDN`.

### ldap

Plain unencrypted ldap can also be used, but is discouraged. No keystore is needed, simply configure the `application.conf`
as above but use `ldapProtocol = "ldap"` and `ldapPort = 336` instead.

### none

Authentication can be disabled, if you are working on development without access to an ldap server. Simply set
`ldapProtocol = "none"` in `application.conf`. This will treat any session to be logged in with a username of `noldap`.

Fairly obviously, don't deploy the system like this!

### Authentication Precedence

pluto-core supports three distinct types of authentication. Only one is ever applied to an incoming request, depending on
the headers present in that request:

- HMAC shared-secret
  If an incoming HTTP request contains the `X-HMAC-Authentication` header, then shared-secret authentication is applied
  (for details, see `Signing requests for server->server interactions`)

- Bearer token authentication
  If an incoming HTTP request does not contain X-HMAC-Authentication but does contain the `Authorization` header then bearer-token
  authentication is applied (for details, see `OAuth2 authentication`)

- LDAP in-session
  If an incoming HTTP request contains neither authentication header then session-based auth is applied. The server expects a
  session cookie to be present and to cryptographically validate and it gets the authentication information from that. If
  no cookie is present, then the requestor must call the /login endpoint to present username/password credentials for validation
  against LDAP and if successful a session cookie will be returned.

### Signing requests for server->server interactions

pluto-core supports HMAC signing of requests for server-server actions.  This is used by the assetsweeper ingest/monitoring system
In order to use this, you must:

- provide a base64 encoded SHA-384 checksum of your request's content in a header called `X-Sha384-Checksum`
- ensure that an HTTP date is present in a header called `Date`
- ensure that the length of your body content is present in a header called `Content-Length`. If there is no body then this value should be 0.
- provide a signature in a header called 'Authorization'. This should be of the form `{uid}:{auth}`, where {uid} is a user-provided
  identifier of the client and {auth} is the signature

The signature should be calculated like this:

- make a string of the contents of the Date, Content-Length and Checksum headers separated by newlines followed by the
  request method and URI path (not query parts) also separated by newlines.
- use the server's shared secret to calculate an SHA-384 digest of this string, and base64 encode it
- the server performs the same calculation (in `auth/HMAC.scala`) and if the two signatures match then you are in.
- if you have troubles, turn on debug at the server end to check the string_to_sign and digests

There is a working example of how to do this in Python in `scripts/test_hmac_auth.py`

## Project backups

Backups can be configured in the user interface.
- Set up a secondary storage, preferably on an external medium that supports versioning.
- Go into the configuration for your primary storage and set up the "backup" option to point to your secondary storage

Backups must be run in a seperate process to the main server.  This can be done by running
`/opt/docker/bin/backups_launcher`; you'd normally deploy this as a separate timed job (e.g. Kubernetes cronjob) to run
on a regular basis.

A normal backup run will check all projects with an "In Production" status and make a copy if there is no existing copy
on the secondary or if the copy there is a different size.  If the secondary storage supports versioning, then no backups
will be overwritten but new versions created for each run where there is a difference.

**NOTE** the `backups_launcher` kickoff script is automatically generated by the sbt universal packager.  It _will not work_
if you have **AshScriptPlugin** enabled in `build.sbt` when building.

## Development

### Prerequisites

- You should have Docker installed and working both to run postgres and rabbitmq and to build packages locally.
- You need a working postgres installation. You could install postgres using a package manager, or you can quickly run a Dockerised version by
  running the `setup_docker_postgres.sh` script in `scripts`.
- You should have a working rabbitmq installation too.  You can get this quickly by running the `setup_docker_rabbit.sh` script in `scripts`.
- You need an installation of node.js to build the frontend. We normally use the "Erbium" long-term-support version, 12.18.1.  It's easiest to first install the Node Version Manager, nvm, and then use this to install node: `nvm install 12.18.1`
- You need a version 1.11 JDK. On Linux this is normally as simple as `apt-get install openjdk-11-jdk` or the yum equivalent
- If you are not using the postgres docker image (not recommended!!), you will need to set up the test database before the tests will work:
  `sudo -u postgres ./scripts/create_dev_db.sh` (**Note**: if installing through homebrew, postgres runs as the current
  user so the `sudo -u postgres` part is not required.)

### Backend

- The backend is a Scala play project. IDEs like Intellij IDEA can load this directly and have all of the tools needed to build and test.
- If you want to do this from the terminal, you'll need to have the Scala Built tool installed: `wget https://dl.bintray.com/sbt/debian/sbt-1.3.10.deb && sudo dpkg -i sbt-1.3.10.deb` or whatever is the appropriate form for your platform
- You can then run the backend tests: `sbt test`. **Note**: after each invokation of the backend tests, you should restart the database container in order to blank out the test db so you start from a fresh state

### Running the backend tests

The backend tests can be run with sbt, but since they depend on having a test database (`projectlocker-test`) in a specific state to start
and on the akka cluster configuration being suitable, they can be a pain to get working. If you are having trouble getting `sbt test` to pass,
make sure of the following:

- set `ldap.ldapProtocol` to `"ldaps"` in the config (this will fix errors around "expected "testuser", got "noldap")
- run the database with `scripts/setup_docker_postgres.sh`. This will automatically set up projectlocker_test for you.
- reset the state of the database before each test run. The simplest way to do this is to use `scripts/setup_docker_postgres.sh`
  and use CTRL-C to exit the database and re-run it to set up the environment again before each run


### Frontend

The frontend is written in React with both Javascript and Typescript. To run in a browser, it's transpiled with the Typescript compiler and bundled with Webpack

Installing `yarn` is recommended to manage JavaScript dependencies. See `https://yarnpkg.com/lang/en/docs/install/`.

The following list explains the available yarn scripts in the frontend directory. (Commands in this section assume the current working directory is `${PROJECT_ROOT}/frontend/`.)

- Install dependencies: `yarn`
- Run tests: `yarn test`
- Build the frontend: `yarn build` (use `yarn build:prod` to build for a production environment)
- Build in watch mode: `yarn dev`. This builds the project in a development environment and continues to monitor source files for modifications. The project is rebuilt automatically when they occur.

### Run-local

In order to correctly run pluto-core, you need to have rabbitmq and postgres available.  There are scripts to do this using Docker in the `scripts/` directory.
- in one terminal window:
```
$ cd scripts/
$ ./setup_docker_postgres.sh
```
- in another terminal window:
```
$ cd scripts/
$ ./setup_docker_rabbit.sh
```
- in a third terminal window:
```
$ cd frontend/
$ yarn install
$ yarn dev
```
- then start up the local server either in your IDE or by running `sbt run`.

The server will fail to start if there is no database running on localhost:5432 (by default).

If rabbitmq is not running it will start up but will repeatedly log errors as it attempts to reconnect.


## Setting up the database server for deployment

Pluto-Core requires a PostGresQL database, it's tested against 9.6 at present.
In order to work with the "out of the box" docker config, you will need to:
1. deploy a Postgres image with the virtual hostname of "database" (or customise the POSTGRES_HOST environment var or 
the application.conf)
2. set this database up with a user called projectlocker, password projectlocker, and a database called projectlocker that
is owned by the projectlocker user.  Again these can be customised with POSTGRES_ environment
variables or adjusting the application.conf. I would strongly suggest setting a stronger password!
3. create another database called "journal" also owned by the projectlocker user
4. deploy the akka-persistence-jdbc schema to "journal"

All of these steps are carried out by the `scripts/docker-init/setup_dev.db.sh` script,
consult that for setup-by-step instructions.

These steps are carried out automatically when you run `scripts/setup_docker_postgres.sh`.

