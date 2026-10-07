#!/usr/bin/env bash
# Script to sync and push Sanatan Seva Samiti (sss) app to GitHub
set -e

GITHUB_USERNAME="${1:-}"

if [ -z "$GITHUB_USERNAME" ]; then
    echo "Usage: ./push_to_github.sh <YOUR_GITHUB_USERNAME>"
    echo "Example: ./push_to_github.sh mishrarsrs"
    exit 1
fi

REPO_URL="https://github.com/${GITHUB_USERNAME}/sss.git"

echo "Configuring remote origin: $REPO_URL..."
git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"

echo "Renaming branch to main..."
git branch -M main

echo "Pushing code to $REPO_URL..."
git push -u origin main

echo "Done! Successfully pushed to https://github.com/${GITHUB_USERNAME}/sss"
