package com.github.fserver.fserver

import io.javalin.Javalin
import org.slf4j.LoggerFactory

import com.github.fserver.html.HtmlBuilder as Html
import com.github.fserver.utils.*
import org.apache.pdfbox.pdmodel.PDDocument

//import org.apache.pdfbox.pdmodel.*;
//import org.apache.pdfbox.rendering.*;

class FileServer()
{
    private val html_basicPage = "/template/basic_page.html"

    data class UserAuth(val userName: String, val password: String)
    data class StaticFileRoute(val diretoryLabel: String, val directoryPath: String)

    val imageEnabledCookie = "show-images"
    val routeToggleImage   = "/toggle-image"

    lateinit var mApp: Javalin
    val mRoutes = ArrayList<StaticFileRoute>()
    var mAuth: UserAuth? = null
    var mEnableShowDirectory  = false
    var mEnableUpload         = false
    var mEnabledTSL           = false
    var mPort: Int = 9080

    // Experimental Feature
    var mEnablePDFThumbnail: Boolean = false

    fun enableUpload(flag: Boolean)
    {
        mEnableUpload = flag
    }

    fun enableAuthentication(user: String, password: String)
    {
        mAuth = UserAuth(user, password)
    }

    fun enablePDFThumbnail(flag: Boolean)
    {
        mEnablePDFThumbnail = flag
    }

    fun hasAuthentication(): Boolean
    {
        return mAuth != null
    }

