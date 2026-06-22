---
name: scaffold-module
description: Create the hexagonal package skeleton for a new BeatzClik bounded context under org.shakvilla.beatzmedia.<module> using new-module.sh. Use when starting a module that has no package tree yet, or when a WU needs an absent layer scaffolded.
allowed-tools: Bash, Read, Glob
---

# Scaffold a hexagonal module

Create the fixed package layout for a bounded context so subsequent WUs drop code into known places.
The layout and the dependency rule come from `00-system-architecture.md §4` and `01-conventions §1`.

## Procedure
1. Confirm the module name (lowercase, e.g. `identity`, `catalog`, `payments`) and its ADD at `backend/docs/architecture/<module>.md`. Read the ADD's port list so the skeleton matches it.
2. Check it does not already exist: `find backend/src/main/java/org/shakvilla/beatzmedia/<module>`.
3. Run the scaffolder:
   ```bash
   backend/scripts/new-module.sh <module>
   ```
   It creates, under `org.shakvilla.beatzmedia.<module>`:
   - `domain/` — aggregates, value objects, domain events, framework-free exceptions
   - `application/port/in/` — input ports (use cases / commands)
   - `application/port/out/` — output ports (repositories, gateways, `Clock`, `IdGenerator`, `AuditWriter`)
   - `application/` — service impls (`@Transactional`) wiring ports
   - `adapter/in/rest/` — Quarkus REST resources + DTO records
   - `adapter/in/job/` — scheduled jobs / message handlers
   - `adapter/out/persistence/` — JPA entities + mappers + repository impls (owns only this module's tables)
   - `adapter/out/integration/` — external gateways (payment provider, hashing, JWT, media)
4. If the script is unavailable, create the directories above by hand with the same names.

## The dependency rule (ArchUnit-enforced — never violate)
- `adapters → application → domain`. **Domain imports no framework** (no Jakarta/Quarkus/Hibernate annotations on domain types; use JPA entities/mappers in the persistence adapter).
- **Application imports only domain** (+ the kernel `org.shakvilla.beatzmedia.platform`).
- **Inbound and outbound adapters never import each other.**
- A module reads/writes **only its own tables**; cross-module data comes from calling the owning module's input port or from ids/snapshots on domain events. **No cross-module foreign keys.**
- Shared primitives (`Money`, typed ids, `Page<T>`, `ApiError`/`ErrorCode`, `Clock`, `IdGenerator`, `DomainEvent`, `PlatformSettings`) live in the `platform` kernel and may be imported by any module's domain.

## After scaffolding
Update the module ADD if the realized layout differs, then proceed with implement-work-unit for the WU.
Do not add migrations here — that is create-flyway-migration's job.
