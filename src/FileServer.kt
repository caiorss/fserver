package com.github.fserver.fserver

import io.javalin.Javalin
import org.slf4j.LoggerFactory


import com.github.fserver.utils.*

//import org.apache.pdfbox.pdmodel.*;
//import org.apache.pdfbox.rendering.*;

class FileServer()
{
    data class UserAuth(val userName: String, val password: String)
    data class StaticFileRoute(val diretoryLabel: String, val directoryPath: String)

    val imageEnabledCookie = "show-images"
    val routeToggleImage   = "/toggle-image"

    lateinit var mApp: Javalin
    val mRoutes = ArrayList<StaticFileRoute>()
    var mAuth: UserAuth? = null
    var mShowParams:   Boolean = false
    var mEnableUpload: Boolean = false

    fun enableUpload(flag: Boolean)
    {
        mEnableUpload = flag
    }

    fun enableAuthentication(user: String, password: String)
    {
        mAuth = UserAuth(user, password)
    }

    fun hasAuthentication(): Boolean
    {
        return mAuth != null
    }

    fun enableShowDirectoryPath(flag: Boolean)
    {
        mShowParams = flag
    }

    fun addDirectory(route: String,  path: String): FileServer
    {
        mRoutes.add(StaticFileRoute(route, HttpFileUtils.expandPath(path) ))
        return this
    }

    fun run(  port: Int = 9080
            , certificatePassword: String? = null
            , certificateFile: String? = null)
    {

        mApp = Javalin.create() // .start(port)

        if(certificateFile != null && certificatePassword != null)
        {
            // ------ Run with SSL/TSL communication encryption enabled. ----- //
            HttpUtils.setTSLServer(mApp, port, certificatePassword!!, certificateFile!!)
            mApp.config.enforceSsl = true
            mApp.start()
        } else
        {   // ------ Run without SSL/TSL communication encryption enabled. ----- //
            mApp.start(port)
        }

//        mApp.before { ctx ->
//            println("\n =>> [REQUEST] ctx.path = ${ctx.path()} port = ${ctx.req.serverPort} url = ${ctx.url()}  => ${ctx.req.requestURL} " )
//            if(ctx.req.serverPort == httpPort)
//            {
//                val reqUrl = java.net.URL(ctx.url())
//                val url = java.net.URL("https", reqUrl.host, httpsPort, reqUrl.file)
//                println("\n =>> URL2: = " + url)
//                ctx.redirect(url.toString(), 302)
//            }
//
//        }

        val logger = LoggerFactory.getLogger(FileServer::class.java)

        // Index page
        mApp.get("/", this::pageIndex)
        mApp.get(routeToggleImage, this::routeToggleImageDisplay)
        for(r in mRoutes) this.pageServeDirectory(mApp, r.diretoryLabel, r.directoryPath)

        // Resource/assets pages  http://<hostaddr>/assets/favicon.png
        // Assets are files are stored in the jar file (zip file)
        HttpUtils.addResourceRoute(mApp,"/assets", "/assets")

        // Set up basic http authentication
        // HttpUtils.basicAuthentication(app, "myuser", "mypass")
        if(this.hasAuthentication())
            this.installFormAuthentication(mApp
                    , loginFormPage = "/assets/login.html"
                    , userName = mAuth!!.userName
                    , userPass = mAuth!!.password
            )

        // Set up REQUEST logging
        mApp.before { ctx ->
            logger.info("[REQUEST] => "
                    + " ID = ${ctx.hashCode()} "
                    + " THID = ${Thread.currentThread().id} "
                    + " Origin = ${ctx.ip()} "
                    + "; Method = ${ctx.method()} "
                    + "; Path = ${ctx.path()} "
                    + "; UserAgent = '${ctx.userAgent()}' ")
        }

        // Set up RESPONSE logging
        mApp.after { ctx ->
            logger.info("[RESPONSE] => "
                    + " ID = ${ctx.hashCode()} "
                    + " THID = ${Thread.currentThread().id} "
                    + " Origin = ${ctx.ip()} "
                    + "; Method = ${ctx.method()} "
                    + "; Status = ${ctx.status()} "
            )
        }

    }

    // page: http://<hostaddress>/
    fun pageIndex(ctx: io.javalin.http.Context)
    {
        var resp = "<h2>Shared Directory</h2>"
        for(r in mRoutes) {
            resp += "\n <br><br> Directory: " + HttpUtils.htmlLink(r.diretoryLabel, "/directory/${r.diretoryLabel}")
            if(mShowParams) resp += "\n <li> => ${r.directoryPath} </li>"
        }
        val html = if(this.hasAuthentication())
            TemplateLoader.basicPage("<a href=\"/user-logout\">Logout</a>", resp)
        else
            TemplateLoader.basicPage("", resp)

        ctx.html(html)
    }

    // page: http://<hostaddress>/toggle-image
    fun routeToggleImageDisplay(ctx: io.javalin.http.Context)
    {
        val url = ctx.queryParam<String>("url").get()
        val imagesEnabledCookieValue = ctx.sessionAttribute<Boolean>(imageEnabledCookie) ?: false
        ctx.sessionAttribute(imageEnabledCookie, !imagesEnabledCookieValue)
        ctx.redirect(url, 302)
    }

