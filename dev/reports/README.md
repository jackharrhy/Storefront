# Local Paper refresh measurements

Measured on 2026-09-12 using the Compose development server: Paper 26.2 build 123, Temurin 26.0.2, 2 GiB heap, and a Xeon W-2133 (6 cores / 12 threads). The host also runs other services; this is a local comparison, not a production capacity guarantee.

## What changed in the JVM code

The plugin is still Kotlin. The earlier modernization upgraded Java 8 to 26, Paper 1.15.2 to 26.2, Kotlin to 2.4, and Javalin to 7, while preserving the SQLite table and saved inventory JSON. It also fixed command registration, UUID ownership checks, stable storefront IDs, and completion of failed map requests.

[Paper's scheduling guidance](https://docs.papermc.io/paper/dev/scheduler/) calls for world access on the server thread and blocking database work off it.

The original refresher scheduled a task per chest and then read the live inventory asynchronously. That inventory access was unsafe. Commit `7211279` moved inventory access onto the server thread, but also did all serialization and database updates in one synchronous loop. The baseline below exposed the resulting tick stalls. The earlier database/HTTP unit tests did not test thread performance.

The current refresh:

1. Reads target rows on one database worker.
2. Captures live inventories on the server thread, with a default limit of eight chests or approximately 2 ms per tick. A single capture cannot be interrupted; this is a soft budget.
3. Skips unloaded chunks, preserving their last saved contents. Map HTTP requests also avoid loading chunks and resolve Bukkit worlds on the server thread.
4. Sends changed contents to the worker for transactional SQLite writes. WAL lets HTTP readers continue while writes occur; a location index speeds up interactive lookups.
5. Checks the row's modification token before updating or deleting it, protecting newer player edits and replacement listings from stale captures. Tokens are now monotonically increasing millisecond values; existing second-based values still work.
6. Coalesces overlapping requests and exposes completion/timing through `/storefrontrefreshstatus`. `/storefrontforceupdate` acknowledges queuing, not completion.

Sign registration, owner checks, manual sign updates, and sign removal still make small database calls on the server thread. This work addresses the bulk refresh bottleneck; it does not claim all database work is asynchronous.

## Workload and results

Mineflayer registered each shop by right-clicking its sign. RCON prepared the fixtures: 27 occupied slots per chest, each containing 64 diamonds, in a grid starting at `(1024, 65, 1024)`. One separate demo shop remained in the database. Fixture chunks were kept loaded during measurement. Four HTTP readers repeatedly fetched the full storefront list, with a 100 ms pause after each response.

| Run | Shops | Sweeps | Final 10 s tick average / maximum | API p95 | HTTP errors |
| --- | ---: | ---: | ---: | ---: | ---: |
| [Baseline](baseline-100.json), synchronous refresh | 100 | 10 | 20.0 / 524.2 ms | 58.7 ms | 0 |
| [Batched refresh](optimized-100.json), unchanged inventories | 100 | 10 | 4.4 / 9.7 ms | 45.6 ms | 0 |
| [Batched refresh with writes](dirty-100.json) | 100 | 3 | 5.2 / 29.0 ms | 46.2 ms | 0 |
| [Larger workload](scale-500.json), unchanged inventories | 500 | 10 | 4.3 / 16.8 ms | 128.3 ms | 0 |
| [Larger workload with writes](dirty-scale-500.json) | 500 | 3 | 5.2 / 26.5 ms | 111.8 ms | 0 |

The dirty run changed slot zero in every chest before each sweep and verified the changed counts through the API. All three sweeps persisted 100 changes. It restored the original counts afterward.

The baseline command took a median 460 ms because it performed the whole sweep before returning. The new command returns immediately; its response latency must not be compared as sweep duration. The final 100-chest sweep took 1.76 seconds spread across ticks, with a maximum capture batch of 3.84 ms. The dirty sweeps took 1.67–2.31 seconds, with maximum capture batches of 3.16–3.31 ms. Smoother ticks trade off against slower completion of a full sweep.

At 500 shops (13,500 occupied slots), all ten sweeps completed in 6.77–8.35 seconds. The maximum capture batch across those sweeps was 15.24 ms. All 500 fixture chests were accounted for in loaded chunks; the extra demo listing was skipped. The final one-minute tick maximum was 34.2 ms. The API served 1,622 requests with no errors; response latency increased because each request returns all shops. A final warmed-up dirty run on the final plugin build persisted 500 changes in each of three sweeps, verified every count through the API, and waited for restoration of all counts to 64 before unloading fixtures. Those sweeps took 7.66–7.91 seconds; the final one-minute tick maximum was 37.4 ms, with no HTTP errors.

These tick numbers are Paper's rolling windows, not per-sweep percentiles. The final 10-second window reflects warmed-up operation. The first cold capture in the optimized run still took 121 ms, and its final one-minute tick window included a 446 ms maximum around startup/initial activity. The 2 ms budget is not a hard upper bound and does not eliminate JVM warm-up, chunk generation, GC, or other server work. Raw reports retain all windows and every refresh sample.

After restarting the final build with no players connected, a refresh skipped all 501 unloaded listings and preserved the API data exactly. A second request during that sweep was coalesced. A map request for an unloaded fixture returned 404 without loading the chunk.

## Reproduce

From the repository root:

```sh
docker compose up --build -d --wait paper storefront-frontend
STOREFRONTS=100 RUN_LABEL=local docker compose --profile stress run --build --rm stress
STOREFRONTS=100 SETUP=false DIRTY=true RUN_LABEL=dirty docker compose --profile stress run --rm stress
STOREFRONTS=500 RUN_LABEL=scale docker compose --profile stress run --rm stress
```

`SETUP=false` requires the same fixture count already present. For a smaller run after a larger one, use a fresh development volume or remove the extra fixtures/listings first. The historical baseline was collected against `7211279`, before the harness gained asynchronous completion polling; that revision has no refresh-status command.

The harness supports up to 1,000 shops and has a 15-minute timeout. It leaves fixtures in place for inspection and removes the chunk force-load tickets when it exits normally. If it is forcibly killed, release them with `docker compose exec paper rcon-cli "forceload remove 1023 1023 1125 1185"`. Never point this fixture generator at a world you want to preserve.

This is one connected bot plus four HTTP readers, not a simulation of hundreds of simultaneous players. Full chests of diamonds exercise serialization volume but not every expensive item metadata type. Real server profiling with representative inventories remains useful before choosing production limits.
