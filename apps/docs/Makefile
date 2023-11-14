DOCKER_ORG := ga4gh
DOCKER_REPO_STATIC_BUILD := ga4gh-starter-kit-docs-static-build
DOCKER_REPO_NGINX_DEPLOY := ga4gh-starter-kit-docs-nginx-deploy
DOCKER_TAG := $(shell cat package.json | grep '"version"' | cut -d '"' -f 4)
DOCKER_IMG_STATIC_BUILD := ${DOCKER_ORG}/${DOCKER_REPO_STATIC_BUILD}:${DOCKER_TAG}
DOCKER_IMG_NGINX_DEPLOY := ${DOCKER_ORG}/${DOCKER_REPO_NGINX_DEPLOY}:${DOCKER_TAG}
TEST_DOCKER_TAG := test-${DOCKER_TAG}
TEST_DOCKER_IMG_STATIC_BUILD := ${DOCKER_ORG}/${DOCKER_REPO_STATIC_BUILD}:${TEST_DOCKER_TAG}
TEST_DOCKER_IMG_NGINX_DEPLOY := ${DOCKER_ORG}/${DOCKER_REPO_NGINX_DEPLOY}:${TEST_DOCKER_TAG}

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
	docker run -d --name starter-kit-docs-static-build -v $(PWD)/build/:/app/build/ $(DOCKER_IMG_STATIC_BUILD)
	docker wait starter-kit-docs-static-build
	sleep 5

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

.PHONY: publish-docker-images
publish-docker-images: build-docker-static-build push-docker-static-build run-docker-static-build build-docker-nginx-deploy push-docker-nginx-deploy

.PHONY: build-docker-static-build-test
build-docker-static-build-test:
	@echo "Building Docker image for static build with test tag ..."
	docker build -t $(TEST_DOCKER_IMG_STATIC_BUILD) --build-arg VERSION=$(TEST_DOCKER_TAG) -f Dockerfile-static-build .

.PHONY: push-docker-static-build-test
push-docker-static-build-test:
	@echo "Pushing Docker image for static build with test tag ..."
	docker image push $(TEST_DOCKER_IMG_STATIC_BUILD)

.PHONY: run-docker-static-build-test
run-docker-static-build-test:
	@echo "Running Docker container for static build with test tag ..."
	docker run -d --name starter-kit-docs-static-build-test -v $(PWD)/build/:/app/build/ $(TEST_DOCKER_IMG_STATIC_BUILD)
	docker wait starter-kit-docs-static-build-test
	sleep 5

.PHONY: build-docker-nginx-deploy-test
build-docker-nginx-deploy-test:
	@echo "Building Docker image for Nginx deployment with test tag ..."
	docker build -t $(TEST_DOCKER_IMG_NGINX_DEPLOY) --build-arg VERSION=$(TEST_DOCKER_TAG) -f Dockerfile-nginx-deploy .

.PHONY: push-docker-nginx-deploy-test
push-docker-nginx-deploy-test:
	@echo "Pushing Docker image for Nginx deployment with test tag ..."
	docker image push $(TEST_DOCKER_IMG_NGINX_DEPLOY)

.PHONY: run-docker-nginx-deploy-test
run-docker-nginx-deploy-test:
	@echo "Running Docker container for Nginx deployment with test tag ..."
	docker run -d -p 8080:80 $(TEST_DOCKER_IMG_NGINX_DEPLOY)

.PHONY: publish-docker-images-test
publish-docker-images-test: build-docker-static-build-test push-docker-static-build-test run-docker-static-build-test build-docker-nginx-deploy-test push-docker-nginx-deploy-test
