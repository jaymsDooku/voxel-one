# Hub immutable native release correction

Base HEAD: b098d7b5af2fcfc18ca36a9b67768c167f261c74
Review base: 403033b9f53ce66063982879c6e2daff684a427b
PR: https://github.com/jaymsDooku/voxel-one/pull/72

Native catalog entries now resolve their approved playtest-N immutable manifest.
Fresh installs do not depend on releases/latest remaining compatible. Both Hub
Play and CLI experience selection use this path. Legacy latest behavior remains.
Parser/publisher reject mutable, unsafe or unsupported native tags. Existing
source allowlist, hash/size checks, immutable shared cache and fallback remain.

Validation: Maven selected suite/package21 passed; publisher1 and bundle3 passed.
Fresh A/incompatible-latest B integration passed with zero latest/B requests;
repeat install reuses shared artifact. Actual public immutable565 fresh-profile
Hub install and City window passed with both system and packaged runtimes.
Fresh progress capture inspected; it does not claim completed gameplay footage.
Package1.0.4 built; launcher bytes match final Maven JAR.
See dashboard/evidence/hub-immutable-validation.json and hub-immutable-cli.json.

Audit: no deleted files; unrelated dashboard entries, website and normal release
workflow preserved. No Git recovery pending. All helper tasks resolved.
No developer commit, push, release, publication or deployment performed.

Controller handoff: synchronize latest master, commit/publish reviewed source,
then run exact-head Hub platform checks. Run prepared read-only publication
workflow on that exact source and regenerate publication bundle after commit.
Publish only through controller fencing: immutable preview assets first, then
experience-catalog metadata using its expected-head CAS. Current catalog/shell/
preview endpoints404. Verify delivery and two distinct published reviewed shell
generations. Do not relabel test fixtures as published generations.
Prior b098d7b Win/Mac reports still fail native GLFW creation on all three hosts;
MSI/shortcuts, updater, UI and login checks passed. Physical GPU/DPI and current
source platform qualification remain incomplete. Keep stable rollout gated.
No new owner answer is needed. All edits and media remain preserved locally.
