# Testing a built image

The CI process completes by pushing a Docker image to https://hub.docker.com/repository/docker/guardianmultimedia/pluto-core.
If you want to run it, then you'll need to have a working database set up and this is where the compose file here comes in.

To use it, you'll need docker-compose and docker installed https://docs.docker.com/compose/install/.

Once you have this, you need to set the build number you want to run.
In `docker-compose.yml`, find the line reading `image: guardianmultimedia/pluto-core:DEV` and replace :DEV with the 
build number you want to run (you can find this either from the docker hub page or the log from the `deploy` phase of the build)

With the desired build number in place, you should be able to  cd to scripts/test_built_image and 
run `docker-compose up` to start the stack.
It will download the relevant images and kick off a database instance.  **NOTE** - you must run this from the 
`scripts/test_built_image` directory, as it references the docker database setup in the parent dir.

With the server running, go to http://localhost:9000 to see the local version in action.  You should be logged in as
`noldap` by default.

## Setting up storages
The docker-compose setup has included a persistent volume that mounts at /data for the purposes of application data.
(on builds <50 you might find that the server user is forbidden from it though))
You can either use the UI to create a single `local` storage pointing to /data, or you can use docker exec to create
subdirectories if you need to split things up:
```bash
33212:~ localhome$ docker ps
CONTAINER ID        IMAGE                              COMMAND                  CREATED             STATUS              PORTS                    NAMES
4465d42c25f8        guardianmultimedia/pluto-core:45   "/opt/docker/bin/proj"   39 seconds ago      Up 37 seconds       0.0.0.0:9000->9000/tcp   testbuiltimage_plutocore_1
882b9369a726        postgres:9.6                       "docker-entrypoint.sh"   39 seconds ago      Up 37 seconds       0.0.0.0:5432->5432/tcp   testbuiltimage_database_1
$ docker exec -it testbuiltimage_plutocore_1 /bin/bash
demiourgos728@4465d42c25f8:/opt/docker$ mkdir /data/test
demiourgos728@4465d42c25f8:/opt/docker$ mkdir /data/templates
demiourgos728@4465d42c25f8:/opt/docker$ mkdir /data/projects
demiourgos728@4465d42c25f8:/opt/docker$ exit
```
Then use the UI to set up storages pointing to those locations as normal.

## Customising configuration

For some testing, just spinning it up is OK but sometimes you'll want to improve the default configuration, for example
to set a certificate for authenticating bearer tokens.
In this case, you should:
 - copy the `conf/` subdirectory from the repo root into `scripts/test_built_image/conf` 
 - remove the existing `application.conf` and rename `docker-application.conf` to `application.conf`
 - make any updates you want to the newly renamed `application.conf`
 - make any updates you need to `logback.xml` for logging
 - edit `docker-compose.yml` to create a bind-mount from `scripts/test_built_image/conf` to `/opt/docker/conf`:
 ```
     pluto-core:
       volumes:
          - projectlocker_app_data:/data
          - ./conf:/opt/docker/conf
 ```
 - run `docker-compose up` to start the stack. The entire application config directory should now be loaded from your
 host machine.