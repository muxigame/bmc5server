# CustomNPCs recovered workspace

- Read README.md and docs/MIGRATION.md first. This is recovered third-party code, not verified upstream source.
- Preserve original/ and reference/ byte-for-byte. Never edit their manifests to hide changes.
- Edit src/ only for intentional changes; document each change in patches/ and keep behavior fixes separate from compilation restoration.
- Preserve package names, public API, serialized keys and numeric IDs until a deliberate compatibility migration is specified.
- Do not install or publish a replacement JAR merely because compilation passes. Use isolated runtime regression tests.
- Source generation must never overwrite hand-edited working files. Generated analysis is not a substitute for runtime evidence.
- The public server repo tracks recovery tooling; this workspace's third-party source, resources and dependencies remain local. No remote is configured for this workspace.
