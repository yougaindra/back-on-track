#!/usr/bin/env bash
set -e

cd /data/data/com.termux/files/home/back-on-track

echo "Cleaning up duplicate shakelight sources from apps/back-on-track..."
rm -rf apps/back-on-track/src/main/java/com/yougaindra/shakelight
rm -rf apps/back-on-track/src/test/java/com/yougaindra/shakelight
rm -f apps/back-on-track/src/main/res/drawable/ic_camera.xml
rm -f apps/back-on-track/src/main/res/drawable/ic_flashlight.xml

echo "Committing and pushing fix..."
git add -A
git commit -m "Fix back-on-track: isolate shakelight code to apps/moto-gestures"
git push origin "$(git branch --show-current)"

echo "Fixed and pushed! Check GitHub Actions with 'gh run list'."
