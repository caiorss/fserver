package samples

import io.javalin.Javalin


fun htmlLink(label: String, href: String): String
{
    // val uri = java.net.URI(href).toURL()
    return "<a href='$href'>$label</a>"
}

/**  Returns mime type of a given file name Requires Java7 */
fun getMimeType(file: java.io.File): String {

    val codeExtensions = arrayListOf<String>(
            // Programming language source code file extensions
            "py", ".sh", ".bat", ".psh", ".c", ".cpp", ".cxx", ".scala", ".kt"
            , ".kts", ".gradle", ".sbt", ".hpp", ".hxx", ".groovy", ".js", ".csv", ".json", ".m", ".jl"
            // Markdown file extension
            ,".md", ".org")
    val mimetype =
            if(codeExtensions.any { file.name.endsWith(it) })
                "text/plain; charset=utf-8"
            else
                java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    return mimetype
}

fun fileIsImage(file: java.io.File): Boolean
{
    val imageExtensions = arrayListOf<String>(".png", ".jpeg", ".jpg", ".tiff", ".bmp")
    return imageExtensions.any { file.name.endsWith(it) }
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

/** Note: It should be only used for small files */
fun responseFile(ctx: io.javalin.http.Context, file: java.io.File)
{
    val mimeType = getMimeType(file)
    ctx.contentType(mimeType)
    //val fsizeStr = file.length().toString()
    //ctx.header("Content-Length", fsizeStr)
    ctx.result(file.inputStream())
}

fun responseFileRange(ctx: io.javalin.http.Context, file: java.io.File)
{
    // Success response
    val mimeType = getMimeType(file)
    ctx.contentType(mimeType)
    ctx.header("Accept-Ranges", "bytes")

    val fileSize = file.length()
    ctx.header("Content-Length", fileSize.toString())
    println(" [INFO] File size = $fileSize")

    val rangeHeader = ctx.req.getHeader("Range")
    if(rangeHeader != null){
        val range = rangeHeader.removePrefix("bytes=").split("-")
        val from =  range[0].toLong()
        val to = if(range[1] == "-"  || range[1] == "") fileSize - 1 else range[1].toLong()

        println(" [TRACE] From = $from / to = $to  / fileSize = $fileSize")

        // val to = range[1].toLong()
        val fd = java.io.RandomAccessFile(file, "r")
        val buffer = ByteArray((to - from).toInt())
        fd.seek(from)
        val bytesRead = fd.read(buffer)
        val fs = java.io.ByteArrayInputStream(buffer)

        ctx.header("Content-Length", (bytesRead - from).toString())
        ctx.header("Content-Range", "bytes: $to-$bytesRead/${file.length()}")
        ctx.result(fs).status(206)
        return
    }

    println(" [TRACE] Requested range = $rangeHeader")

    ctx.result(file.inputStream())
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


    app.get("$route/*") dir@{ ctx ->
        val rawUriPath = ctx.req.requestURI.removePrefix(route + "/")
        val filename = decodeURL( rawUriPath )
        val file = java.io.File(path, filename.replace("..", ""))

        val showImageParam = ctx.queryParam<String>("image", "false").get() ?: "true"
        val showImageFlag = showImageParam == "true"
        println(" [INFO] ShowImage = $showImageParam")

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

            if(!showImageFlag)
                html += "</br> " + htmlLink("Show Images", ctx.req.requestURL.toString() + "?image=true")
            else
                html += "</br> " + htmlLink("Hide Images", ctx.req.requestURL.toString() + "?image=false")

            html += "<h2> Directories  </h2> \n"
            // List only directories and ignore hidden files dor directories (which names starts with '.' dot)
            for(f in file.listFiles{ f -> f.isDirectory
                                          && !f.name.startsWith(".")
                                          && !f.name.endsWith("~") }!!)
            {
                html += "<li>" + relativePathLink(root, f) + "</li> </br>"
            }

            html += "<h2> Files </h2> \n"
            // List only files and ignore hidden files directories (which name starts with '.' dot)
            for(f in file.listFiles{ f -> f.isFile
                                          && !f.name.startsWith(".")
                                          && !f.name.endsWith("~") }!!)
            {

                html += "</br> <li> " + relativePathLink(root, f) + "</li>"
                if(showImageFlag && fileIsImage(f))
                {
                    val relativePath = getRelativePath(root, f)
                    html += "\n </br> <img width='600px' src='$route/$relativePath'/>"
                }

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


class FileServer(val app: io.javalin.Javalin){
    data class StaticFileRoute(val route: String, val path: String)
    val routes = ArrayList<StaticFileRoute>()

    fun addDirectory(route: String,  path: String): FileServer
    {
        routes.add(StaticFileRoute(route, path))
        return this
    }

    fun run(port: Int) {

        var html = ""
        for(r in routes) {
            html += "\n <li> " + htmlLink("${r.route} ", r.route) + " => Directory = ${r.path} </li>"
        }
        app.get("/") { it.html(html) }
        for(r in routes) serveDirectory(app, r.route, r.path)
        //app.start(port)
    }
}





fun main(args: Array<String>)
{
    println(" [INFO] Server Running OK")

    val app = Javalin.create().start(7000)
    app.config.enableDevLogging()

    val fserver = FileServer(app)
            .addDirectory("/home", "/home/archbox")
            .addDirectory("/wiki", "/home/archbox/Documents/wiki")
            .addDirectory("/read", "/home/archbox/Desktop/must read")

    fserver.run(8000)

//    val app = Javalin.create()
//            .start(7000)
//
//    app.config.enableDevLogging()
//
//    serveDirectory(app, "/media", "/home/archbox")
//    serveDirectory(app, "/wiki", "/home/archbox/Documents/wiki")


    println(" [INFO] Server stopped")
}


