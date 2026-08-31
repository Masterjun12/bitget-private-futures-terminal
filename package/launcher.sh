#!/bin/sh
set -eu

exec /usr/bin/java \
    -Xms32m -Xmx256m \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -jar /opt/bitget-private-terminal/bitget-private-terminal.jar "$@"
