#!/bin/bash
./gradlew clean

./gradlew releaseMinor || exit 1

git reset --hard HEAD~1
./publish-npm.sh
git pull

./gradlew build gitChangelogTask
git commit -a --amend --no-edit
git push -f

