#!/usr/bin/env bash
export JAVA_HOME="$HOME/.local/share/java/jdk-17.0.19+10"
export MAVEN_HOME="$HOME/.local/share/apache-maven-3.9.16"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$HOME/.local/bin:$PATH"
cd /home/weilai/CodeProject/iot-pt/backend
exec mvn -q spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
    -Dserver.port=33412 \
    -DMYSQL_HOST=localhost -DMYSQL_PORT=33402 \
    -DTDENGINE_HOST=localhost -DTDENGINE_PORT=33403 \
    -DMQTT_BROKER=tcp://localhost:33405 \
    -DTCP_PORT=33410"