    fun pagePDFThumbnailImage(ctx: io.javalin.http.Context, directoryPath: String)
    {
        val rawUriPath = ctx.queryParam("pdf")
        val pdfFile = java.io.File(directoryPath, rawUriPath)

        if(!pdfFile.exists()){
            ctx.result(" Error 404 - file not found. Unable to find file: $pdfFile")
               .status(404)
            return
        }

        DocUtils.writePDFPageToStream(0, pdfFile.toString(), ctx.res.outputStream)
        ctx.header("Content-type", "image/jpeg")
    }

    //  page: http://<hostaddress>/directory/<DIRECTORY-SHARED>
    fun pageServeDirectory(app: Javalin, routeLabel: String, path: String, showIndex: Boolean = true)
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

        if (mEnableUpload)
            app.post("/upload/$routeLabel/*") upload@{ ctx ->
                val rawUriPath = ctx.req.requestURI.removePrefix("/upload/$routeLabel/")
                val filename = HttpUtils.decodeURL(rawUriPath)
                val destination = java.io.File(path, filename.replace("..", ""))

                println(" [TRACE] destination = $destination ")

                if (!destination.isDirectory) {
                    ctx.result("Error: cannot upload to this location.").status(404)
                    return@upload
                }

                ctx.uploadedFiles("files").forEach { fdata ->
                    val fupload = java.io.File(destination, fdata.filename)
                    val out = fupload.outputStream()
                    HttpFileUtils.copyStream(fdata.content, out)
                    out.close()
                    println(" [TRACE] Written file: ${fdata.filename} to $fupload ")
                }
                ctx.status(302)
                val url = "/directory/$routeLabel/$filename"
                println(" [TRACE] redirect URL = $url ")
                ctx.redirect(url)
            }

        app.get("/pdf-thumbnail/$routeLabel") { ctx ->
            pagePDFThumbnailImage(ctx, path)
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
                val parentRelativePath = HttpFileUtils.getRelativePath(root, file.parentFile)

                if(parentRelativePath != ".")
                    htmlHeader += HttpUtils.htmlLink("Parent (..)", "$route/$parentRelativePath")

                val imagesEnabled = ctx.sessionAttribute<Boolean>(imageEnabledCookie) ?: false

                if(imagesEnabled)
                    htmlHeader += " / " + HttpUtils.htmlLink("Hide"
                            , routeToggleImage + "?url=" + ctx.req.requestURL.toString())
                else
                    htmlHeader += " / " + HttpUtils.htmlLink("Show"
                            , routeToggleImage + "?url=" + ctx.req.requestURL.toString())

                if(this.hasAuthentication())
                    htmlHeader += " / <a href=\"/user-logout\">Logout</a> "

                if(mEnableUpload)
                {
                    val relativePath = HttpFileUtils.getRelativePath(root, file)
                    htmlHeader += """ 
                    <form method="post" action="/upload/$routeLabel/$relativePath" enctype="multipart/form-data">
                        <button>Upload</button>
                        <input type="file" name="files" multiple> 
                    </form>
                """.trimIndent()
                }


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

                    if(f.toString().endsWith(".pdf"))
                    {
                        //val b64Image = DocUtils.readPDFPageAsHtmlBase64Image(0, f.toString())
                        // pw.println("\n <br> $b64Image")
                        val relPath = HttpFileUtils.getRelativePath(root, f)
                        val fileLink = relativePathLink(root, f)
                        pw.println("\n <br> " +
                                "<a href='$route/$relPath'> " +
                                "  <img src='/pdf-thumbnail/$routeLabel?pdf=$relPath' style='max-height: 200px; max-width: 200px;' /> " +
                                "</a> <br><br>")
                    }

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


    private fun installFormAuthentication(  app: Javalin
                                          , loginFormPage: String
                                          , userName: String
                                          , userPass: String ): Unit
    {
        val userLoggedAttribute = "user-logged"
        val loginValidationRoute = "/user-login"

        /*  Note: The form html tag must have the following format:
         *    <form method='post' action='/user-login'>
         *
         *  The user name input must be named as 'username' and
         * the password input be named as 'password'. for instance:
         *
         *    <input type="text" placeholder="Username" name="username" required>
         *     <input type="password" placeholder="Password" name="password" required>
         */
        app.post(loginValidationRoute)  { ctx ->
            println(" [TRACE] Enter validation route")
            val user = ctx.formParam("username", "")
            val pass = ctx.formParam( "password", "")

            // println(" [TRACE] user = $user - pass = $pass")

            if(user == userName && pass == userPass)
            {
                ctx.sessionAttribute(userLoggedAttribute, true)
                ctx.redirect("/", 302)
            } else
            {
                ctx.redirect(loginFormPage, 302)
            }
        }

        app.get("/user-logout") { ctx ->
            ctx.sessionAttribute(userLoggedAttribute, false)
            ctx.redirect(loginFormPage, 302)
        }

        app.config.accessManager gate@ { handler, ctx, permittedRoles ->
            val isLogged = ctx.sessionAttribute<Boolean>(userLoggedAttribute) ?: false
            if(ctx.path() == loginFormPage || ctx.path() == loginValidationRoute) {
                handler.handle(ctx)
                return@gate
            }
            if(!isLogged) {
                // println(" [TRACE] Access denied => Redirect to login page => URL = ${ctx.path()}.")
                ctx.redirect(loginFormPage, 302)
                return@gate
            }
            handler.handle(ctx)
        }
    }


} // ----- End of class FileServer --------//

