#!/bin/bash

SCREEN_ON=$(adb shell dumpsys power | grep mScreenOn | cut -d'=' -f2 | grep 'true')

if [[ ! ${SCREEN_ON} ]]; then
    echo "waking up the device's screen"
    adb shell input keyevent KEYCODE_POWER
fi
