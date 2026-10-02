#!/bin/bash
sed -i '' 's/val safeKnownIps = node.knownIps ?: emptyList()//g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
sed -i '' 's/node.knownIps ?: emptyList()/node.safeKnownIps/g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
sed -i '' 's/val safePrevKnownIps = prev.knownIps ?: emptyList()//g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
sed -i '' 's/safePrevKnownIps/prev.safeKnownIps/g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
sed -i '' 's/val safeKnownIps = node.safeKnownIps//g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
sed -i '' 's/node.knownIps/node.safeKnownIps/g' apps/android/app/src/main/java/com/devlens/network/DevLensRepository.kt
