#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
java scripts/Build.java
exec java -Xmx4g -Dfile.encoding=UTF-8 -cp build/classes stud.g01.runner.LabRunner "$@"