    fun enableShowDirectoryPath(flag: Boolean)
    {
        mEnableShowDirectory = flag
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
        mPort = port

        if(certificateFile != null && certificatePassword != null)
        {

            // ------ Run with SSL/TSL communication encryption enabled. ----- //
            HttpUtils.setTSLServer(mApp, port, certificatePassword!!, certificateFile!!)
            mEnabledTSL = true
            mApp.config.enforceSsl = true
            mApp.start()
        } else
        {   // ------ Run without SSL/TSL communication encryption enabled. ----- //
            mEnabledTSL = false
            mApp.start(port)
        }

        println(" [INFO] File Web Server URL => ${this.getServerURL()} ")
        println(" ---------------------------------------------------- ")

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

    fun getServerURL(): String? {
        val localIpAddress = HttpUtils.GetLocalNetworkAddress()
        val protocol = if(mEnabledTSL) "https" else "http"
        if(localIpAddress != null)
            return "$protocol://$localIpAddress:$mPort"
        return null
    }

    // page: http://<hostaddress>/
    fun pageIndex(ctx: io.javalin.http.Context)
    {
        val content = Html.many {

            a {
                href = "https://github.com/caiorss/fserver"
                label = "Project's Repository"
            }

            val serverURL = getServerURL()
            if(serverURL != null)
            {
                p("Server URL => $serverURL")
            }


            h2("Shared Directories ")

            for(r in mRoutes)
            {
                t("Directory: ")
                a(label = r.diretoryLabel, href = "/directory/${r.diretoryLabel}") { }
                if(mEnableShowDirectory) li {
                    t(" => ${r.directoryPath} ")
                }
                br()
            }
        }

        val logoutLink = if(this.hasAuthentication())
            Html.a("/user-logout", "Logout")
        else
            Html.empty()

        val html = TemplateLoader().loadAsset(html_basicPage)
                .set("CONTENT", content.render())
                .set("HEADER",  logoutLink.render())
                .set("TITLE",   "Index / FServer - Micro Http File Server")
                .html()

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

        val thumbnailsDir = java.io.File(pdfFile.parent, ".pdf-thumbnail")
        thumbnailsDir.mkdir()


        // Get image file from cache directory
        val imgFile = java.io.File(thumbnailsDir, pdfFile.nameWithoutExtension + ".jpeg")
        if(!imgFile.exists()) {
            DocUtils.writePDFPageToStreamWithScale(
                        pageNum = 0
                      , dpi     = 96.0f
                      , scale   = 0.25
                      , pdfFile = pdfFile.toString()
                      , output  = imgFile.outputStream())
        }

        ctx.result(imgFile.inputStream())
        ctx.contentType("image/jpeg")
    }


    fun listDirectoryResponse( ctx: io.javalin.http.Context
                              , routeLabel: String
                              , path: String
                              , file: java.io.File)
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

        val imagesEnabled = ctx.sessionAttribute<Boolean>(imageEnabledCookie) ?: false

        val header = Html.many {
            a("/", "Top"){ }
            t(" / ")

            // val relativePath = root.toURI().relativize(file.parentFile.toURI()).path
            val parentRelativePath = HttpFileUtils.getRelativePath(root, file.parentFile)

            if(parentRelativePath != ".")
                a(href = "$route/$parentRelativePath", label = "Parent (..)"){ }


            // Hyperlink for toggling image view
            a {
                label = if(imagesEnabled) "Hide" else "Show"
                href = routeToggleImage + "?url=" + ctx.req.requestURL.toString()
            }

            if(hasAuthentication())  a( href ="/user-logout", label = "Logout") { }


            if(mEnableUpload)
                t {
                    val relativePath = HttpFileUtils.getRelativePath(root, file)
                    text = """ 
                    <form method="post" action="/upload/$routeLabel/$relativePath" enctype="multipart/form-data">
                        <button>Upload</button>
                        <input type="file" name="files" multiple> 
                    </form>
                """.trimIndent()
                }
        } // --- End of Html Page's Header ---- //

        val content = Html.many {
            h2("Listing Directory: ./${HttpFileUtils.getRelativePath(root, file)} ")

            h3("Directories")

            // List only directories and ignore hidden files dor directories (which names starts with '.' dot)
            val dirList = file.listFiles{ f -> f.isDirectory
                    && !f.name.startsWith(".")
                    && !f.name.endsWith("~") }

            for(f in dirList.toList().sortedBy { it.name?.toLowerCase() })
            {
                li{ t(relativePathLink(root, f)) }
                br()
                //pw.println("<li>" + relativePathLink(root, f) + "</li> <br>")
            }

            h3("Files")

            val fileList = file.listFiles{ f -> f.isFile
                    && !f.name.startsWith(".")
                    && !f.name.endsWith("~") }

            for(f in fileList.toList().sortedBy { it.name?.toLowerCase() })
            {
                val relPath = HttpFileUtils.getRelativePath(root, f)

                br()
                li{
                    t(relativePathLink(root, f))
                    t("  ")
                    if(f.toString().endsWith(".pdf"))
                    {
                        a{
                            href  = "/pdf-view/$routeLabel?page=0&pdf=$relPath"
                            label = "View"
                        }
                    }
                }

                  // TODO  Implement PDF metadata view
//                if(f.toString().endsWith(".pdf"))
//                {
//                    val doc = PDDocument.load(f)
//                    val info = doc.documentInformation
//                    br(); t("  Title: ${info.title ?: ""}")
//                    br(); t("  Author: ${info.author ?: ""}")
//                    br(); t( " Subject: ${info.subject ?: ""} ")
//                    doc.close()
//                }

                if(mEnablePDFThumbnail && f.toString().endsWith(".pdf"))
                {

                    //val b64Image = DocUtils.readPDFPageAsHtmlBase64Image(0, f.toString())
                    // pw.println("\n <br> $b64Image")
                    val fileLink = relativePathLink(root, f)

                    br()
                    a{
                        href = "$route/$relPath"
                        img {
                            src   = "/pdf-thumbnail/$routeLabel?pdf=$relPath"
                            style = "max-height: 200px; max-width: 200px;"
                        }
                    }
                    br();  br()
                }

                if(imagesEnabled && HttpFileUtils.fileIsImage(f))
                {
                    val relativePath = HttpFileUtils.getRelativePath(root, f)
                    // pw.println( "\n <br> <img width='600px' src='$route/$relativePath'/>" )
                    img {
                        src = "$route/$relativePath"
                        // label = "File: ${f.name}"
                        width = "600px"
                    }
                }

            }


        } // --- End of content html code --- //

        val html = TemplateLoader().loadAsset(html_basicPage)
                .set("CONTENT", content.render())
                .set("HEADER",  header.render())
                .set("TITLE",   if(mEnableShowDirectory) "Listing Directory: $file"
                                else "Listing directory: $route: ${HttpFileUtils.getRelativePath(root, file)}" )
                .html()

        ctx.html(html)

    } // ---- End of listDirectoryResponse() method ---- //


    //  page: http://<hostaddress>/directory/<DIRECTORY-SHARED>
    fun pageServeDirectory(app: Javalin, directoryLabel: String, directoryPath: String, showIndex: Boolean = true)
    {
        val root = java.io.File(directoryPath)
        val route = "/directory/$directoryLabel"


        if (mEnableUpload)
            app.post("/upload/$directoryLabel/*") upload@{ ctx ->
                val rawUriPath = ctx.req.requestURI.removePrefix("/upload/$directoryLabel/")
                val filename = HttpUtils.decodeURL(rawUriPath)
                val destination = java.io.File(directoryPath, filename.replace("..", ""))

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
                    // println(" [TRACE] Written file: ${fdata.filename} to $fupload ")
                }
                ctx.status(302)
                val url = "/directory/$directoryLabel/$filename"
                // println(" [TRACE] redirect URL = $url ")
                ctx.redirect(url)
            }

        if(mEnablePDFThumbnail)
        {
            app.get("/pdf-thumbnail/$directoryLabel") { ctx ->
                this.pagePDFThumbnailImage(ctx, directoryPath)
            }

            app.get("/pdf-view/$directoryLabel") { ctx ->
                this.pdfViewResponse(ctx, directoryLabel, directoryPath)
            }
        }


