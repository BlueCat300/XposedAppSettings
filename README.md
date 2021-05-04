App Settings Reborn
===========

### Download: [EdXposed Repository](https://repo.xposed.info/module/ru.bluecat.android.xposed.mods.appsettings)

**Discussion/Support:** [XDA thread](https://forum.xda-developers.com/t/mod-xposed-app-settings-reborn.4141339)

Fork of the original App Settings by Rovo89. Supporting Android 8.0+

**Compatibility:**
The application is tested only on Android 10/11 (Samsung firmware), but has theoretical support for earlier versions of the OS. I do not guarantee work on other firmware. I also note that not all applications will work with all functions.
If you do not see errors in edxposed logs from this module, then your firmware requires additional hooks. I cannot provide development for such devices.

Using the function revoke/restore permissions: select the permissions and click ok + save, reboot, return to the submenu of the target application and click save again, reboot.

**Features:**
- Screenshot control
- density / dpi
- font scale
- fake screen size for resources loading
- locale (language)
- fullscreen mode
- hide title bar
- keep screen on while app is visible
- show app above lockscreen
- disable fullscreen keyboard input
- Force LTR
- Auto hide fullscreen (Android 11+)
- force orientation (portrait/landscape/auto)
- mute audio (for most apps)
- insistent notifications (loop the sound / reboot required)
- force or prevent ongoing notifications (reboot required)
- mute notifications (reboot required)
- stay resident in memory (reboot required)
- force or exclude app from recents (reboot required)
- access to recent tasks (reboot required)
- revoke permissions (double reboot required)
- force using legacy (navbar) menu button (not available on OS 10.0+)
- notifications priority (not available on OS 8.0+)
- disable big (expanded) notifications (not available on OS 6.0+)

**Previous Versions and original developers:**
- https://github.com/Phoenix09/XposedAppSettings
- https://github.com/cooldroid/XposedAppSettings
- https://github.com/rovo89/XposedAppSettings

License
-------

Licensed under the Apache License, Version 2.0
