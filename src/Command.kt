package com.github.fserver.command

import io.javalin.Javalin

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

import com.github.fserver.http.FileServer

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

class CommandTest: com.github.ajalt.clikt.core.CliktCommand(
         name = "test"
        ,help = "Run server in demonstration mode." )
{
    val port: Int by option(help = "Http Server port (default 9080)").int().default(9080)
    val debug: Boolean by option(help = "Enable debug logging").flag()

    override fun run()
    {
        println(" [INFO] Server Running OK")

        val app = Javalin.create().start(port)
        if(debug) app.config.enableDevLogging()

        val fserver = FileServer(app)
                // Publish user's home directory
                .addDirectory("/home", System.getProperty("user.home"))
                // Publish user's desktop directory
                .addDirectory("/desktop", System.getProperty("user.home") + "/Desktop")
                // Downloads
                .addDirectory("/downloads", System.getProperty("user.home") + "/Downloads")

        fserver.run()

        println(" [INFO] Server stopped")
    }
}

fun main(args: Array<String>)
{
    val cli = CommandMain().subcommands(
              CommandConfigFile()
            , CommandTest())
    cli.main(args)
}


