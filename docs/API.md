# MeowSpool print server API

Enable **Print server** in the app menu. Default address: `http://<phone-ip>:8631` (use **Show QR** in the app).
If *Require access token* is on, send `Authorization: Bearer <token>` (or `X-API-Key: <token>`).

| Endpoint | Purpose |
|---|---|
| `GET /api` | List endpoints |
| `GET /api/status` | Printers, connection, paper/cover/overheat/battery flags |
| `GET /api/history?limit=20` | Recent jobs |
| `POST /api/print` | Print an image or PDF |
| `POST /api/test` | Print the test page |
| `POST /api/feed?mm=20` / `POST /api/retract?mm=20` | Move paper |

## Print

Send the file as the raw body, or as multipart field `file`. Parameters go in the query string (or as multipart form fields).
Anything omitted uses the app's Print settings.

```sh
curl -H "Authorization: Bearer $TOKEN" --data-binary @photo.jpg "http://192.168.1.20:8631/api/print?darkness=70&dither=smooth"
curl -F file=@doc.pdf -F pages=1-2 -F copies=2 http://192.168.1.20:8631/api/print
```

| Param | Values |
|---|---|
| `printer` | Saved printer address (default: selected printer) |
| `darkness` | 0–100 |
| `feed` | Paper feed after print, mm |
| `copies` | 1–20 |
| `size` | 5–100 (% of width) |
| `side`, `vert` | Margins, 0–10 mm |
| `rotation` | 0, 90, 180, 270 |
| `brightness`, `contrast` | -100…100 |
| `invert`, `lineBefore`, `lineAfter` | `1`/`true` |
| `dither` | `smooth`, `sharp`, `pattern` |
| `pages` | PDF pages, e.g. `2-4` (1-based) |

Response: `{"ok":true,"pages":1,"copies":1,"rows":842}`; errors: `{"ok":false,"error":"..."}` with HTTP 4xx/5xx.
The request returns when printing has finished.
