# MeowSpool

An Android **print service** for Bluetooth **cat thermal printers** (GB01 / GB02 / GB03-style, 58 mm paper). Print from any app through Android's normal print dialog, print photos and PDFs directly, or run a small **print server** so other devices and scripts can print through your phone.

## Features

- **System print service**: appears in Share → Print, Chrome, Photos, Files and other apps.
- **Print a file**: pick a photo or PDF, then adjust size, margins, rotation, brightness, contrast, invert, dithering, darkness, copies and page range with a live preview.
- **Print server** (optional): HTTP API and a browser upload page, local-only or over the network, with optional token auth and a QR code on demand. See [docs/API.md](docs/API.md).
- **History**: every job is kept with a preview. Open one to see its details, print it again, share it or save it as an image.
- **Paper presets** (continuous roll, 100/50/30 mm labels, or your own), tear-off lines, feed and retract controls, test page with ruler and grey ramp.
- **Live printer status**: paper, cover, overheat and battery flags, with automatic reconnect.
- **Friendly first run**: guided setup for connecting the printer and allowing background use.
- Light/dark and dynamic colour themes; a built-in debug log for bug reports.

## Setup

1. Install the APK (build it below or grab it from the GitHub Actions artifacts).
2. Open MeowSpool and follow the first-run flow: switch the printer on, pick it from the list, allow background use.
3. Turn on **MeowSpool** in Android's print settings (Settings → Connected devices → Connection preferences → Printing) so other apps can use it.
4. Print from any app and choose your printer, or use **Print a file** inside MeowSpool.

Requires Android 8.0 (API 26) or newer and Bluetooth LE.

## Print server

Menu → **Print server**.

- **Web page and/or API**, switched independently.
- **Network access** on (other devices on your Wi-Fi) or off (this phone only).
- **Access token** (optional) for every request; **Show QR** opens the address, including the token, on another device.
- Runs as a foreground service with wake and Wi-Fi locks. **Disable battery optimisation** for MeowSpool or Android may stop it in the background; the app warns you and the first-run flow asks.

Quick example:

```sh
curl -H "Authorization: Bearer $TOKEN" --data-binary @photo.jpg \
  "http://192.168.1.20:8631/api/print?darkness=70&dither=smooth"
```

Full reference (endpoints, parameters, errors, security): **[docs/API.md](docs/API.md)**.

## Print settings

| Setting | What it does |
|---|---|
| Darkness | Print heat. Darker uses more battery and can overheat on long jobs. |
| Dithering | Smooth (photos), Sharp (text/QR), Pattern. |
| Feed after print | Extra paper for a clean tear. |
| Margins | Extra space inside the 48 mm printable width. |
| Tear-off line | Solid or dashed line before/after a job. |
| Paper | Presets and custom lengths; the head is 48 mm wide on 58 mm paper. |

## Build

Open in Android Studio (it generates the Gradle wrapper) and run, or from the command line:

```sh
gradle :app:assembleDebug
```

GitHub Actions builds a debug APK on every push (artifact `MeowSpoolService-debug-apk`).

## Protocol

GB01/02/03-style BLE: service `AE30`, write characteristic `AE01`, notify `AE02`, 384 dots per line at 203 dpi, 1-bit rows.

## Project layout

```
app/src/main/java/dev/meowspool/
  CatPrinterLink / CatProtocol   BLE link and printer protocol
  PrinterManager                 persistent connection, status polling
  PrintEngine                    dithered rows → printer, job history
  MeowSpoolService               Android PrintService
  DirectPrint                    file import and page composition
  HttpServer / PrintApi          print server: HTTP and routes
  ServerService                  foreground service hosting the server
  History / Prefs / Power        job log, settings, battery-exemption helper
  ui/                            Jetpack Compose screens
docs/API.md                      print server API reference
```

## Contributing / issues

Bug reports welcome: copy the debug log from the app first, then open an issue at <https://github.com/sonothamin/MeowSpool/issues>.
