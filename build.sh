#!/bin/bash
[ -z "$JAVA_HOME" ] && export JAVA_HOME=/usr/jdk/jdk-9

export javac=$JAVA_HOME/bin/javac
export jar=$JAVA_HOME/bin/jar
export java=$JAVA_HOME/bin/java

rm -fr target/

# main
echo "compile cplisp source ..."
mkdir -p target/main/exploded
$javac -d target/main/exploded --module-path deps --module-source-path src/java/main --module fr.umlv.cplisp

echo "create cplisp jar ..."
mkdir -p target/main/artifact
$jar --create --file target/main/artifact/cplisp.jar --module-version=1.0 --main-class=fr.umlv.cplisp.CpLisp -C target/main/exploded/fr.umlv.cplisp .

# test
echo "(print hello cplisp)" | $java --module-path target/main/artifact:deps -m fr.umlv.cplisp

