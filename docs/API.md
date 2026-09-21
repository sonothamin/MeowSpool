# MeowSpool print server: API reference

MeowSpool can run an HTTP server on your phone so other devices, scripts and browsers can print to your cat printer.

- [Getting started](#getting-started)
- [Authentication](#authentication)
- [Conventions](#conventions)
- [Endpoints](#endpoints)
- [Print parameters](#print-parameters)
- [Errors](#errors)
- [Examples](#examples)
- [Security notes](#security-notes)
- [Troubleshooting](#troubleshooting)

## Getting started

1. Save a printer in the app (Devices) and make sure it is selected.
2. Open the menu → **Print server** and switch **Run print server** on.
3. Turn off battery optimisation for MeowSpool when asked, otherwise Android may stop the server when the screen is off.
4. Use the address shown on that screen, e.g. `http://192.168.1.20:8631`. Tap **Show QR** to open it on another device by scanning.

Settings on the Print server screen:

| Setting | Meaning |
|---|---|
| Network access | On: reachable from other devices on your Wi-Fi (binds `0.0.0.0`). Off: only apps on this phone (`127.0.0.1`). |
| Port | Default `8631`, range 1024–65535. |
| Require access token | Off by default. On: every API request must carry the token. |
| Web page | Serves a simple upload page at `/`. |
| API | Enables the `/api/...` endpoints below. The web page and API can be turned on separately. |

The server runs as a foreground service (you'll see a notification with a **Stop** button) and keeps the printer connection open while it runs. It is not started automatically after a reboot; open the app once to bring it back.

## Authentication

Authentication is optional. When **Require access token** is on, send the token (shown in the app) in one of these ways:

```
Authorization: Bearer <token>
X-API-Key: <token>
?token=<token>            (query string; visible in logs, prefer a header)
```

Requests without a valid token get `401`. The web page at `/` is always public; it asks for the token itself. The QR code contains the token in the URL fragment (`#t=...`), which browsers never send to the server.

## Conventions

- Base URL: `http://<phone-ip>:<port>`
- Responses are JSON (`Content-Type: application/json`) with an `ok` boolean; failures also carry `error`.
- Requests are handled one connection at a time per request (no keep-alive). Print requests **block until the printer has finished**, so use a generous client timeout (long jobs can take minutes).
- Maximum request body: 30 MB. Maximum job length: about 40,000 dot rows (≈ 5 m of paper).
- Concurrent print jobs are queued: the printer link is used by one job at a time.
- CORS: with a token required, all origins are allowed (`Access-Control-Allow-Origin: *`) so browser apps can call the API. Without a token, cross-origin browser POSTs are refused (`403`).

## Endpoints

### `GET /api`
Lists endpoints and print parameters.

### `GET /api/status`
Printers, the selected printer's connection state and hardware flags.

```json
{
  "ok": true,
  "printers": [{ "address": "AA:BB:CC:DD:EE:FF", "name": "GB03" }],
  "paper": "58 mm roll (continuous)",
  "selected": "AA:BB:CC:DD:EE:FF",
  "connection": "connected",
  "printing": false,
  "error": null,
  "status": {
    "outOfPaper": false, "coverOpen": false, "overheat": false,
    "lowBattery": false, "busy": false, "problems": []
  }
}
```

- `connection`: `idle`, `connecting`, `connected` or `error`.
- `selected` is `null` (and the printer fields are omitted) when no printer is selected.
- `status` may be missing until the printer has answered its first status request.

### `POST /api/print`
Prints an image (JPEG, PNG, WebP, GIF, BMP…) or a PDF. Send **either**:

- the file as the **raw request body** (the format is detected from the content; `Content-Type` is not required), with options in the query string, or
- `multipart/form-data` with the file in a part named `file` and options as form fields (query-string options also work and take precedence).

Any option you leave out uses the app's current **Print settings**. Success:

```json
{ "ok": true, "pages": 1, "copies": 1, "rows": 842 }
```

`rows` is the number of dot rows sent (8 rows ≈ 1 mm), including tear-off lines. See [Print parameters](#print-parameters).

### `POST /api/test`
Prints the built-in test page on the selected (or `?printer=`) printer.

### `POST /api/feed?mm=20` and `POST /api/retract?mm=20`
Moves the paper forward or back. `mm` is 1–200 and defaults to the Feed/Retract length set in the app. Both accept `?printer=`.

### `GET /api/history?limit=20`
Most recent jobs, newest first (`limit` 1–100).

```json
{
  "ok": true,
  "history": [
    { "time": 1790000000000, "printer": "GB03", "source": "api", "rows": 842, "ok": true, "error": null }
  ]
}
```

`time` is Unix milliseconds. `source` is one of `test`, `direct`, `service`, `api`, `reprint`.

### `GET /` (web page)
A minimal upload page: choose a file, adjust darkness, copies, size, feed, dithering and invert, then print. It shows live printer status. It uses the same engine as the API (via `/w/...` routes) and works even when the API switch is off.

## Print parameters

Pass as query parameters or multipart form fields. Values outside a range are clamped. Unknown parameters are ignored.

| Parameter | Type / range | Default | Description |
|---|---|---|---|
| `printer` | address string | selected printer | Saved printer address (from `/api/status`). `404` if unknown. |
| `darkness` | 0–100 | app setting | Print heat. Higher is darker and uses more battery. |
| `feed` | 0–100 mm | app setting | Paper pushed out after printing. |
| `copies` | 1–20 | 1 | Number of copies (a multi-page PDF is repeated as a whole). |
| `size` | 5–100 | 100 | Image width as a percentage of the printable 48 mm. |
| `side` | 0–10 mm | app setting | Extra left/right margin. |
| `vert` | 0–10 mm | app setting | Extra top/bottom margin. |
| `rotation` | 0, 90, 180, 270 | 0 | Clockwise rotation (other values are rounded down to a multiple of 90). |
| `brightness` | -100…100 | 0 | Tone adjustment before dithering. |
| `contrast` | -100…100 | 0 | Tone adjustment before dithering. |
| `invert` | bool | false | Swap black and white. |
| `dither` | `smooth`, `sharp`, `pattern` | image: app setting, PDF: `sharp` | Smooth = Floyd–Steinberg (photos), Sharp = pure threshold (text, barcodes, QR), Pattern = ordered dots. |
| `lineBefore` | bool | app setting | Tear-off line before the print. |
| `lineAfter` | bool | app setting | Tear-off line after the print. |
| `pages` | `N` or `A-B` | all | PDF page range, 1-based, e.g. `2-4`. |

Booleans accept `1`, `true`, `on` or `yes` (anything else is false).

Paper facts: the print head is 384 dots (48 mm) wide at 203 dpi, so images are scaled to that width.

## Errors

Errors return `{"ok": false, "error": "message"}` with one of these statuses:

| Status | When |
|---|---|
| 400 | Empty body, unreadable image/PDF, bad multipart |
| 401 | Token required and missing/wrong |
| 403 | Cross-origin browser request while no token is set |
| 404 | Unknown endpoint or printer, or the web page/API is disabled |
| 409 | No printer selected in the app |
| 413 | Body over 30 MB or job longer than ~5 m |
| 500 | Printer error (out of paper, cover open, overheated, connection failed…); `error` has the reason |

## Examples

Replace `192.168.1.20:8631` with your phone's address and `TOKEN` with your token (drop the auth header if it is off).

```sh
# Check the printer
curl -H "Authorization: Bearer $TOKEN" http://192.168.1.20:8631/api/status

# Print an image (raw body)
curl -H "Authorization: Bearer $TOKEN" --data-binary @photo.jpg \
  "http://192.168.1.20:8631/api/print?darkness=70&dither=smooth&size=80"

# Print pages 1-2 of a PDF, two copies (multipart)
curl -H "Authorization: Bearer $TOKEN" \
  -F file=@doc.pdf -F pages=1-2 -F copies=2 -F dither=sharp \
  http://192.168.1.20:8631/api/print

# Feed 30 mm, then print the test page
curl -X POST -H "Authorization: Bearer $TOKEN" "http://192.168.1.20:8631/api/feed?mm=30"
curl -X POST -H "Authorization: Bearer $TOKEN" http://192.168.1.20:8631/api/test

# Recent jobs
curl -H "Authorization: Bearer $TOKEN" "http://192.168.1.20:8631/api/history?limit=5"
```

Python:

```python
import requests
r = requests.post(
    "http://192.168.1.20:8631/api/print",
    params={"darkness": 65, "copies": 1},
    headers={"Authorization": "Bearer TOKEN"},
    data=open("label.png", "rb").read(),
    timeout=300,
)
print(r.json())
```

JavaScript (browser or Node 18+):

```js
const res = await fetch("http://192.168.1.20:8631/api/print?dither=sharp", {
  method: "POST",
  headers: { Authorization: "Bearer TOKEN" },
  body: fileOrBlob,
});
console.log(await res.json());
```

## Security notes

- The server speaks plain HTTP. Use it on a network you trust, and turn on **Require access token** if others share the Wi-Fi.
- With **Network access** off, only apps on the phone itself can connect.
- The token is stored on the phone; generate a new one from the Print server screen at any time (this invalidates the old one).
- Anyone with the token can print, feed and read printer status and history summaries. Nothing can read files from the phone.

## Troubleshooting

| Problem | Fix |
|---|---|
| Can't connect from another device | Same Wi-Fi? Network access on? Some routers isolate Wi-Fi clients ("AP isolation"). Check the IP shown in the app. |
| Server stops when the screen is off | Disable battery optimisation for MeowSpool (Print server screen shows a warning when it is enabled). Some vendors also need "Autostart"/"Background activity" allowed. |
| "Port … is already in use" | Pick another port. |
| `409 No printer selected` | Select a printer in the app first. |
| `500 Out of paper` / `Cover open` | Fix the printer and retry. |
| Requests time out | Long jobs block until finished; raise the client timeout. |
