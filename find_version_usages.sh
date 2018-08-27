#!/usr/bin/env bash

set -e

pwd
echo ${BASH_SOURCE[0]}
echo $(dirname ${BASH_SOURCE[0]})
cd $(dirname ${BASH_SOURCE[0]})

gradleFiles="$(find . -name "*.gradle" -a -type f -a ! -name dependency-constraints.gradle)"
for item in $(grep -Eo "\S*\.version" /Users/pivotal/workspace/geode/gradle/dependency-versions.properties) ; do
#  echo ${item}
  grep -hE "\w.*${item}" ${gradleFiles}
#  grep -hE "\w.*${item}" ${gradleFiles} | grep -Eo "^\s*\w*" | sort -u

  echo
done


