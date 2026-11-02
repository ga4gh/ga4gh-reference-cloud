# Project Guide: GA4GH Reference Cloud Local Deployment

## Project Overview & Architecture

This repo serves to provide configuration for the local deployment context 

* **Tech Stack:** Caddy, Docker, Docker Compose, Ory Kratos & Hydra, GA4GH Refcloud

### Directory Layout

* `Caddyfile` - Caddy-based ingress controller
* `docker-compose.yml` - compose file for spinning up dependency services
* `ory/hydra` - config files for deployment of Ory Hydra 
* `ory/kratos` - config files for deployment of Ory Kratos

---

## Core Commands

### Development & Build

* Start dependency services: `docker-compose up -d`
* Stop dependency services: `docker-compose down`
* Start Caddy ingress controller: `caddy run --config Caddyfile`

### Code Quality & Validation

* TBD: Run linter
* TBD: Fix linting/formatting errors
* TBD: Run type check

### Testing

* TBD: Run all tests
* TBD: Run specific test file

## Things to Note:

* All browser-facing traffic goes through Caddy. Any new backend path, whether a Kratos endpoint or a core-api route, needs a handle block, or it falls through to Next.js and returns a 404 page.
* Docker containers reach host services through host.docker.internal, never the public hostname.
