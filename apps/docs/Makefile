Nothing:
	@echo "No target provided. Stop"

.PHONY: build
build:
	yarn build

.PHONY: publish
publish:
	aws s3 sync build s3://starterkit.ga4gh.org

.PHONY: build-publish
build-publish: build publish
