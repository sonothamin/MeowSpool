# MeowSpool (Android PrintService for cat thermal printers)
Open in Android Studio (it generates the Gradle wrapper), run on a device, then:
1. Open the app -> Scan -> tap your printer to save it.
2. Settings -> Connected devices -> Connection preferences -> Printing -> enable "MeowSpool".
3. Print from any app (Share -> Print, Chrome, Photos, Files...) and pick the printer.
Protocol: GB01/02/03-style (service AE30, write AE01, notify AE02), 384 dots/line.
