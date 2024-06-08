#!/bin/bash

echo "Generating feature graphics to ~/termux-icons/termux-feature-graphic.png..."
mkdir -p ~/termux-icons/

# The Android TV banner on google play (1280x720) has same aspect ratio
# as the banner in the app (320x180).
rsvg-convert -w 1280 -h 720 tv-banner.svg > ~/termux-icons/tv-banner.png

# See https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines#banner
rsvg-convert -w 240 -h 135 tv-banner.svg > ../termux-app/src/main/res/mipmap-hdpi/banner.png
rsvg-convert -w 320 -h 180 tv-banner.svg > ../termux-app/src/main/res/mipmap-xhdpi/banner.png
rsvg-convert -w 480 -h 270 tv-banner.svg > ../termux-app/src/main/res/mipmap-xxhdpi/banner.png
rsvg-convert -w 640 -h 360 tv-banner.svg > ../termux-app/src/main/res/mipmap-xxxhdpi/banner.png
