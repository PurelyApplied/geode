#!/usr/bin/env bash

set -e

pwd
echo ${BASH_SOURCE[0]}
echo $(dirname ${BASH_SOURCE[0]})
cd $(dirname ${BASH_SOURCE[0]})

gradleFiles="$(find . -name "*.gradle" -a -type f -a ! -name dependency-constraints.gradle)"

do-search () {
  for item in $(grep -Eo "\S*\.version" /Users/pivotal/workspace/geode/gradle/dependency-versions.properties) ; do
  #  echo ${item}
    grep -hE "\w+\('.*${item}" ${gradleFiles} | sed "s/^ *//"
    echo
  done
}

# Why does Mac sed behave so poorly..?
do-search \
 | sort -u \
 | sed "s/project\.'\(.*\)'/\1/" \
 | sed "s/' \+ \([^.]*\)\.\(.*\)/' + \1_\2/g" \
 | sed "s/' \+ \([^.]*\)\.\(.*\)/' + \1_\2/g" \
 | sed "s/' \+ \([^.]*\)\.\(.*\)/' + \1_\2/g" \
 | sed "s/' \+ \([^-]*\)-\(.*\)/' + \1_\2/g" \
 | sed "s/' \+ \([^-]*\)-\(.*\)/' + \1_\2/g" \
 | sed "s/' \+ \([^-]*\)-\(.*\)/' + \1_\2/g"