        app.get("$route/*") dir@{ ctx ->

            val rawUriPath = ctx.req.requestURI.removePrefix(route + "/")
            val filename = HttpUtils.decodeURL( rawUriPath )
            val file = java.io.File(directoryPath, filename.replace("..", ""))

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
                this.listDirectoryResponse(ctx, directoryLabel, directoryPath, file)
                return@dir
            } // -- End of if(file.isDirectory){ ... } //

            // Success response
            if(HttpFileUtils.fileIsMediaAV(file))
                HttpUtils.responseFileRange(ctx, file)
            else
                HttpUtils.responseFile(ctx, file)
        }

        if(route != "/")
            app.get(route) { ctx -> ctx.redirect("$route/", 302)}

    } //--- End of function serveDirectory() --- //

    fun pdfViewResponse(ctx: io.javalin.http.Context, routeLabel: String, directoryPath: String)
    {
        // rawUriPath = relative path between the pdf file and diretoryPath
        val rawUriPath = ctx.queryParam("pdf")
        // PDF page to be displayed
        val pageNum = ctx.queryParam<Int>("page").get()
        val pdfFile = java.io.File(directoryPath, rawUriPath)

        if(!pdfFile.exists()){
            ctx.result(" Error 404 - file not found. Unable to find file: $pdfFile")
                    .status(404)
            return
        }

        val response = Html.html {

            head {

                //  Add library hammer for enabling zooming and pinching in mobile devices.
                //  Library Hammer:
                //    + https://hammerjs.github.io
                //    + https://github.com/hammerjs/hammer.js
                //
                // Jascript file taken from: https://cdnjs.cloudflare.com/ajax/libs/hammer.js/2.0.8/hammer.min.js
                script{
                    type = "text/javascript"
                    src  = "/assets/hammer-2.0.8-min.js"
                            // "https://cdnjs.cloudflare.com/ajax/libs/hammer.js/1.0.5/hammer.min.js"
                }

                script{
                    type = "text/javascript"
                    src  = "/assets/pdfview.js"
                }

                t("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0\"/>")
                title("PDF file: $pdfFile")
                // Favicon
                link {
                    rel = "icon"
                    type = "image/png"
                    href = "/assets/server-icon.png"
                    sizes = "16x16"
                }

                link {
                    rel = "stylesheet"
                    type = "text/css"
                    href = "/assets/basic_page_style.css"
                }
            }

            body {

                div {
                    hclass = "header"
                    val totalPages = DocUtils.getPDFNumberOfPages(pdfFile.toString())
                    h3("Page: ${pageNum + 1} / $totalPages - File: ${pdfFile.name}")

                    a("/", "Top"){ }
                    t(" / ")

                    if(pageNum != 0)
                        a{
                            href  = "/pdf-view/$routeLabel?page=${pageNum - 1}&pdf=$rawUriPath"
                            label = "Previous"
                        }
                    else
                        t("Previous")

                    t(" / ")

                    if(pageNum < totalPages - 1)
                        a{
                            href  = "/pdf-view/$routeLabel?page=${pageNum + 1}&pdf=$rawUriPath"
                            label = "Next"
                        }
                    else
                        t("Next")

                    t(" / ")

                    a {
                        href = "/directory/$routeLabel/$rawUriPath"
                        label = "Download"
                    }

                }

                div {
                    hclass = "content"

                    img {
                        id = "img_page_display"
                        hclass = "pdfimage"
                        // val pdfPageImage = DocUtils.readPDFPage(pageNum, pdfFile.toString())
                        val pdfPageImage = DocUtils.readPDFPageGray(pageNum, 96.0f, pdfFile.toString())
                        setImageBase64(pdfPageImage)
                    }
                }

            }
        }.render()

        ctx.html(response)
    } //--- End of pdfViewResponse() method --- //

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

            println("\n [INFO] ctx.path() = ${ctx.path()} \n")

            fun isLocalMachine(ipAddr: String): Boolean {
                return ipAddr == "0:0:0:0:0:0:0:1"
                        || ipAddr == "127.0.0.1"
                        || ipAddr == "localhost";
            }

            // Check whether user is logged or the path or resource/resource is whitelisted
            if(isLogged || ctx.path() == loginFormPage
                        || ctx.path() == loginValidationRoute
                        || ctx.path().startsWith("/assets/")
                        // Localhost machine does not need login
                        || isLocalMachine(ctx.ip()) ) {
                handler.handle(ctx)
                return@gate
            }

            // If user is not logged, redirects him to login page
            ctx.redirect(loginFormPage, 302)
            return@gate
        }
    }


} // ----- End of class FileServer --------//

