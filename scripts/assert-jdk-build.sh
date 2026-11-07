#!/usr/bin/env bash
# Fail unless the JDK on PATH is a Temurin build at or above the release that carries the fixed
# JSSE implementations: 17.0.21+9 on the 17 line, 21.0.13+9 on the 21 line. A build below those
# predates the fixes, and anything that runs on it — including a consumer's application — inherits
# the vulnerable TLS stack.
#
# The check reads the vendor token `java -version` prints (`Temurin-17.0.21+9`), never a bare
# version number. Oracle's risk matrix lists the same "17.0.21" string under *affected* releases,
# so grepping the number alone cannot separate a fixed JVM from a vulnerable one; the vendor build
# string is the only line that can.
#
# 要求 PATH 上的 JDK 是 Temurin，且不低于带修复版 JSSE 实现的发行版：17 线 17.0.21+9、21 线 21.0.13+9。
# 低于它们的构建早于修复，跑在其上的一切 —— 包括使用者的应用 —— 都继承了有漏洞的 TLS 栈。
#
# 判据取 `java -version` 打印的厂商标识（`Temurin-17.0.21+9`），不取裸版本号。Oracle 风险矩阵把同一个
# 「17.0.21」串列在*受影响*版本下，只 grep 版本号分不出已修与未修；能分的只有这一行厂商标识。
set -euo pipefail

report="$(java -version 2>&1)"
printf '%s\n' "$report"

build="$(printf '%s\n' "$report" \
    | sed -n 's/.*Temurin-\([0-9][0-9.]*\)+\([0-9]\+\).*/\1+\2/p' \
    | head -1)"

if [ -z "$build" ]; then
    echo "::error::No Temurin build string in 'java -version' output; this repository pins the temurin distribution." >&2
    exit 1
fi

case "$build" in
    17.*) minimum='17.0.21+9' ;;
    21.*) minimum='21.0.13+9' ;;
    *)
        echo "::error::Unsupported JDK line '$build'; the build matrix covers 17 and 21." >&2
        exit 1
        ;;
esac

lowest="$(printf '%s\n%s\n' "$build" "$minimum" | sort -V | head -1)"
if [ "$lowest" != "$minimum" ]; then
    echo "::error::JDK $build predates $minimum, which carries the fixed JSSE implementations." >&2
    exit 1
fi

echo "JDK $build is at or above $minimum."
