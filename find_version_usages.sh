#!/usr/bin/env bash

set -ex

pwd
echo ${BASH_SOURCE[0]}
echo $(dirname ${BASH_SOURCE[0]})
cd $(dirname ${BASH_SOURCE[0]})

gradleFiles="$(find . -name "*.gradle" -a -type f)"
for item in $(grep -Eo "\S*\.version" /Users/pivotal/workspace/geode/gradle/dependency-versions.properties) ; do
  echo ${item}
  grep "${item}" ${gradleFiles}
  echo
done


