package samples

import io.javalin.Javalin
import io.javalin.http.staticfiles.Location


fun htmlLink(label: String, href: String): String
{
    return "<a href='$href'>$label</a>"
}

/**  Returns mime type of a given file name Requires Java7 */
fun getMimeType(file: java.io.File): String {

    val codeExtensions = arrayListOf<String>(
            // Programming language source code file extensions
            "py", ".sh", ".bat", ".psh", ".c", ".cpp", ".cxx", ".scala", ".kt"
            , ".kts", ".gradle", ".sbt", ".hpp", ".hxx", ".groovy"
            // Markdown file extension
            ,".md", ".org")
    val mimetype =
            if(codeExtensions.any { file.name.endsWith(it) })
                "text/plain; charset=utf-8"
            else
                java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    return mimetype
}

fun getRelativePath(root: java.io.File, path: java.io.File): String
{
    return root.toURI().relativize(path.toURI()).path
}

fun decodeURL(url: String): String
{
      //    return java.net.URLDecoder.decode(url
    //            , java.nio.charset.StandardCharsets.UTF_8.name())
    return java.net.URLDecoder.decode(url.replace("+", "%2B"),
            "UTF-8").replace("%2B", "+")
}


fun serveDirectory(app: Javalin, route: String, path: String, showIndex: Boolean = true)
{
    val root = java.io.File(path)

    fun relativePathLink(root: java.io.File, file: java.io.File): String
    {
        val relativePath = getRelativePath(root, file)
        if(file.isDirectory)
            return htmlLink(file.name + "/",  "$route/$relativePath")
        else
            return htmlLink(file.name,  "$route/$relativePath")
    }

    fun responseFile(ctx: io.javalin.http.Context, file: java.io.File)
    {
        // Success response
        val mimeType = getMimeType(file)
        // ctx.contentType(mimeType)
        ctx.contentType(mimeType)
        ctx.result(file.inputStream())
    }

    app.get("$route/*") dir@{ ctx ->
        val rawUriPath = ctx.req.requestURI.removePrefix(route + "/")
        val filename = decodeURL( rawUriPath )
        val file = java.io.File(path, filename.replace("..", ""))

        println(" INFO rawPath = $rawUriPath ; filename = $filename ; file = $file")

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
            responseFile(ctx, indexHtml)
            return@dir
        }

        if(file.isDirectory)
        {
            var html = ""
            html += "<h1>Listing Directory: ./${getRelativePath(root, file)}  </h1>"

            // val relativePath = root.toURI().relativize(file.parentFile.toURI()).path
            val relativePath = getRelativePath(root, file.parentFile)
            if(relativePath != ".")
                html += htmlLink("Go to parent (..)", "$route/$relativePath")


            html += "<h2> Directories  </h2> \n"
            // List only directories and ignore hidden files dor directories (which names starts with '.' dot)
            for(f in file.listFiles{ f -> f.isDirectory
                                          && !f.name.startsWith(".")
                                          && !f.name.endsWith("~") }!!)
            {
                html += relativePathLink(root, f) + "</br>"
            }

            html += "<h2> Files </h2> \n"
            // List only files and ignore hidden files directories (which name starts with '.' dot)
            for(f in file.listFiles{ f -> f.isFile
                                          && !f.name.startsWith(".")
                                          && !f.name.endsWith("~") }!!)
            {
                html += relativePathLink(root, f) + "</br>"
                // html += "<a href='$route/?file=$relativePath'> ${f.name} </a> </br>"
            }

            ctx.html(html)
            return@dir
        }

        // Success response
        responseFile(ctx, file)
    }

    app.get(route) { ctx -> ctx.redirect("$route/", 302)}

} //--- End of function serveDirectory() --- //

fun main(args: Array<String>)
{
    println(" [INFO] Server Running OK")

    val app = Javalin.create().start(7000)
    app.get("/") { ctx -> ctx.result("Hello World") }

    app.config.addStaticFiles("/etc", Location.EXTERNAL)

    /**
     *  Route: http://<HOST ADDRESS>/file?f=relative/path/to/file.txt
     * */
    app.get( "/file") { ctx ->
        //val filename = ctx.pathParam("filename")
        val filename = ctx.queryParam<String>("f").get()
        val file = java.io.File("/etc", filename.replace("..", ""))

        if(!file.isFile) {
            // Error Response
            ctx.result("Error file $file not found.")
               .status(404)
        }
        else {
            // Success response
            ctx.result(file.inputStream())
            ctx.contentType("text/plain")
        }
    }

    serveDirectory(app, "/media", "/home/archbox")
    serveDirectory(app, "/wiki", "/home/archbox/Documents/wiki")

    app.get( "/html") { ctx ->
                    // Success response

        val imageURl = "https://www.recantha.co.uk/blog/wp-content/uploads/2017/02/fallout_4_terminal.jpg"
        ctx.html("<h1> My Web Page title</h1>"
                    + "\n <img width=400px src='$imageURl'></img> ")

    }


        println(" [INFO] Server stopped")
}


