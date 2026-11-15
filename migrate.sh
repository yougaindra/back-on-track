#!/usr/bin/env bash
set -e

cd /data/data/com.termux/files/home/back-on-track

echo "Restoring Back On Track from master branch..."
git checkout master -- app/

echo "Populating apps/back-on-track..."
mkdir -p apps/back-on-track
cp -r app/* apps/back-on-track/
rm -rf app
rm -f .github/workflows/build-apk.yml

echo "Committing and pushing to GitHub..."
git add -A
git commit -m "Migrate to Gradle monorepo with path-filtered CI"
git push origin "$(git branch --show-current)"

echo "Monorepo migration complete! CI is now running."
