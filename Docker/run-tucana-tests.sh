#!/bin/bash -e

modelServerMainJar=$1
modelServerTestJar=$2
scalaTestJars="scalatest_2.11-3.0.5.jar:scalactic_2.11-3.0.5.jar"


modelTestClass=com.ilabs.dsi.modelserver.functionalTests.WebApiTests

scala -J-Xmx2g -cp "$scalaTestJars:$modelServerMainJar" org.scalatest.tools.Runner -o -R $modelServerTestJar -s $modelTestClass
