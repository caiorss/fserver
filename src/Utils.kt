package com.github.fserver.utils

import io.javalin.Javalin
import io.javalin.http.Context

class TemplateLoader(){
    private lateinit var mHtml: String

    fun html(): String { return mHtml   }

    fun loadAsset(file: String): TemplateLoader
    {
        mHtml = javaClass.getResource(file).readText()
        return this
    }

    fun set(tag: String, value: String): TemplateLoader
    {
        mHtml = mHtml.replace("{{$tag}}", value)
        return this
    }

    companion object {
        fun basicPage(header: String, content: String): String
        {
            return TemplateLoader()
                    .loadAsset("/template/basic_page.html")
                    .set("CONTENT", content)
                    .set("HEADER", header)
                    .html()

        }
    }

}


object HttpFileUtils
{
    fun copyStream(from: java.io.InputStream, to: java.io.OutputStream, bufferSizeKB: Int = 1024)
    {
        val buffer = ByteArray(bufferSizeKB)
        var nRead: Int = 0
        while ( from.read(buffer).also { nRead = it } >=  0 )
        {
            to.write(buffer, 0, nRead)
        }
    }

    /**  Returns mime type of a given file name Requires Java7 */
    fun getMimeType(file: java.io.File): String {

        val codeExtensions = arrayListOf<String>(
                // Programming language source code file extensions
                "py", ".tcl", ".rb", ".sh", ".bat", ".psh", ".c", ".cpp", ".cxx", ".scala", ".kt"
                , ".kts", ".gradle", ".sbt", ".hpp", ".hxx", ".groovy", ".js", ".csv", ".json", ".m", ".jl", ".php"
                // Markdown file extension
                ,".md", ".org", ".desktop")
        val mimetype =
                if(codeExtensions.any { file.name.toLowerCase().endsWith(it) })
                    "text/plain; charset=utf-8"
                else
                    java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        return mimetype
    }

    /** Returns true if file is image */
    fun fileIsImage(file: java.io.File): Boolean
    {
        val exts = arrayListOf<String>(".png", ".jpeg", ".jpg", ".tiff", ".bmp", ".ico", ".gif")
        return exts.any { file.name.toLowerCase().endsWith(it) }
    }

    /** Returns true if file is audio or video */
    fun fileIsMediaAV(file: java.io.File): Boolean
    {
        val exts = arrayListOf<String>(
                ".mpg", ".mp4", ".mkv", ".avi", ".webm", ".ogg"
                , ".mp3", ".acc", ".mid", ".midi", ".oga", ".ogg"
                , ".opus", ".3gp", ".3g2")
        return exts.any { file.name.toLowerCase().endsWith(it) }
    }


    fun getRelativePath(root: java.io.File, path: java.io.File): String
    {
        return root.toURI().relativize(path.toURI()).path
    }

} // --- End of class HttpFileUtils --------------//

object HttpUtils
{

    fun decodeURL(url: String): String
    {
        //    return java.net.URLDecoder.decode(url
        //            , java.nio.charset.StandardCharsets.UTF_8.name())
        return java.net.URLDecoder.decode(url.replace("+", "%2B"),
                "UTF-8").replace("%2B", "+")
    }

    fun htmlLink(label: String, href: String): String
    {
        // val uri = java.net.URI(href).toURL()
        return "<a href='$href'>$label</a>"
    }


    /** Note: It should be only used for small files */
    fun responseFile(ctx: io.javalin.http.Context, file: java.io.File)
    {
        val mimeType = HttpFileUtils.getMimeType(file)
        ctx.contentType(mimeType)
        //val fsizeStr = file.length().toString()
        //ctx.header("Content-Length", fsizeStr)
        ctx.result(file.inputStream())
    }

    fun addResourceRoute(app: Javalin, route: String, path: String)
    {
        // javaClass.getResource().readText()
        app.get("$route/*") { ctx ->
            val rawUriPath = ctx.req.requestURI.removePrefix(route + "/")
            val filename = HttpUtils.decodeURL( rawUriPath )
            val file = java.io.File(path, filename.replace("..", ""))
            val assetURL = javaClass.getResource(file.toString())
            val mimetype = HttpFileUtils.getMimeType(file)
            if(assetURL != null)
                ctx.result(assetURL.openStream()).contentType(mimetype)
            else
                ctx.result("Error: file not found resource:/$file")
                        .status(404)
        }
    }

    fun responseFileRange(ctx: io.javalin.http.Context, file: java.io.File)
    {
        // Success response
        val mimeType = HttpFileUtils.getMimeType(file)
        ctx.contentType(mimeType)
        ctx.header("Accept-Ranges", "bytes")

        val fileSize = file.length()
        ctx.header("Content-Length", fileSize.toString())
        // println(" [INFO] File size = $fileSize")

        val rangeHeader = ctx.req.getHeader("Range")
        if(rangeHeader != null){
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val from =  range[0].toLong()
            val to = if(range[1] == "-"  || range[1] == "") fileSize - 1 else range[1].toLong()

            // println(" [TRACE] From = $from / to = $to  / fileSize = $fileSize")

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

        // println(" [TRACE] Requested range = $rangeHeader")

        ctx.result(file.inputStream())
    }

    /** Add basic authentication to all resources (routes)
     *  Note: This implementation uses no Cookies
     *
     * See:
     *  + https://en.wikipedia.org/wiki/Basic_access_authentication
     *  + https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication
     *
     */
    fun basicAuthentication( app: Javalin
                            , userName: String
                            , userPass: String ): Unit

    {
        val bytes = ("$userName:$userPass").toByteArray()
        val secret = java.util.Base64.getEncoder().encodeToString(bytes)

        app.config.accessManager { handler, ctx, permittedRoles ->
            val auth = ctx.header("Authorization")
            if(auth == null || auth != "Basic " + secret)
            {
                ctx.result("Error: 401 - Unauthorized access")
                        .status(401)
                        .header("Www-Authenticate"
                                , "Basic realm=\"Fake Realm\"")
            } else
            { // successful Authentication
                handler.handle(ctx)
            }

        }
    }


} // ------- End of object HttpUtils -----------//


