#!/bin/sh
# A throwaway signing key for the CI jobs that build but never ship. androidApp signs its release with
# keystore.properties, and while the file is missing every load of the project warns that signing is
# enabled without it. The release job writes the real one from secrets; this never touches one that
# is already there. Both files are gitignored.
set -eu
cd "$(dirname "$0")/.."
properties=apps/androidApp/keystore.properties
if [ -e "$properties" ]; then
  echo "$properties exists; leaving it alone"
  exit 0
fi
printf 'storeFile=ci.keystore\nstorePassword=throwaway\nkeyAlias=ci\nkeyPassword=throwaway\n' > "$properties"
# The build reads storeFile against apps/androidApp/ and the generator against the working directory (its
# --keystore-file gives way to storeFile), so it runs from there.
cd apps/androidApp
../../kotlin tool generate-keystore --properties-file keystore.properties
