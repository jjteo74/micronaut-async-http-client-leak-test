#!/usr/bin/env bash

READLINK=$(which greadlink || which readlink)
DIR=$(${READLINK} -f $(dirname $0))

POSITIONAL=()
while [[ $# -gt 0 ]]
do
    key="$1"
    case ${key} in
        -t|--test)
        TEST_METHOD="$2"
        shift
        shift
        ;;
        -d|--delay)
        DELAY="$2"
        shift
        shift
        ;;
        -w|--worker-count)
        COUNT="$2"
        shift
        shift
        ;;
        *)    # unknown option
        echo "Unknown option $1"
        exit 1
        ;;
    esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

TEST_METHOD=${TEST_METHOD:-retrieve}
DELAY=${DELAY:-50ms}
COUNT=${COUNT:-1}

${DIR}/gradlew shadowJar && \
  java \
  -Xms24m \
  -Xmx32m \
  -XX:MaxDirectMemorySize=4m \
  -XX:MaxMetaspaceSize=64m \
  -XX:NativeMemoryTracking=detail \
  -Dio.netty.leakDetectionLevel=paranoid \
  -Dasync-http-client.test-method=${TEST_METHOD} \
  -Dasync-http-client.test-delay=${DELAY} \
  -Dasync-http-client.test-workers=${COUNT} \
  -jar ${DIR}/build/libs/buffer-leak-app-0.1-all.jar
