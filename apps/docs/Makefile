DOCKER_ORG := ga4gh
DOCKER_REPO_STATIC_BUILD := ga4gh-starter-kit-docs-static-build
DOCKER_REPO_NGINX_DEPLOY := ga4gh-starter-kit-docs-nginx-deploy
DOCKER_TAG := $(shell cat package.json | grep '"version"' | cut -d '"' -f 4)
DOCKER_IMG_STATIC_BUILD := ${DOCKER_ORG}/${DOCKER_REPO_STATIC_BUILD}:${DOCKER_TAG}
DOCKER_IMG_NGINX_DEPLOY := ${DOCKER_ORG}/${DOCKER_REPO_NGINX_DEPLOY}:${DOCKER_TAG}

.PHONY: Nothing
Nothing:
	@echo "No target provided. Stop"

.PHONY: build
build:
	@echo "Building the static contents using yarn..."
	yarn build

.PHONY: publish-prod
publish-prod:
	@echo "Publishing to AWS S3..."
	aws s3 sync build s3://starterkit.ga4gh.org

.PHONY: build-publish-prod
build-publish: build publish-prod

.PHONY: build-docker-static-build
build-docker-static-build:
	@echo "Building Docker image for static build..."
	docker build -t $(DOCKER_IMG_STATIC_BUILD) --build-arg VERSION=$(DOCKER_TAG) -f Dockerfile-static-build .

.PHONY: push-docker-static-build
push-docker-static-build:
	@echo "Pushing Docker image for static build..."
	docker image push $(DOCKER_IMG_STATIC_BUILD)

.PHONY: run-docker-static-build
run-docker-static-build:
	@echo "Running Docker container for static build..."
	docker run -d -v $(PWD)/build/:/app/build/ $(DOCKER_IMG_STATIC_BUILD)

.PHONY: build-docker-nginx-deploy
build-docker-nginx-deploy:
	@echo "Building Docker image for Nginx deployment..."
	docker build -t $(DOCKER_IMG_NGINX_DEPLOY) --build-arg VERSION=$(DOCKER_TAG) -f Dockerfile-nginx-deploy .

.PHONY: push-docker-nginx-deploy
push-docker-nginx-deploy:
	@echo "Pushing Docker image for Nginx deployment..."
	docker image push $(DOCKER_IMG_NGINX_DEPLOY)

.PHONY: run-docker-nginx-deploy
run-docker-nginx-deploy:
	@echo "Running Docker container for Nginx deployment..."
	docker run -d -p 8080:80 $(DOCKER_IMG_NGINX_DEPLOY)