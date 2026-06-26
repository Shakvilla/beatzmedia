# BeatzClik API — local docs & Postman

What's currently built and how to exercise it against the local Docker stack.

## Run the stack

```bash
cd backend
./mvnw package -DskipTests
docker compose up -d --build
```

> **Port note:** host `8080` is often taken by another local app, so
> `docker-compose.override.yml` maps the API to **host `8081`** (container stays 8080).
> If 8080 is free on your machine you can delete the override and use 8080.

Wait for readiness:

```bash
curl -s http://localhost:8081/q/health/ready
```

## API docs page

- **Swagger UI:** http://localhost:8081/q/swagger-ui
- **OpenAPI spec (JSON):** http://localhost:8081/q/openapi?format=json
- **OpenAPI spec (YAML):** http://localhost:8081/q/openapi

Swagger UI is bundled into the packaged jar via `quarkus.swagger-ui.always-include=true`
(it is disabled in the `prod` profile).

## Postman collection

Import [`BeatzClik.postman_collection.json`](BeatzClik.postman_collection.json) into Postman.

- `baseUrl` defaults to `http://localhost:8081`.
- Run **Identity → Auth → Signup** (or **Login**) first. The response JWT is captured
  automatically into the `{{token}}` collection variable and reused as a bearer token by
  every other request.
- Catalog read requests are pre-filled with real seed IDs from `R__seed_dev_data.sql`
  (`black-sherif`, `iron-boy`, `last-last`, `vibes-from-the-233`), so they return 200 out of the box.

Regenerate after adding endpoints:

```bash
curl -s "http://localhost:8081/q/openapi?format=json" -o /tmp/openapi.json
# then re-run the generator used to produce this file
```

## Endpoints built so far

| Area | Method | Path |
|------|--------|------|
| Identity | POST | `/v1/auth/signup` |
| Identity | POST | `/v1/auth/login` |
| Identity | POST | `/v1/auth/logout` |
| Catalog | GET | `/v1/artists/{id}` |
| Catalog | GET | `/v1/artists/{id}/tracks` |
| Catalog | GET | `/v1/artists/{id}/albums` |
| Catalog | GET | `/v1/artists/{id}/shows` |
| Catalog | GET | `/v1/albums/{id}` |
| Catalog | GET | `/v1/tracks/{id}` |
| Catalog | GET | `/v1/tracks/{id}/lyrics` |
| Catalog | GET | `/v1/playlists/{id}` |
