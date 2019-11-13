package samples

import io.javalin.Javalin
import io.javalin.http.staticfiles.Location


fun htmlLink(label: String, href: String): String
{
    return "<a href='$href'>$label</a>"
}

// Requires Java7
fun getMimeType(file: java.io.File): String {
    return  java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
}

fun getRelativePath(root: java.io.File, path: java.io.File): String
{
    return root.toURI().relativize(path.toURI()).path
}

fun serveDirectory(app: Javalin, route: String, path: String)
{
    val root = java.io.File(path)

    app.get(route) dir@{ ctx ->
        val filename = ctx.queryParam<String>("file", ".").get()
        val file = java.io.File(path, filename.replace("..", ""))

        println(" INFO filename = $filename ; file = $file")


        if(!file.exists()) {
            // Error Response
            ctx.result("Error file $file not found.")
                    .status(404)
            // Early return
            return@dir
        }
        if(file.isDirectory)
        {
            var html = ""
            html += "<h2> Directories  </h2> \n"
            // List only directories and ignore hidden files dor directories (which names starts with '.' dot)
            for(f in file.listFiles{ f -> f.isDirectory && !f.name.startsWith(".") }!!){
                val relativePath = root.toURI().relativize(f.toURI()).path
                html += "<a href='$route/?file=$relativePath'> ${f.name} </a> </br>"
            }
            html += "<h2> Files </h2> \n"
            // List only files and ignore hidden files directories (which name starts with '.' dot)
            for(f in file.listFiles{ f -> f.isFile && !f.name.startsWith(".") }!!){
                val relativePath = root.toURI().relativize(f.toURI()).path
                html += "<a href='$route/?file=$relativePath'> ${f.name} </a> </br>"
            }

            ctx.html(html)
            return@dir
        }

        // Success response
        ctx.result(file.inputStream())
        val mimeType = getMimeType(file)
        ctx.contentType(mimeType)

    }
}

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

    app.get( "/html") { ctx ->
                    // Success response

        val imageURl = "https://www.recantha.co.uk/blog/wp-content/uploads/2017/02/fallout_4_terminal.jpg"
        ctx.html("<h1> My Web Page title</h1>"
                    + "\n <img width=400px src='$imageURl'></img> ")

    }


        println(" [INFO] Server stopped")
}


