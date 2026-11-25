# Time & Motion – Galvatech

Android app for time and motion studies. You set up a job analysis with motions marked VA / NNVA / NVA, then time up to 4 labourers at once. The results show the efficiency split, a timeline per labourer and time per unit. You can export them to Excel or PDF, or as a study file for the planned PC video-coding app.

## Getting the app on your phone
GitHub builds the APK automatically every time code is pushed to `main`. To install the latest build:
1. On the phone, open the repository's **Releases** page in the browser while signed in to GitHub.
2. Download the newest `TimeMotion-<n>.apk`.
3. Open it and allow "Install unknown apps" for your browser when Android asks.

Every build is signed with the same key (`app/debug.keystore`), so a new build installs as an update and your analyses and studies stay on the phone.

## Using it
1. **New analysis**: enter a name, a description, the unit you count (e.g. "Jig loaded"), and the motions, each marked VA / NNVA / NVA.
2. **Start study**: enter the observer, the shift and 1–4 labourers.
3. **Capture (landscape)**: tap a motion when a labourer changes what they're doing. **+1** counts a unit for that labourer, **Undo** removes their last tap, and **Pause** stops the clock for everyone.
   The timer keeps running if the screen locks or you leave the app. Reopen the app and tap **Resume study**.
4. **Finish**: see the results, then share them as Excel, PDF or a study file (WhatsApp, email, Drive, etc.).

## Data
All data stays on the phone in the app's private storage. There's no server and no internet use.
Uninstalling the app deletes the data, so export the studies first.

## Building it yourself (optional)
Open the folder in Android Studio and run *Build → Build APK(s)*, or run `gradle assembleDebug` with the Android SDK installed.
