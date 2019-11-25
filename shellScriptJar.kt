// Script for embedding Fat-jar into shell script:

fun copyStream(  from: java.io.InputStream
	       , to: java.io.OutputStream, bufferSizeKB: Int = 1024)
{
        val buffer = ByteArray(bufferSizeKB)
        var nRead: Int = 0
        while ( from.read(buffer).also { nRead = it } >=  0 )
        {
                to.write(buffer, 0, nRead)
        }
} // --- End of copyStream() ----// 
	
val payload = """#!/usr/bin/env sh
# Check if JAVA_HOME is Set 
if [ -n "${'$'}{JAVA_HOME}" ]
then
    # Check if JAVA is Installed in this JAVA_HOME
    if [ -f  "${'$'}JAVA_HOME/bin/java" ] ;
    then
        "${'$'}JAVA_HOME/bin/java" -jar "${'$'}0" "${'$'}@"
    # Try to use JAVA from ${'$'}PATH Variable
    else
        # Check if Java is Installed 
        if hash java 2>/dev/null;
        then
            java -jar "${'$'}0" "${'$'}@"
        else
            echo "Error: Java not available in PATH variable or in ${'$'}JAVA_HOME"
            echo "Make sure Java is installed"
            exit 1
        fi 
    fi 
else
    # Check if Java is Installed 
    if hash java 2>/dev/null;
    then
        java -jar "$0" "$@"
    else
        echo "Error: Java not available in PATH variable"
        echo "Make sure Java is installed"
        exit 1
    fi     
fi
exit 0
"""

// val inputFile: java.io.File = outputs.getFiles().getSingleFile()

fun main(args: Array<String>){
    // Input file: fat-jar 
    val inputFile = java.io.File(args[0])
    val outputFile = java.io.File(args[1])

    println(" inputFile = $inputFile")
    println(" outputFile = $outputFile")

    val fos = java.io.FileOutputStream(outputFile)

    val inch = java.io.FileInputStream(inputFile)
    // val payloadStream = java.io.ByteArrayInputStream(payload.getBytes("utf-8"))
    val payloadStream = java.io.ByteArrayInputStream(payload.toByteArray())
    copyStream(payloadStream, fos)
    copyStream(inch, fos)
    // Release acquired resources 
    payloadStream.close()
    inch.close()
    fos.close()


    // Mark Unix shell script as executable 
    java.lang.Runtime.getRuntime().exec("chmod u+x " + outputFile)
    println("Created  = " + outputFile)

    //println("Run it with java -jar $outFile or ./$outputFile or sh $outputFile")
 
}

