# Android service-owned proxy traffic reporting (task271, 2026-10-30)

Production evidence for the dedicated mobile test code: version 2.11.51
Alpha-a847246-261029; uploaded 51,896 / downloaded 70,885 bytes total; two counter
records both sequence 1 at 07:47:23 and 07:54:01. User measured x-ui about
155.57/181.14 MB. Snapshot is read-only; no usage reset or inferred backfill.
The old reporting loop lived in MainActivity, normally waited 5–6 minutes, was
cancelled with Activity destruction, dropped the pending tail on stop and reset
IDs on start. A surviving VPN could therefore continue carrying unreported data.

The single accounting owner is now the background TunService/ClashService runtime.
MainActivity only updates UI and writes encrypted report configuration on startup,
import and before VPN start. No Activity network traffic writer remains. Report
configuration and durable ledger live as AES-GCM encrypted AtomicFiles under
noBackupFilesDir; Android Keystore keys, RSA-wrapped legacy key on API21/22.
Credential/config files and the legacy wrapped-key preference are excluded from
backup. No node/API/device credentials in source, logs, test outputs or notes.

A service module samples every second and sends active usage about every 30 seconds,
with frozen counter ID, sequence and cumulative payload across ambiguous failures,
Retry-After and exponential retry. Every sample and frozen request is persisted.
HTTP is bound to an available non-VPN underlying network; auth redirects disabled.
Only the exact managed profile is billable; old identities keep separate durable
counters through switching. Large deltas are chunked at 5 GiB, never discarded.
Accepted-after-limit advances its exact payload; rejected-before-limit does not.
The service persists a final sample before core reset and attempts a bounded final
send; failed/uncertain packets survive for later retry. Android force kill or
power loss can still lose bytes since the last successful one-second sample;
this is not an authoritative server-side billing replacement.

Byte counters now read proxy-only cumulative totals through the Go/C JNI bridge.
The packed UI traffic value keeps its existing all-traffic display semantics.
The pinned Android core remains a84724665eb7f989809abe463c05f5723bd24975.
`prepare-proxy-core.py` applies the same audited continuous IO accounting patch as
PC270, before every Go ABI build through a single Gradle dependency. The patch
classifies completed concrete proxy leaves/provider identity, excludes DIRECT
aliases/reject/DNS/internal dialer duplicate trackers, counts TCP/UDP/peek/fast IO,
and retains short closed requests. No per-connection periodic sum for billing.
Submodule source is deliberately patched at build time; do not commit the dirty
submodule or change its gitlink. Reverse apply only this patch to recover it.

Verification: `python3 scripts/test-service-traffic.py`, existing access-recovery
checks; `./gradlew service:testMetaDebugUnitTest` (five real ledger behavior tests,
including user's hundreds-of-MB tail, frozen retry, >5GiB, core epochs and rebase),
then Build Android APK on this isolated branch. APK/signing/device acceptance are
separate states. No version/tag, formal Release, NAS updater or install authorized.

Editable paths: app MainActivity/config writer; service/traffic ledger/store/reporter;
TrafficReportingModule; ConfigurationModule transition hooks; both VPN services;
core queryProxyTotal/C JNI byte bridge, build.gradle.kts build gate and scripts patch.
Runtime remains independent of the UI screen. Rebuild/reinstall is required.
Rollback source commit and rebuild; preserve server stats and encrypted pending
ledger. Never restore a production DB or send the old ambiguous Activity cache as
new proxy-only traffic. Windows270 package/production backend are unchanged.
