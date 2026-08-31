#!/bin/sh
set -eu

umask 022

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"
PACKAGE_NAME=bitget-private-terminal
PACKAGE_VERSION=3.1.1
PACKAGE_ARCH=all
SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-1788134400}
export SOURCE_DATE_EPOCH
JAR_FILE="$BUILD_DIR/$PACKAGE_NAME.jar"
OUTPUT="$DIST_DIR/${PACKAGE_NAME}_${PACKAGE_VERSION}_${PACKAGE_ARCH}.deb"
STAGE_DIR=$(mktemp -d "${TMPDIR:-/tmp}/${PACKAGE_NAME}.XXXXXX")

cleanup() {
    rm -rf -- "$STAGE_DIR"
}
trap cleanup EXIT HUP INT TERM

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    fi
}

for command_name in java dpkg-deb sha256sum; do
    require_command "$command_name"
done

compile_java() {
    if command -v javac >/dev/null 2>&1; then
        javac "$@"
    else
        java -m jdk.compiler/com.sun.tools.javac.Main "$@"
    fi
}

build_jar() {
    if command -v jar >/dev/null 2>&1; then
        jar "$@"
    else
        java -m jdk.jartool/sun.tools.jar.Main "$@"
    fi
}

rm -rf -- "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes" "$DIST_DIR"
find "$PROJECT_DIR/src" -type f -name '*.java' -print | LC_ALL=C sort > "$BUILD_DIR/sources.list"
find "$PROJECT_DIR/test" -type f -name '*.java' -print | LC_ALL=C sort > "$BUILD_DIR/tests.list"

if [ ! -s "$BUILD_DIR/sources.list" ] || [ ! -s "$BUILD_DIR/tests.list" ]; then
    printf 'Java sources and regression tests are required.\n' >&2
    exit 1
fi

compile_java --release 11 -Xlint:all -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.list"
compile_java --release 11 -Xlint:all -cp "$BUILD_DIR/classes" \
    -d "$BUILD_DIR/test-classes" @"$BUILD_DIR/tests.list"
java -cp "$BUILD_DIR/classes:$BUILD_DIR/test-classes" com.local.bitget.BitgetClientTest
mkdir -p "$BUILD_DIR/classes/META-INF"
printf 'Manifest-Version: 1.0\r\nMain-Class: com.local.bitget.Main\r\n\r\n' \
    > "$BUILD_DIR/classes/META-INF/MANIFEST.MF"
find "$BUILD_DIR/classes" -exec touch -d "@$SOURCE_DATE_EPOCH" {} +
(
    cd "$BUILD_DIR/classes"
    find . -type f -print | LC_ALL=C sort > "$BUILD_DIR/jar-files.list"
    build_jar --create --file "$JAR_FILE" --no-manifest @"$BUILD_DIR/jar-files.list"
)

chmod 0755 "$STAGE_DIR"
install -D -m 0644 "$SCRIPT_DIR/control" "$STAGE_DIR/DEBIAN/control"
install -D -m 0644 "$JAR_FILE" "$STAGE_DIR/opt/$PACKAGE_NAME/$PACKAGE_NAME.jar"
install -D -m 0755 "$SCRIPT_DIR/launcher.sh" "$STAGE_DIR/usr/bin/$PACKAGE_NAME"
install -D -m 0644 "$SCRIPT_DIR/bitget-private-terminal.desktop" \
    "$STAGE_DIR/usr/share/applications/$PACKAGE_NAME.desktop"
install -D -m 0644 "$SCRIPT_DIR/bitget-private-terminal.desktop" \
    "$STAGE_DIR/etc/xdg/autostart/$PACKAGE_NAME.desktop"
install -D -m 0644 "$SCRIPT_DIR/bitget-private-terminal.svg" \
    "$STAGE_DIR/usr/share/icons/hicolor/scalable/apps/$PACKAGE_NAME.svg"

for document in README.md LICENSE SECURITY.md PRIVACY.md; do
    install -D -m 0644 "$PROJECT_DIR/$document" \
        "$STAGE_DIR/usr/share/doc/$PACKAGE_NAME/$document"
done

find "$STAGE_DIR" -exec touch -d "@$SOURCE_DATE_EPOCH" {} +

dpkg-deb --root-owner-group --build "$STAGE_DIR" "$OUTPUT"
(
    cd "$DIST_DIR"
    sha256sum "$(basename -- "$OUTPUT")" > SHA256SUMS
    chmod 0644 SHA256SUMS
)

printf '%s\n' "$OUTPUT"
