package com.github.fserver.command

import io.javalin.Javalin

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

import com.github.fserver.fserver.FileServer

class CommandMain: com.github.ajalt.clikt.core.CliktCommand()
{
    override fun run() = Unit
}

class CommandConfigFile: com.github.ajalt.clikt.core.CliktCommand(
         name = "config"
        ,help = "Start server from user-provided configuration file. " )
{
    private val file: String by argument(help = "TOML Configuration file")

    override fun run()
    {
        println(" Configuration file = $file")
    }
}

class CommandServerSingleDirectory: com.github.ajalt.clikt.core.CliktCommand(
        name = "dir"
        ,help = "Serve a single directory" )
{
    private val path: String by argument(help = "Directory to be served")
    private val port: Int by option(help = "Http Server port (default 9080)").int().default(9080)

    override fun run()
    {
        FileServer().addDirectory(java.io.File(path).name, path).run(port)
    }
}

class CommandServerMultipleDirectory: com.github.ajalt.clikt.core.CliktCommand(
         name = "mdir"
        ,help = "Serve multiple directory" )
{
    private val pathlist by argument(help = "Directories => <label>:<directory> to be served")
            .multiple()
    private val port: Int by option(help = "Http Server port (default 9080)").int().default(9080)

    override fun run()
    {
        val server = FileServer()

        if(pathlist.isEmpty()){
            println("Error: required at least a single pair <LABEL>:<DIRECTORY> as argument.")
            return
        }

        pathlist.forEach { p ->
            val (label, path) = p.split(":")
            server.addDirectory(label, path)
        }
        server.run(port)
    }
}


class CommandTest: com.github.ajalt.clikt.core.CliktCommand(
         name = "test"
        ,help = "Run server in demonstration mode." )
{
    private val port: Int by option(help = "Http Server port (default 9080)").int().default(9080)
    // val debug: Boolean by option(help = "Enable debug logging").flag()

    override fun run()
    {
        println(" [INFO] Server Running OK")

        // if(debug) app.config.enableDevLogging()

        val fserver = FileServer()
                // Publish user's home directory
                .addDirectory("home", System.getProperty("user.home"))
                // Publish user's desktop directory
                .addDirectory("desktop", System.getProperty("user.home") + "/Desktop")
                // Downloads
                .addDirectory("downloads", System.getProperty("user.home") + "/Downloads")

        fserver.run(port)

        println(" [INFO] Server stopped")
    }
}

class CommandDummy: com.github.ajalt.clikt.core.CliktCommand(
         name = "dummy"
        ,help = "Dummy command" )
{
    override fun run()
    {
//        val text = javaClass::class.java.getResource("/assets/protocols.txt").readText()
//        println(" Resource = ")
//        println(text)
    }
}



fun main(args: Array<String>)
{
    val cli = CommandMain().subcommands(
              CommandServerSingleDirectory()
            , CommandServerMultipleDirectory()
            , CommandConfigFile()
            , CommandTest()
            , CommandDummy() )
    cli.main(args)
}


