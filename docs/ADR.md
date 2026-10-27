# Architectural Decision Record (ADR)

## 2026-06-05
  GA4GH Tech Team decided to bundle multiple GA4GH APIs into a single monolithic app (`refcloud-api`). This was decided primarily for faster development and to avoid unnecessary overhead (i.e. "microservice premium") in the v1 phase, as accelerating our "time to market" is currently our highest priority. We will explore splitting out APIs into multiple microservices once performance and scale become greater concerns.
