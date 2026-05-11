# ShakeAlert iOS Replica

This is a Swift/iOS replica of the ShakeAlert Android application.

## Prerequisites

1.  **Xcode**: Required to build and run the app.
2.  **CocoaPods** or **Swift Package Manager**: To install dependencies.

## Dependencies

You need to add the following dependencies to your project:

1.  **GoogleWebRTC**: For WebRTC audio communication.
2.  **Socket.io-Client-Swift**: For real-time signaling with the server.

## Project Setup

1.  Create a new Xcode Project (App) named `ShakeAlert`.
2.  Replace the generated files with the ones in this folder.
3.  Add the dependencies using your preferred package manager.
4.  (Improved) Configure the Socket server URL via `Info.plist` key `ShakeAlertSocketURL`.
    If absent, the app falls back to the previous local dev URL used during development.
5.  **Permissions**: Add the following keys to your `Info.plist`:
    *   `Privacy - Location Always and When In Use Usage Description`: "We need your location to send emergency alerts to rescuers."
    *   `Privacy - Location When In Use Usage Description`: "We need your location to send emergency alerts to rescuers."
    *   `Privacy - Microphone Usage Description`: "We need microphone access for emergency audio communication with rescuers."
6.  **Background Modes**: Enable the following Background Modes in the project's Signing & Capabilities:
    *   Location updates
    *   Audio, AirPlay, and Picture in Picture
    *   Background fetch
    *   Remote notifications

## Recent Polishes

- Read `ShakeAlertSocketURL` from `Info.plist` (fallback to dev URL).
- Timestamps added to runtime logs for easier debugging in `ContentView`.
- `ContentView` UI: friendlier button text, prominent action button, styled log panel and app version shown in toolbar.
- WebRTC candidate parsing hardened to avoid runtime crashes from malformed signaling payloads.

## Comparison with Android Version

*   `WebRTCHandler.swift` replicates `WebRTCHandler.kt`.
*   `ShakeDetector.swift` replicates `ShakeDetector.kt`.
*   `ShakeAlertManager.swift` replaces `ShakeAlertService.kt`. Note that iOS background execution is more restrictive; "Always" location permission is used here to keep the app alive.
*   `ContentView.swift` replaces the XML layout and `MainActivity.kt` logic.
