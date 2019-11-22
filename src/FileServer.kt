package com.github.fserver.fserver

import io.javalin.Javalin
import org.slf4j.LoggerFactory

import com.github.fserver.html.HtmlBuilder as Html
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
        val content = Html.many {
            h2("Shared Directories ")

            for(r in mRoutes)
            {
                p("Directory: ")
                a(label = r.diretoryLabel, href = "/directory/${r.diretoryLabel}") { }
                if(mShowParams) li(" => ${r.directoryPath} ")
                br()
            }
        }

        val logoutLink = if(this.hasAuthentication())
            Html.a("/user-logout", "Logout")
        else
            Html.empty()

        val html = TemplateLoader.basicPage(logoutLink.render(), content.render())
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
            DocUtils.writePDFPageToStream(0, pdfFile.toString()
                    , imgFile.outputStream())
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
                br()
                li{ t(relativePathLink(root, f)) }


                if(mEnablePDFThumbnail && f.toString().endsWith(".pdf"))
                {
                    //val b64Image = DocUtils.readPDFPageAsHtmlBase64Image(0, f.toString())
                    // pw.println("\n <br> $b64Image")
                    val relPath = HttpFileUtils.getRelativePath(root, f)
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
                        label = f.toString()
                        width = "600px"
                    }
                }

            }


        } // --- End of content html code --- //

        ctx.html(TemplateLoader.basicPage(header.render(), content.render()))

    } // ---- End of listDirectoryResponse() method ---- //




    //  page: http://<hostaddress>/directory/<DIRECTORY-SHARED>
    fun pageServeDirectory(app: Javalin, routeLabel: String, path: String, showIndex: Boolean = true)
    {
        val root = java.io.File(path)
        val route = "/directory/$routeLabel"


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

        if(mEnablePDFThumbnail)
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
                this.listDirectoryResponse(ctx, routeLabel, path, file)
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

