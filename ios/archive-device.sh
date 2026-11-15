#!/usr/bin/env bash
# Reproducible unsigned arm64 archive. This is NOT installable or distribution signed.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"
python3 ios/make-project.py
xcodebuild archive -project ios/build/VoxelOne.xcodeproj -scheme VoxelOne \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  -archivePath "$repo/ios/build/VoxelOne.xcarchive" \
  -derivedDataPath "$repo/ios/build/DeviceDerivedData" \
  CODE_SIGNING_ALLOWED=NO SKIP_INSTALL=NO ARCHS=arm64
