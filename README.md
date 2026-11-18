# Momentum

Momentum is an Android-first browser project based on Chromium (originally forked from Ultimatum) with desktop-grade WebExtensions support on Android and experimental content-addressing (`hash://`, `signed://`, `related://`) capabilities.

### Tested extensions

- [uBlock Origin](https://addons.opera.com/en/extensions/details/ublock/)
- [uBlock Origin Lite](https://chromewebstore.google.com/detail/ublock-origin-lite/ddkjiahejlhfcafbddmgiahcphecmpfh)
- [Tampermonkey](https://chromewebstore.google.com/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo)
- [Violentmonkey](https://chromewebstore.google.com/detail/violentmonkey/jinjaccalgkegednnccohejagnlnfdag)
- [Browsec VPN](https://chromewebstore.google.com/detail/browsec-vpn-%D0%B1%D0%B5%D1%81%D0%BF%D0%BB%D0%B0%D1%82%D0%BD%D1%8B%D0%B9-%D0%B2%D0%BF/omghfjlpggmjjaagoclmmobgdodcjboh)
- [MetaMask](https://chromewebstore.google.com/detail/metamask/nkbihfbeogaeaoehlefnkodbefgpgknn)
- [Midnight Lizard](https://chromewebstore.google.com/detail/midnight-lizard/pbnndmlekkboofhnbonilimejonapojg)
- [ClearURLs](https://chromewebstore.google.com/detail/clearurls/lckanjgmijmafbedllaakclkaicjfmnk)
- [BookmarkHub](https://chromewebstore.google.com/detail/bookmarkhub-%D1%81%D0%B8%D0%BD%D1%85%D1%80%D0%BE%D0%BD%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F/fohimdklhhcpcnpmmichieidclgfdmol)

### How to build & package APKs

Momentum includes build, versioning, packaging, and verification automation under [`scripts/`](scripts/README.md) with staged outputs in [`bundled-apk/`](bundled-apk/README.md):

```bash
# Bump version
./scripts/bump-version.sh patch

# Build debug / release APKs into ./bundled-apk/
./scripts/build-debug.sh
./scripts/build-release.sh

# Package release bundle, manifest, and SHA256SUMS.txt
./scripts/package-release.sh

# Verify scripts, signatures, alignment, and checksums
./scripts/verify.sh
```

For full Chromium GN builds, configure `args.gn` with:

```
target_os = "android"
target_cpu = "arm64"

is_debug = false
is_component_build = false
symbol_level = 0
blink_symbol_level = 0
v8_symbol_level = 0
ffmpeg_branding = "Chrome"
proprietary_codecs = true

enable_desktop_android_extensions = true
enable_guest_view = true
enable_platform_apps = false
```

See the [Chromium Linux build instructions](https://chromium.googlesource.com/chromium/src/+/main/docs/linux/build_instructions.md), [Android build instructions](https://chromium.googlesource.com/chromium/src/+/HEAD/docs/android_build_instructions.md#installing-and-running-chromium-on-a-device), and [Chrome DevTools remote debugging](https://developer.chrome.com/docs/devtools/remote-debugging) for upstream setup details.

### WebExtensions Support Status

Installation from extension stores, unpacked extensions, and `.crx` files (`"Content-Type": "application/x-chrome-extension"`) is supported, including installing, deleting, and toggling extensions.

#### Known Limitations

- There is no modal window when an extension requests increased permissions (such as uBlock Origin Lite); exercise caution when installing extensions from third-party sites.

Legend:

- ✅ working (may differ slightly from desktop)
- ❌ not working / undefined from JS
- ❌ ✅ present (not undefined) but currently a no-op

### Full Support

- ✅ `chrome.cookies`
- ✅ `chrome.proxy`
- ✅ `chrome.storage`
- ✅ `chrome.contextMenus` (except text selection)
- ✅ `chrome.webRequest`

#### Private APIs (used internally)

- ✅ `chrome.developerPrivate`
- ✅ `chrome.guestViewInternal`
- ✅ `chrome.settingsPrivate`
- ✅ `chrome.webstorePrivate`

#### `chrome.tabs` API

##### Properties

- ✅ `MAX_CAPTURE_VISIBLE_TAB_CALLS_PER_SECOND`
- ✅ `TAB_ID_NONE`
- ✅ `TAB_INDEX_NONE`

##### Methods

- ✅ `captureVisibleTab`
- ✅ `connect`
- ✅ `create`
- ✅ `detectLanguage`
- ❌ `discard`
- ❌ `duplicate`
- ✅ `get`
- ✅ `getCurrent`
- ❌ `getZoom`
- ❌ `getZoomSettings`
- ✅ `goBack`
- ✅ `goForward`
- ✅ `group`
- ✅ `highlight`
- ❌ `move`
- ✅ `query`
- ❌ `reload`
- ✅ `remove`
- ✅ `sendMessage`
- ❌ `setZoom`
- ❌ `setZoomSettings`
- ✅ `ungroup`
- ✅ `update`
- ✅ `executeScript`
- ✅ `insertCSS`
- ✅ `removeCSS`

##### Events

- ✅ `onActivated`
- ❌ `onAttached`
- ✅ `onCreated`
- ❌ `onDetached`
- ❌ `onHighlighted`
- ✅ `onMoved`
- ✅ `onRemoved`
- ❌ `onReplaced`
- ✅ `onUpdated`
- ❌ `onZoomChange`

#### `chrome.windows` API

On Android, regular and incognito sessions are exposed as two windows (`WINDOW_ID_CURRENT` / `WINDOW_ID_NONE`).

##### Methods

- ❌ ✅ `create` (no-op, returns current window)
- ✅ `get`
- ✅ `getAll`
- ✅ `getCurrent`
- ✅ `getLastFocused` (returns current window)
- ✅ `remove` (closing the regular window can cause a crash)
- ❌ ✅ `update` (no-op, returns current window)

##### Events

- ❌ `onBoundsChanged`
- ❌ `onCreated`
- ❌ `onFocusChanged`
- ❌ `onRemoved`

#### Added (Pending Full Testing)

- `chrome.history`
- `chrome.scripting`
- `chrome.declarativeNetRequest`
- `chrome.i18n`
- `chrome.idle`
- `chrome.metricsPrivate`
- `chrome.management`
- `chrome.offscreen`
- `chrome.runtime` (messaging works)

### Content Addressing (Desktop / Experimental)

Momentum retains content-addressing support for decentralized content verification via `hash://`, `signed://`, and `related://` schemes:

- **`hash://sha256/...`**: Polls configured agent nodes sequentially and verifies the SHA-256 digest of the returned payload against the requested URN.
- **`signed://secp256r1.sha256/...`**: Queries all configured agents, validates cryptographic signatures, and selects the valid response with the highest `nonce` version before fetching the referenced content hash.
- **`related://`**: Fetches signed messages matching a `relatedTo` URL/URN via `fetch` (GET only).
