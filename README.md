# Zephyr for Android

A mihomo proxy client for Android, written to match the Windows build of
Zephyr: the same blue-on-paper layout, the same seven-section structure, the
same way of driving the core.

It is not a fork of any existing client. The whole app is the files in this
repository, and the whole native surface is `core/zephyr.go`, about 200 lines.

## What it does, and what it does not

The app does three things:

1. Downloads the subscription from the address you typed.
2. Hands the tunnel's file descriptor to mihomo and keeps the process alive.
3. Reads the core's status back over its REST API on loopback and draws it.

It has no in-app updater, no analytics, no crash reporting, no cloud service,
no subscription-conversion service, and no remote scripting. The only request
it makes to the public internet is the subscription fetch, and that goes to the
address you entered and nowhere else.

Permissions requested, in full:

| Permission | Why |
| --- | --- |
| `INTERNET` | Fetch the subscription; the core dials proxy servers |
| `ACCESS_NETWORK_STATE` | Notice when the network changes |
| `FOREGROUND_SERVICE` | Keep the tunnel alive while the screen is off |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for the above |
| `POST_NOTIFICATIONS` | Show the ongoing tunnel notification |

No location, camera, storage, contacts, installed-app list, or package install.
Subscriptions and settings live in the app's private directory and are excluded
from cloud backup and device transfer.

## How it is put together

```
core/zephyr.go        the only Go: start, stop, socket protection
app/.../vpn           VpnService: opens the TUN, owns the notification
app/.../core          config merge, REST client, subscription fetch
app/.../ui            Compose screens, hand-drawn icons, no icon library
```

mihomo upstream accepts a file descriptor for its TUN device
(`listener/config.Tun.FileDescriptor`) and exposes a socket hook
(`component/dialer.DefaultSocketHook`) that upstream documents as being for
Android clients. Those two seams are all the bridge uses; there are no patches
to the core.

Everything else is read back over mihomo's REST API on `127.0.0.1`, guarded by
a secret generated on first launch, which is the same arrangement the Windows
build uses.

## Building

There is no local toolchain requirement: GitHub Actions builds both halves.

- `core` job: Go 1.27 plus `gomobile bind` produces `app/libs/zephyrcore.aar`
  containing mihomo v1.19.31 and the bridge, for arm64.
- `apk` job: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.20 produce the APK.

Dependencies come from Google's Maven and Maven Central only. Third-party
GitHub Actions are pinned by commit hash.

To sign releases, set four repository secrets: `ZEPHYR_KEYSTORE_BASE64`
(a base64 PKCS#12 keystore), `ZEPHYR_KEYSTORE_PASSWORD`, `ZEPHYR_KEY_ALIAS`,
`ZEPHYR_KEY_PASSWORD`. Without them the build still succeeds and produces a
debug-signed APK.

After the first successful `core` job, download the `go-pins` artifact and
commit `core/go.mod` and `core/go.sum`. That pins every transitive Go
dependency by hash.

## Licence

The bridge links mihomo, which is GPL-3.0, so the combined work is GPL-3.0.
mihomo is by MetaCubeX. The Android app in this repository is original work.
