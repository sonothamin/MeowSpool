# MeowSpool (Android PrintService for cat thermal printers)
Open in Android Studio (it generates the Gradle wrapper), run on a device, then:
1. Open the app -> Scan -> tap your printer to save it.
2. Settings -> Connected devices -> Connection preferences -> Printing -> enable "MeowSpool".
3. Print from any app (Share -> Print, Chrome, Photos, Files...) and pick the printer.
Protocol: GB01/02/03-style (service AE30, write AE01, notify AE02), 384 dots/line.

## Print server (HTTP API + web page)
Menu → Print server. Optional token auth, local-only or network mode, QR code on demand. For reliable background use, disable battery optimisation for MeowSpool (the first-run flow asks). API reference: [docs/API.md](docs/API.md).
