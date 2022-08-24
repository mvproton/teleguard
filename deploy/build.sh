#!/usr/bin/env bash

set -euo pipefail

if [ ! -d "classes" ]; then
  mkdir classes
else
  rm -rf classes/*
fi

rm target/teleguard.jar

clj -M:aot
clj -M:uberjar