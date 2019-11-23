package com.github.fserver.utils

import io.javalin.Javalin

import org.apache.pdfbox.pdmodel.PDDocument
// import org.apache.pdfbox.rendering.*
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.ImageType
import java.awt.Image
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
//import javax.imageio.ImageType

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

    fun expandPath(path: String): String
    {
        val p = path
                // Replace (.) dot for current user directory
                .replace("^\\.", System.getProperty("user.dir"))
                // User Desktop directory
                .replace("~", System.getProperty("user.home"))
        return java.io.File(p).absolutePath
    }

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


    fun setTSLServer(app: Javalin, port: Int,  password: String, certificateFile: String)
    {
        val ctxFactory = org.eclipse.jetty.util.ssl.SslContextFactory()
        ctxFactory.keyStorePath = certificateFile
        ctxFactory.setKeyStorePassword(password)

        app.config.server {
            val server = org.eclipse.jetty.server.Server()
            val sslConnector = org.eclipse.jetty.server.ServerConnector(server, ctxFactory)
            // TSL/SSL Port
            sslConnector.setPort(port)
            server.connectors = arrayOf(sslConnector)
            server
        }
        app.config.enforceSsl = true
    }

    /** Server works with HTTP and HTTPS URLs
     *
     * The server URLs become: http://<SERVER-ADDRESS>:<HTTP_PORT>/ for HTTP
     * and https://<SERVER-ADDRESS>:<HTTPS_PORT>/ for HTTPS
     *
     *  If the server is running listening ports 80 for HTTP and 443 for HTTPS
     *  the server URLs become: http://<SERVER-ADDRESS>/ for HTTP and
     *  https://<SERVER-ADDRESS> for HTTPS
     *
     * */
    fun setTSLServer(app: Javalin, port: Int, sslPort: Int,  password: String, certificateFile: String)
    {
        val ctxFactory = org.eclipse.jetty.util.ssl.SslContextFactory()
        ctxFactory.keyStorePath = certificateFile
        ctxFactory.setKeyStorePassword(password)

        app.config.server {
            val server = org.eclipse.jetty.server.Server()
            val sslConnector = org.eclipse.jetty.server.ServerConnector(server, ctxFactory)
            // TSL/SSL Port
            sslConnector.setPort(sslPort)
            val serverConnector = org.eclipse.jetty.server.ServerConnector(server)
            // Non SSL-TSL Port
            serverConnector.setPort(port)
            server.connectors = arrayOf(sslConnector, serverConnector)
            server
        }
        app.config.enforceSsl = true
    }

} // ------- End of object HttpUtils -----------//

object DocUtils
{
    fun readImage(file: String): BufferedImage
    {
        return javax.imageio.ImageIO.read(java.io.File(file))
    }

    /** Encode Image to Base64 String */
    fun toBase64String(img: BufferedImage, imgType: String = "png"): String
    {
        val bos = java.io.ByteArrayOutputStream()
        try {
            javax.imageio.ImageIO.write(img, imgType, bos)
            val bytes = bos.toByteArray()
            return String(java.util.Base64.getEncoder().encode(bytes))
        } finally {
            bos.close()
        }
    }

    fun getPDFNumberOfPages(pdfFile: String): Int
    {
        val file = java.io.File(pdfFile)
        val doc: PDDocument = PDDocument.load(file.inputStream())
        return doc.numberOfPages
    }

    fun readPDFPage(pageNum: Int, dpi: Float, pdfFile: String): BufferedImage
    {
        val file = java.io.File(pdfFile)
        val doc: PDDocument = PDDocument.load(file.inputStream())
        val prd  = PDFRenderer(doc)
        return prd.renderImageWithDPI(pageNum, dpi, ImageType.RGB)
    }

    fun readPDFPageAndScale(pageNum: Int, dpi: Float, scale: Double, pdfFile: String): BufferedImage
    {
        val image = readPDFPage(pageNum, dpi,  pdfFile)
        val width = (image.getWidth() * scale).toInt()
        val height = (image.getHeight() * scale).toInt()
        val img = image.getScaledInstance(width, height, BufferedImage.SCALE_SMOOTH)
        val bi = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val gx = bi.graphics
        gx.drawImage(img, 0, 0, null)
        return bi
    }

    fun readPDFPageGray(pageNum: Int, dpi: Float, pdfFile: String): BufferedImage
    {
        val file = java.io.File(pdfFile)
        val doc: PDDocument = PDDocument.load(file.inputStream())
        val prd  = PDFRenderer(doc)
        return prd.renderImageWithDPI(pageNum, dpi, ImageType.GRAY)
    }


    fun writePDFPageToStream(pageNum: Int, dpi: Float, pdfFle: String, output: java.io.OutputStream)
    {
        val bim: BufferedImage = readPDFPage(pageNum, dpi, pdfFle)
        ImageIO.write(bim, "PNG", output)
    }

    fun writePDFPageToStreamWithScale(pageNum: Int, dpi: Float, scale: Double
                                      , pdfFile: String, output: java.io.OutputStream): Unit
    {
        val image = readPDFPageAndScale(pageNum, dpi, scale, pdfFile)
        ImageIO.write(image, "PNG", output)
    }
}
