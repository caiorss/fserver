
# Creates executable jar file fserver.sh, in this directory, embedded
# into a shell script.
#
all: execjar 

# fatjar_file := build/lib/fserver-fat.jar

shellScriptJar.jar: shellScriptJar.kt
	kotlinc shellScriptJar.kt -include-runtime -d shellScriptJar.jar

execjar: shellScriptJar.jar
	gradle fatJar
	java -jar shellScriptJar.jar  build/libs/fserver-fat.jar fserver.jsh 

