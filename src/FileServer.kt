package com.github.fserver.fserver

import io.javalin.Javalin
import org.slf4j.LoggerFactory

import com.github.fserver.utils.*

class FileServer(port: Int)
{
    data class StaticFileRoute(val route: String, val path: String)
    val imageEnabledCookie = "images-enabled"
    val routeToggleImage   = "/toggle-image"

    val app = Javalin.create().start(port)
    val routes = ArrayList<StaticFileRoute>()

    fun addDirectory(route: String,  path: String): FileServer
    {
        routes.add(StaticFileRoute(route, path))
        return this
    }

    fun run() {

        val logger = LoggerFactory.getLogger(FileServer::class.java)

        app.before { ctx ->
            logger.info("[REQUEST] => " +
                    " Origin = ${ctx.ip()} " +
                    "; Method = ${ctx.method()} " +
                    "; Path = ${ctx.path()} " +
                    "; UserAgent = '${ctx.userAgent()}' ")
        }

        HttpUtils.addResourceRoute(app,"/assets", "/assets")

        var resp = "<h2>Shared Directory</h2>"
        for(r in routes) {
            resp += "\n <br><br> Directory: " + HttpUtils.htmlLink(r.route, "/directory/${r.route}")
            resp += "\n <li> => ${r.path} </li>"
        }
        val html = TemplateLoader.basicPage("", resp)
        // Index route
        app.get("/") { it.html(html) }


        app.get(routeToggleImage) { ctx ->
            val url = ctx.queryParam<String>("url").get()

            val imageEnabledCookieValue = ctx.cookie(imageEnabledCookie) ?: "false"
            if(imageEnabledCookieValue == "false")
                ctx.cookie(imageEnabledCookie, "true")
            else
                ctx.cookie(imageEnabledCookie, "false")

            ctx.redirect(url, 302)
        }

        for(r in routes) this.serveDirectory(app, r.route, r.path)
        //app.start(port)

    }

    fun serveDirectory(app: Javalin, routeLabel: String, path: String, showIndex: Boolean = true)
    {
        val root = java.io.File(path)

        val route = "/directory/$routeLabel"

        fun relativePathLink(root: java.io.File, file: java.io.File): String
        {
            val relativePath = HttpFileUtils.getRelativePath(root, file)
            if(file.isDirectory)
                return HttpUtils.htmlLink(file.name + "/",  "$route/$relativePath")
            else
                return HttpUtils.htmlLink(file.name,  "$route/$relativePath")
        }


        app.get("$route/*") dir@{ ctx ->

            val rawUriPath = ctx.req.requestURI.removePrefix(route + "/")
            val filename = HttpUtils.decodeURL( rawUriPath )
            val file = java.io.File(path, filename.replace("..", ""))

            if(!file.exists()) {
                // Error Response
                ctx.result("Error file $file not found.")
                        .status(404)
                // Early return
                return@dir
            }

            val indexHtml = java.io.File(file, "index.html")

            if(file.isDirectory && showIndex && indexHtml.isFile)
            {
                HttpUtils.responseFile(ctx, indexHtml)
                return@dir
            }

            if(file.isDirectory)
            {
                var htmlHeader = HttpUtils.htmlLink("Top", "/") + " / "

                val writer = java.io.StringWriter()
                val pw = java.io.PrintWriter(writer, true)


                pw.println("<h2>Listing Directory: ./${HttpFileUtils.getRelativePath(root, file)}  </h2>")

                // val relativePath = root.toURI().relativize(file.parentFile.toURI()).path
                val relativePath = HttpFileUtils.getRelativePath(root, file.parentFile)

                if(relativePath != ".")
                    htmlHeader += HttpUtils.htmlLink("Go to parent (..)", "$route/$relativePath")

                val imageEnabledCookieValue = ctx.cookie(imageEnabledCookie) ?: "false"
                val imagesEnabled = imageEnabledCookieValue == "true"

                if(imagesEnabled)
                    htmlHeader += " / " + HttpUtils.htmlLink("Hide Images"
                            , routeToggleImage + "?url=" + ctx.req.requestURL.toString())
                else
                    htmlHeader += " / " + HttpUtils.htmlLink("Show Images"
                            , routeToggleImage + "?url=" + ctx.req.requestURL.toString())

                pw.println("<h3> Directories  </h3>")
                // List only directories and ignore hidden files dor directories (which names starts with '.' dot)

                val dirList = file.listFiles{ f -> f.isDirectory
                        && !f.name.startsWith(".")
                        && !f.name.endsWith("~") }

                for(f in dirList.toList().sortedBy { it.name?.toLowerCase() })
                {
                    pw.println("<li>" + relativePathLink(root, f) + "</li> <br>")
                }

                pw.println("<h3> Files </h3> \n")
                // List only files and ignore hidden files directories (which name starts with '.' dot)

                val fileList = file.listFiles{ f -> f.isFile
                        && !f.name.startsWith(".")
                        && !f.name.endsWith("~") }

                for(f in fileList.toList().sortedBy { it.name?.toLowerCase() })
                {
                    pw.println("<br> <li> " + relativePathLink(root, f) + "</li>")
                    if(imagesEnabled && HttpFileUtils.fileIsImage(f))
                    {
                        val relativePath = HttpFileUtils.getRelativePath(root, f)
                        pw.println( "\n <br> <img width='600px' src='$route/$relativePath'/>" )
                    }

                }

                ctx.html(TemplateLoader.basicPage(htmlHeader, writer.toString()))
                return@dir
            }

            // Success response
            if(HttpFileUtils.fileIsMediaAV(file))
                HttpUtils.responseFileRange(ctx, file)
            else
                HttpUtils.responseFile(ctx, file)
        }

        if(route != "/")
            app.get(route) { ctx -> ctx.redirect("$route/", 302)}

    } //--- End of function serveDirectory() --- //

}

