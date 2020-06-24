# Projectlocker

Projectlocker is a database interface for storing, backing up and creating new projects from templates.

## Installation

Projectlocker uses a CI build system, at http://circleci.com/gh/fredex42/projectlocker. RPMs are built for every commit,
and it should be installed from said RPM.

## Session cookie setup

Logins are persisted by using session cookies. This is controlled by the `play.http.session` section of `application.conf`.

When deploying, you should ensure that the `domain =` setting is configured to be the domain within which you are deploying,
to prevent cookie theft. It's also recommended to serve via https and set `secure = true` (but this could be problematic if you're
only implementing https to the loadbalancer)

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

## Authentication Setup

Projectlocker is intended to run against an ldap-based authentication system, such as Active Directory. This is configured
in `application.conf` but it can be turned off during development.
It also supports bearer-token and server->server shared secret (HMAC) auth, see "Authentication Precedence" below.

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

## Development

### Prerequisites

- You need a working postgres installation. You could install postgres using a package manager, or you can quickly run a Dockerised version by
  running the `setup_docker_postgres.sh` script in `scripts`.
- You need an installation of node.js to build the frontend. It's easiest to first install the Node Version Manager, nvm, and then use this to install node: `nvm install 8.1.3`
- You need a version 1.8+ JDK. On Linux this is normally as simple as `apt-get install openjdk-8-jdk` or the yum equivalent
- If you are not using the postgres docker image, you will need to set up the test database before the tests will work:
  `sudo -u postgres ./scripts/create_dev_db.sh` (**Note**: if installing through homebrew, postgres runs as the current
  user so the `sudo -u postgres` part is not required)

### Backend

- The backend is a Scala play project. IDEs like Intellij IDEA can load this directly and have all of the tools needed to build and test.
- If you want to do this from the terminal, you'll need to have the Scala Built tool installed: `wget https://dl.bintray.com/sbt/debian/sbt-0.13.12.deb && sudo dpkg -i sbt-0.13.12.deb`
- You can then run the backend tests: `sbt test`. **Note**: after each invokation of the backend tests, you should run `scripts/blank_test_db.sh` to reset the tests database so that the next invokation will run correctly.
- If the tests fail, check that you have set up the projectlocker_test database properly (see previous section, and also check `circle.yml` to see how the CI environment does it)

### Frontend

The frontend is written in ES2016 JavaScript using React JSX. To run in a browser, it's transpiled with Babel and bundled with Webpack

Installing `yarn` is recommended to manage JavaScript dependencies. See `https://yarnpkg.com/lang/en/docs/install/`.

The following list explains the available yarn scripts in the frontend directory. (Commands in this section assume the current working directory is `${PROJECT_ROOT}/frontend/`.)

- Install dependencies: `yarn`
- Run tests: `yarn test`
- Build the frontend: `yarn build` (use `yarn build:prod` to build for a production environment)
- Run a local development server: `yarn start`. This builds the project in a development environment and continues to monitor source files for modifications. The project is rebuilt automatically when they occur.

### Running the backend tests

The backend tests can be run with sbt, but since they depend on having a test database (`projectlocker-test`) in a specific state to start
and on the akka cluster configuration being suitable, they can be a pain to get working. If you are having trouble getting `sbt test` to pass,
make sure of the following:

- set `ldap.ldapProtocol` to `"ldaps"` in the config (this will fix errors around "expected "testuser", got "noldap")
- set `akka.remote.netty.tcp.port` to `0` in order to bind to a random available port (this will fix guice provisioning errors,
  but can stop the app running properly in dev mode)
- run the database with `scripts/setup_docker_postgres.sh`. This will automatically set up projectlocker_test for you.
- reset the state of the database before each test run. The simplest way to do this is to use `scripts/setup_docker_postgres.sh`
  and use CTRL-C to exit the database and re-run it to set up the environment again before each run

### Authentication Precedence

Projectlocker supports three distinct types of authentication. Only one is ever applied to an incoming request, depending on
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

Projectlocker supports HMAC signing of requests for server-server actions.
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

### OAuth2 Autentication

Projectlocker supports validation with OAuth2 bearer tokens. It is assumed that the frontend client is taking care of actually obtaining said tokens.

To use this method, present a header called `Authorization` in the format `Bearer: {token}` where {token} is the JWT obtained from the identity provider. (See https://jwt.io/ for lots more information and tools to generate/validate test JWTs.)

The server requires the public signing certificate of your IdP to validate the token against. Place it in `application.conf` as per the comments in that file. If a presented token validates and its expiry time is not past, then the request will be allowed.

In the application config, you should also provide the name of a claims field to indicate whether the user is an admin or not. If this field is present in the JWT then the user will be considered an admin, if not then they will be considered a regular user.

### Testing a built image

An image built from the CI process can be tested locally using `docker-compose`. See [Testing a built image](scripts/test_built_image/README.md)
for more information.

