# VoltPur implementation roadmap

VoltPur is being rebuilt as a maintainable Purpur fork. Features are not described as shipped until they are represented by an applied patch, compile successfully, and pass their validation checks.

## Working rules

1. Every functional change must be an applied paperweight patch under `purpur-server/minecraft-patches` or `purpur-server/paper-patches`.
2. Do not hand-author patch offsets or Git index hashes. Make the source change, then generate the patch through the project patch-rebuild task.
3. Each patch must be narrowly scoped, documented, and independently reversible.
4. Performance claims require a repeatable benchmark, a baseline, and published test conditions.
5. Changes that alter vanilla mechanics must be opt-in and have a safe default.

## Milestone 0 — build integrity

- [ ] Clone with Git and initialize all required upstream state.
- [ ] Run `./gradlew applyAllPatches` successfully from a clean checkout.
- [ ] Build `createMojmapBundlerJar` successfully.
- [ ] Confirm that a deliberately small test patch is applied to the resulting source tree.
- [ ] Add a CI workflow that performs the clean patch-apply and build checks.

## Milestone 1 — first real VoltPur feature

Implement one conservative, configurable server feature before attempting broad performance changes.

Requirements:

- Lives in the standard Purpur configuration system.
- Is disabled by default unless it is behavior-preserving.
- Includes a focused validation checklist.
- Is generated into an official patch after it compiles.

Candidate: a diagnostic startup report that clearly lists VoltPur version, enabled VoltPur modules, and configuration file location. This changes no gameplay behavior and gives administrators reliable support information.

## Definition of done

A VoltPur feature is complete only when its patch applies on a clean checkout, the server builds, startup is tested, configuration is documented, and any performance statement is supported by reproducible measurements.
