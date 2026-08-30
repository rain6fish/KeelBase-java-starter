#!/usr/bin/env bash
# Build a complete Maven Central upload package from the release build in ~/.m2.
#
# Central Portal rejects any deployment whose files lack .md5/.sha1 checksums
# (Error: Missing md5/sha1 checksum ...). A plain `mvn install -Prelease` yields
# pom/jar/sources/javadoc + .asc but NO checksums. This script copies the release
# artifacts out of ~/.m2, generates a valid (possibly empty) javadoc.jar for any
# source-less module, then produces .asc + .md5 + .sha1 for every file and zips it.
#
# Usage:  bash scripts/build-central-upload.sh [VERSION]
# Env:    GPG_KEY_ID (default 7ECAABC1ABDC27F3)
#         GPG_PASSPHRASE_FILE (default /tmp/gpg-passphrase.txt; plain passphrase, no prefix)
#         M2_REPO (default ~/.m2/repository/cn/com/keelbase)
set -euo pipefail

VER="${1:-0.1.0}"
KEY="${GPG_KEY_ID:-7ECAABC1ABDC27F3}"
PP="${GPG_PASSPHRASE_FILE:-/tmp/gpg-passphrase.txt}"
M2="${M2_REPO:-$HOME/.m2/repository/cn/com/keelbase}"
# 命名带 keelbase-java-starter 前缀（与仓库一致），避免与主库 keelbase 混淆
STAGE="$HOME/.m2/keelbase-java-starter-upload-$VER"
ZIP="$HOME/.m2/keelbase-java-starter-$VER-upload.zip"

MODULES="keelbase-tools-annotation keelbase-delegation-filter keelbase-tools-export keelbase-compensation keelbase-client keelbase-spring-boot-autoconfigure keelbase-spring-boot-starter"

# zip-capable tar: Windows System32 ships bsdtar (libarchive); git-bash's tar is GNU (no zip)
if [ -x /c/Windows/System32/tar.exe ]; then
  ZIP_TAR=/c/Windows/System32/tar.exe
elif command -v bsdtar >/dev/null 2>&1; then
  ZIP_TAR=bsdtar
else
  echo "ERROR: need bsdtar (zip-capable). Not found." >&2; exit 1
fi

[ -f "$PP" ] || { echo "ERROR: passphrase file not found: $PP" >&2; exit 1; }

rm -rf "$STAGE"

# 1. parent pom
PDIR="$STAGE/cn/com/keelbase/keelbase-java-starter-parent/$VER"
mkdir -p "$PDIR"
cp "$M2/keelbase-java-starter-parent/$VER/keelbase-java-starter-parent-$VER.pom" "$PDIR/"

# 2. module artifacts (+ empty-but-valid javadoc.jar for source-less modules)
for m in $MODULES; do
  d="$STAGE/cn/com/keelbase/$m/$VER"
  src="$M2/$m/$VER"
  mkdir -p "$d"
  for f in "$src/$m-$VER.pom" "$src/$m-$VER.jar" "$src/$m-$VER-sources.jar" "$src/$m-$VER-javadoc.jar"; do
    [ -f "$f" ] && cp "$f" "$d/"
  done
  if [ ! -f "$d/$m-$VER-javadoc.jar" ]; then
    ej="$d/$m-$VER-javadoc.jar"
    mj="$(mktemp -d)"
    mkdir -p "$mj/META-INF"
    printf 'Manifest-Version: 1.0\r\n' > "$mj/META-INF/MANIFEST.MF"
    (cd "$mj" && "$ZIP_TAR" --format zip -cf "$ej" META-INF)
    rm -rf "$mj"
    echo "created empty javadoc.jar for source-less module: $m"
  fi
done

# 3. sign + checksum every artifact file
cd "$STAGE"
TOTAL=0
while IFS= read -r f; do
  gpg --batch --no-tty --yes --pinentry-mode loopback --detach-sign --armor \
      --local-user "$KEY" --passphrase-file "$PP" -o "$f.asc" "$f" 2>/dev/null
  printf '%s\n' "$(md5sum "$f" | cut -d' ' -f1)"  > "$f.md5"
  printf '%s\n' "$(sha1sum "$f" | cut -d' ' -f1)" > "$f.sha1"
  TOTAL=$((TOTAL+1))
done < <(find . -type f | sort)
echo "signed+checksummed $TOTAL files"

# 4. zip it
rm -f "$ZIP"
"$ZIP_TAR" -a -cf "$ZIP" -C "$STAGE" cn
echo "upload zip: $ZIP"
ls -la "$ZIP"
