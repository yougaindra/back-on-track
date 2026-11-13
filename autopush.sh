#!/data/data/com.termux/files/usr/bin/bash
# Auto-sync watcher for back-on-track / shake-flashlight
cd /data/data/com.termux/files/home/back-on-track

echo "🚀 Auto-push watcher started!"
echo "Watching for file changes... (Press Ctrl+C to stop)"

while true; do
    if [ -n "$(git status --porcelain)" ]; then
        echo ""
        echo "⚡ Changes detected! Auto-committing and pushing to GitHub..."
        git add -A
        BRANCH=$(git branch --show-current)
        git commit -m "Auto-update: $(date '+%Y-%m-%d %H:%M:%S')"
        git push origin "$BRANCH"
        echo "✅ Pushed to branch '$BRANCH'! GitHub Actions will build the APK automatically."
    fi
    sleep 3
done
