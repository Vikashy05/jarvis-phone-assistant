# Jarvis for Xiaomi Mi 11X

Jarvis is a small Kotlin app for HyperOS 1 on Android 13. Tap **Speak** for one English command, or type the same words and tap **Run command**. After installation, disconnect USB and turn off the laptop. The installed app runs independently.

## Use the local phone agent

Install the **0.2** debug APK. It contains the Qwen2.5-0.5B-Instruct language model and the ARM64 MediaPipe inference runtime. Plan for about 2 GB of free storage during installation and first use. The first goal copies the model into app-private storage. The phone runs the planner on its CPU. No API key, laptop connection or model account is needed. [Qwen model](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct) · [Mobile model bundle](https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct)

1. Open **Jarvis → Enable agent mode** and read the disclosure. Open Android Accessibility settings and enable **Jarvis phone agent**. Return to Jarvis. The YouTube ad helper has a separate switch.
2. Tap **Speak** or enter a goal in the command field and tap **Run command**. Start with **“Make it mute.”** This changes media volume. Calls and alarms retain their levels. Try **“Unmute the media”** to restore sound.
3. Try **“Mute the media, then open YouTube.”** The model sees the current media mute state, foreground app, visible control labels and recent steps. It chooses one supported action, Jarvis checks Android’s response, and the model sees an updated observation. The floating panel shows progress. Check both the media volume and the foreground app yourself when the model reports completion.
4. To start from another app on Android 13, edit Quick Settings, add **Ask Jarvis**, then use the tile while the desired app is open. Enter or speak your goal. Jarvis returns to the previous task before reading the screen. If your HyperOS build returns to the launcher, include the desired app name in the goal.
5. The agent can open installed launcher apps by name, search YouTube, mute or unmute media, go Home or Back, scroll a list forward, tap a visible control, and replace text in an editable field. For a tap or text change, read the app name and proposed effect on the floating panel. **Allow step** executes it. **Stop** cancels the remaining goal, including any pending approval. A native inference already running may finish in the background before releasing memory.
6. **Turn off agent mode** restores quick commands. **Manage phone access** opens Android settings where you can revoke the service grant. For Android 13 restricted-settings messages, see the accessibility setup notes below.

### What to expect on Mi 11X

Allow time for the first model load. This small model can choose an incorrect step, finish early or produce an unusable response. It sees a bounded set of Android accessibility nodes. Games, custom-drawn views, protected dialogs and unlabeled controls can be inaccessible. Some HyperOS builds restrict background activity launches. A locked phone, a changed target at execution, a repeated action on the same screen, a rejected action, eight steps or three minutes end the run. Screen contents may contain misleading instructions. Review each proposed tap and edit, especially when a screen can send, purchase, delete or change account settings. Use the normal Android UI for authentication and restricted actions.

### Personal distribution

