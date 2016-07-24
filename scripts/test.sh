#!/bin/bash

set -e

function cleanup {
  SERVERPID=$(ps -f | grep "s3-server" | grep -v "grep" | awk '{print $2}')
  kill -9 $SERVERPID
  rm -rf $TESTDIR
}

trap cleanup EXIT

export AWS_ACCESS_KEY_ID="AAAAAAAAAAAAAAAAAAAA"
export AWS_SECRET_ACCESS_KEY="aAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaAaA"
export AWS_DEFAULT_PROTOCOL="HTTP"

TESTDIR=/tmp/coursier-s3

mkdir -p $TESTDIR

s3-server -d $TESTDIR -h localhost -p 4568 -b test-s3-coursier-com &

sbt -jvm-debug 9999 coursier-s3/test
