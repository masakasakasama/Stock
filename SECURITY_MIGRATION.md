# Stock signing-key incident and deployment

The old `app/keystore/shared-debug.jks` is publicly readable and identical to Home's committed key. Its signing password was public. All APKs signed with that key must be considered forgeable. Git history and previously downloaded copies cannot be revoked.

Before merging:
1. Back up existing watchlists/holdings and any app-local data.
2. Generate a NEW keystore offline. While Home's Stock read permission uses `signature`, both applications must be built with the SAME new key. Separate app keys require an explicit permission redesign.
3. Set Actions secrets `STOCK_SIGNING_KEYSTORE_B64`, `STOCK_SIGNING_STORE_PASSWORD`, `STOCK_SIGNING_KEY_ALIAS`, `STOCK_SIGNING_KEY_PASSWORD`. None can be checked into Git.
4. Test both new signed apps on a spare device. Upgrading across different signing certs is normally rejected; plan backup, uninstall legacy, reinstall, import data.
5. The published file name stays `stock-widget-debug.apk` to preserve the existing update URL, but its content is a signed **release** build. Rename in a coordinated future app update.
6. Replace old release assets only when the installation migration is confirmed. The workflow refuses releases without all secrets.

Further hardening: validate APK checksum on-device and migrate updater trust to an independently pinned update-signature public key. A release-side SHA256 by itself is not protection against a compromised GitHub publisher.