Google Play restricts using accessibility to autonomously plan and execute actions. This sideloadable prototype uses that capability and should not be submitted as a general-purpose Play accessibility app. Model input, goals and screen observations remain local. Apps you open still handle data under their own permissions. [Google Play accessibility automation policy](https://support.google.com/googleplay/android-developer/answer/10964491)

## Quick commands with agent mode off

Build and install the debug APK using the Windows instructions below, then try these commands on the phone.

1. Open Jarvis. Type **open Settings** and tap **Run command**. Android Settings should open. Press Back to return.
2. Tap **Speak** and allow microphone access while using the app. Say **Jarvis, open Camera**. The word “Jarvis” is optional. Each voice session begins with a tap, needs Jarvis in the foreground and ends after one result, cancellation, loss of foreground or timeout.
3. Try **Jarvis, open YouTube and search for Python tutorials**. Jarvis hands the query to the installed YouTube app. YouTube handles search and playback using its own connection.
4. For a fully offline check, enable airplane mode and say **open Settings**. Speech requires an installed on-device recognizer and its English (US) model. Install offline speech data through the system speech provider if available. Some HyperOS builds lack a compatible provider. Typed commands remain available.

| Command | Action |
| --- | --- |
| Open or launch YouTube, Chrome, Maps or WhatsApp | Opens that installed app |
| Open or launch Camera or Settings | Opens the system camera or settings |
| Search YouTube for lo-fi music | Opens YouTube search results |
| Open YouTube and search for Python tutorials | Opens the same search directly |

Quick mode recognizes the listed commands. Use the keyboard with the microphone and both accessibility grants off.

## Build and install from Windows

The first build downloads a 547 MB quantized model from a pinned revision and verifies its SHA-256 before packaging. The source ZIP omits the weights. For an offline model file supply `-PplannerFile=C:\models\planner.task` to Gradle. Use the q8, 1280-token `.task` file from the linked mobile model repository. Copyright and license information is included under `app/src/main/assets`. The Android APK already contains the model.

1. Install Android Studio with support for Android Gradle Plugin 8.7.3 (Ladybug 2024.2.1 or a compatible newer version). Extract the source ZIP to a short path such as `C:\src`. In Studio choose **Open** and select the **Jarvis** folder containing `settings.gradle.kts`.
2. In **SDK Manager**, install **Android SDK Platform 33**, **Android SDK Build-Tools 34.0.0** (use **Show Package Details**) and **Android SDK Platform-Tools**. Select JDK 17 as the Gradle JDK in **Settings → Build, Execution, Deployment → Build Tools → Gradle**. Allow the first online sync to finish. Gradle 8.9 is pinned in the included wrapper.
3. On the phone open **Settings → About phone → Detailed info and specs**, then tap **OS version** or **MIUI version** repeatedly until developer options appear. Under **Additional settings → Developer options**, enable **USB debugging**. Connect an unlocked phone using a data-capable USB cable and approve this computer on the phone. Menu labels vary between HyperOS builds. [Xiaomi developer options](https://www.mi.com/uk/support/faq/details/KA-168765/) · [Xiaomi USB troubleshooting](https://www.mi.com/global/support/faq/details/KA-42423/)
4. Select **app** and your **Mi 11X** in Android Studio, then click **Run ▶**. If HyperOS reports that USB installation is disabled, check its **Install via USB** setting. Once installed, disable USB debugging if desired and open Jarvis from the phone launcher.

To build in PowerShell, open the project folder with `JAVA_HOME` pointing to JDK 17 and `ANDROID_HOME` pointing to your SDK. Android Studio can also record the SDK location in `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices
& $adb install -r '.\app\build\outputs\apk\debug\app-debug.apk'
```

A device marked `unauthorized` needs the USB confirmation on its unlocked screen. If installing a build signed on a different computer reports a signature conflict, uninstall the existing Jarvis app first if you are comfortable losing its local settings. The next install starts with the helper off.

## Optional YouTube helper

The helper starts off. Voice launching and YouTube search work independently.

1. In Jarvis select **Enable helper**, review the disclosure and choose **Allow helper**.
2. In Android Accessibility settings open **Downloaded apps** or **Installed services → Jarvis YouTube helper**, enable the service and confirm the system prompt. Return to Jarvis to see the status. Android 13 can restrict accessibility for some sideloaded apps. If the option is greyed out, review Android’s explanation before choosing whether to grant access under **App info → More → Allow restricted settings**. Device policy may prevent this. [Android restricted settings](https://support.google.com/android/answer/12623953)
3. Play a video in the official YouTube app. When YouTube exposes a recognized, visible and enabled ad-skip control, the service requests a click. English labels supported are **Skip Ad**, **Skip Ads** and **Skip**, paired with one of the ad-specific view IDs listed in `SkipService.kt`. A countdown, an unskippable ad, a localized label, a hidden control or a changed view ID can leave the ad playing. A manual tap still works whenever YouTube offers one.
4. **Pause helper** immediately stops processing YouTube screen content. **Accessibility settings** lets you revoke the system grant separately. Opening system settings directly and granting access still requires consent in Jarvis before it acts.

The helper receives window-change events scoped to YouTube, checks the active window’s package and ID, and requests at most one click per 1.5 seconds. Android manages the service independently of the Jarvis screen. HyperOS power management, a force-stop or a YouTube update can interrupt it. Check both controls in Jarvis if it stops.

## Privacy and distribution

Jarvis requests microphone access on the first tap of **Speak**. It explicitly selects Android’s on-device recognition service. Jarvis stores the helper preference locally. Audio, transcripts and screen content are never written to a history or sent to a Jarvis server. A recognized phrase appears in the command field for the current screen. YouTube receives a search phrase only when the corresponding command runs. The app has no internet permission. [Android speech recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer)

This is a personal debug build. YouTube restricts automated access, which may affect use of the helper. Google Play prohibits apps interfering with another app’s ad display. Automating a visible skip button can create a distribution risk. Play also requires accessibility declarations and, for apps like this, disclosure and affirmative consent. Review these requirements before distributing any build, and consider a voice-only version for Play. [YouTube terms](https://www.youtube.com/static?template=terms) · [Google Play ad-interference policy](https://support.google.com/googleplay/android-developer/answer/16559646) · [Google Play accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491)

## On-phone acceptance checks

| Try this | Expected result |
| --- | --- |
| Agent enabled, airplane mode on, type “make it mute” | Media becomes muted if the model selects the correct action; verify via Android volume controls |
| Ask to mute and open YouTube; watch the floating panel | Planner gets a fresh observation between steps |
| Stop during planning or while a tap awaits approval | Panel closes and late model output cannot execute a step |
| Deny microphone, then type “open Settings” | Settings opens |
| Tap Speak, then Cancel or Home before finishing | Recording stops and a late result cannot launch an app |
| Type “open SomethingUnknown” | Jarvis explains the supported syntax |
| Speak or type a search with spaces and an ampersand | YouTube receives one encoded search query |
| Leave either helper control off | Jarvis makes no skip attempts |
| Enable both controls, then play a skippable ad | A click is possible only when the recognized control is exposed |
| Pause the helper or revoke its system access | Subsequent ads remain under manual control |
| Disconnect USB, close Studio and open Settings by command | Launch works on the phone alone |

## License

The original Jarvis code and documentation are available under the [MIT License](LICENSE). The Gradle wrapper, MediaPipe runtime and Qwen model retain their upstream licenses. The model attribution and license are in [app/src/main/assets](app/src/main/assets/).
