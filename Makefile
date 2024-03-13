.PHONY: all set-env build-frontend build-backend localrestart

all: set-env build-frontend build-backend localrestart

set-env:
	@echo "Setting up environment"
	eval $(minikube docker-env)

build-frontend:
	cd frontend && yarn build

build-backend:
	sbt docker:publishLocal

localrestart:
	kubectl delete pod -l service=pluto-core