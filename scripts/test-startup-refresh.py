"""276 integration guards for real UI request path and nonblocking startup.
Python3 only; no devices, network, credentials or production config writes.
Executable policy tests run with app:testMetaDebugUnitTest in CI.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/github/kr328/clash/MainActivity.kt').read_text()
d=(r/'design/src/main/java/com/github/kr328/clash/design/MainDesign.kt').read_text()
l=(r/'design/src/main/res/layout/design_main.xml').read_text();ET.fromstring(l)
assert 'if (starting && request != Request.UpdateSubscription) return' in d
assert 'setOf(Request.ToggleStatus, Request.ImportByCode, Request.UpdateSubscription)' in d
assert 'subscriptionStatus' in l and 'subscriptionUpdating' in l
assert '@{!clashRunning &amp;&amp; activationCode != null ? View.VISIBLE : View.GONE}' in l
main=a[a.index('override suspend fun main()'):a.index('val ticker =')]
assert 'launch { runCatching { EndpointResolver.initialize(); refreshLineStatus(design) } }' in main
assert 'design.requestSubscriptionRefresh(force = true)' in main
assert 'runCatching { design.fetch() }' not in main
refresh=a[a.index('private fun MainDesign.requestSubscriptionRefresh'):a.index('private suspend fun MainDesign.importSubscriptionCode')]
assert 'shouldReuseSubscriptionRefresh(subscriptionRefreshCode, code, existing?.isActive == true)' in refresh
assert 'setSubscriptionStatus("正在更新配置' in refresh
assert 'else -> "配置更新失败，已保留原配置' in refresh
assert 'knownState = state' in a and 'val updateState = knownState ?: queryUpdateState(code)' in a
start=a[a.index('private suspend fun MainDesign.startClash('):a.index('val vpnRequest = startClashService()')]
assert 'requestSubscriptionRefresh(force = true)' in start and 'awaitStartupRefresh(it)' in start
assert 'withTimeoutOrNull(1_500)' not in start and 'KEY_ACCESS_RESTRICTION' in start
returned=a[a.index('Event.ActivityStart ->'):a.index('Event.ServiceRecreated,')]
assert 'requestSubscriptionRefresh' not in returned
assert 'openingRefreshRequested.compareAndSet(false, true)' in main
print('276 actual startup/request/status/coalescing/restriction integration guards passed')

# A slow refresh continues in background; failures stop before VPN start.
assert 'when (refresh?.let { awaitStartupRefresh(it) })' in start
assert 'false -> return' in start
assert '配置正在后台更新' in start
assert 'binding.starting = value' in d and '@string/main_processing' in l
assert '智能加速' not in l and '稳定连接' not in l
print('328 bounded startup wait, failure stop and visible busy-state integration passed')

# The first code is entered in the shared dialog; successful import starts the
# already validated profile without a second immediate subscription download.
dialog = a[a.index('private fun MainDesign.showCodeImportDialog()'):a.index('private fun MainDesign.showExpiredDialog()')]
assert 'access_code_input' not in l
assert 'activationCode == null ? @string/import_code_title : @string/switch_import_code' in l
assert 'if (savedActivationCode().isBlank()) design.showCodeImportDialog()' in a
assert 'if (importSubscriptionCode(code, onStatus = { status.text = it }))' in dialog
assert 'if (firstImport) startClash(refreshManagedProfile = false)' in dialog
assert 'refreshManagedProfile: Boolean = true' in start and 'if (refreshManagedProfile)' in start
assert '!isActivationStillValid(code)' in start and '!active.imported' in start
assert 'EditorInfo.IME_ACTION_DONE' in dialog and 'confirm.performClick()' in dialog
print('456 first-import button, dialog submission and startup checks passed')

managed = (r/'app/src/main/java/com/github/kr328/clash/ManagedProfile.kt').read_text()
assert 'secureManagedApiBase(apiBase), "ticket"' in managed
assert 'this@exchangeManagedImportTicket,secureBase,"exchange"' in managed
assert 'apiBase=secureBase,' in managed
assert 'apiBase = secureManagedApiBase(json.getString("api_base"))' in managed
print('456 secure managed API address survives first import and cached credentials')
