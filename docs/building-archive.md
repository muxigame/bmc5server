# Building Archive 0.5.2

Adds the locally developed building collaboration mod to the existing checksum-locked external downloads. Both client and server use the same JAR (Minecraft 1.21.1, NeoForge 21.1.250, Java 21). Existing baseline archives and CustomNPCs entries are unchanged.

Release: https://github.com/muxigame/bmc5server/releases/tag/building-archive-0.5.2-20261025

The release includes the runtime JAR, sources JAR, and a small handoff ZIP containing existing source/build scripts, corrected collection/ZIP scripts, and per-file hashes. It excludes Minecraft runtimes, dependencies, worlds, logs and caches. The source is our existing local snapshot, without an invented source commit. Older README sections inside the snapshot predate 0.5.2.

Use the normal setup command to download and verify the new external JAR. Back up unique building/world data first. Remove any manually installed older Building Archive JAR before installation; do not load two versions. Stop and inspect unknown existing files rather than overwrite them.

Rollback: revert this change and remove only building_archive-0.5.2.jar from the runtime mods folder; preserve building archives/world data. Deployment/restarts require separate coordination.

Validation: existing project build passed on 2026-10-25 (cached tasks, test NO-SOURCE). Historical 0.5.2 report records 34 passing GameTests on 2026-10-21. Full-pack setup/verify/smoke was not rerun for this packaging-only change; no running game or shared test environment was modified. See building-archive-0.5.2.json for exact sizes/hashes.
