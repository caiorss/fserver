package com.github.fserver.html

interface HtmlDom
{
    fun getTag(): String?
    fun render(): String
}

/** Pseudo-Html tag for representing an empty text.  */
class TagEmpty: HtmlDom {
    override fun getTag(): String? { return "EMPTY" }
    override fun render(): String  { return ""}
}


class TagBR: HtmlDom {
    override fun getTag(): String? { return "br" }
    override fun render(): String  { return "<br>"}
}

class TagText(var text: String? = null): HtmlDom {
    override fun getTag(): String? { return "" }
    override fun render(): String { return text ?: "" }
}
abstract class HtmlTextAbstract(var text: String? = null): HtmlDom {
    override fun render(): String {
        val tag = this.getTag()
        return "<$tag>${ text ?: null }</$tag>"
    }
}

class TagP: HtmlTextAbstract() {
    override fun getTag(): String? { return "p" }
}

class TagH1: HtmlTextAbstract() {
    override fun getTag(): String? { return "h1" }
}

class TagH2: HtmlTextAbstract() {
    override fun getTag(): String? { return "h2" }
}

class TagH3: HtmlTextAbstract() {
    override fun getTag(): String? { return "h3" }
}

class TagA(var href: String): HtmlDom {
    var id:     String? = null
    var child:  HtmlDom? = null
    var target: String? = null

    constructor(href: String, label: String) : this(href) {
        this.child = TagText(label)
    }

    override fun getTag(): String? { return "a" }

    // Property label
    var label: String?
       get() = if(this.child is TagText) (this.child as TagText).text else null
       set(text){  this.child = TagText(text) }

    fun img(block: TagImg.() -> Unit) {
        this.child = TagImg("").apply(block)
    }

    fun img(src: String, block: TagImg.() -> Unit) {
        this.child = TagImg(src).apply(block)
    }

    override fun render(): String {
        if(child != null)
            return "<a href='$href' target='$target'>${child!!.render()}</a>"
        else
            return "<a href='$href' target='$target'> </a>"
    }
}

class TagImg(var src: String): HtmlDom {
    var id:     String? = ""
    var hclass: String? = ""
    var child:  HtmlDom? = null
    var style:  String?  = null
    var width:  String?  = null

    constructor(href: String, label: String) : this(href) {
        this.child = TagText(label)
    }

    override fun getTag(): String? { return "a" }

    fun setImageBase64(img: java.awt.image.BufferedImage, imgType: String = "png"): Unit {
        val bos = java.io.ByteArrayOutputStream()
        var imgb64: String? = null
        try {
            javax.imageio.ImageIO.write(img, imgType, bos)
            val bytes = bos.toByteArray()
            imgb64 = String(java.util.Base64.getEncoder().encode(bytes))
        } finally {
            bos.close()
        }
        this.src = "data:image/$imgType;base64,$imgb64"
    }

    // Property label
    var label: String?
        get() = if(this.child is TagText) (this.child as TagText).text else null
        set(text){  this.child = TagText(text) }

    override fun render(): String {
        val childHtml = if(child != null) child!!.render() else ""
        val styleHtml = if(style != null) "style='$style'"  else ""
        val widthHtml = if(width != null) "width='$width'"  else ""
        return "<img id='$id' class='$hclass' src='$src' $widthHtml $styleHtml>$childHtml</img>"
    }
}




class TagLI(var inner: HtmlDom? = null): HtmlDom {

    override fun getTag(): String? {
        return "li"
    }

    fun t(text: String) {
        inner = TagText(text)
    }

    fun a(href: String, block: TagA.() -> Unit) {
        inner = TagA(href).apply(block)
    }

    fun a(href: String, label: String, block: TagA.() -> Unit) {
        inner = TagA(href, label).apply(block)
    }

    override fun render(): String {
        return "<li>${inner?.render()}</li>"
    }
}

abstract class TagComposite: HtmlDom {
    val elements = arrayListOf<HtmlDom>()
    var id: String = ""
    var hclass: String = ""

    // abstract fun getTag(): String?
    override fun render(): String {
        val tag = this.getTag()
        var html = ""
        for(t in elements) html += "\n  " + t.render()
        if(tag != null)
        {
            html = "<$tag class='$hclass'>\n $html \n</$tag>"
        }
        return html
    }

    fun add(tag: HtmlDom){ elements += tag }

    /** Html tag <br> for adding new line */
    fun br() { this.add(TagBR()) }

    fun a(block: TagA.() -> Unit) {
        this.add(TagA("").apply(block))
    }


    fun a(href: String, block: TagA.() -> Unit) {
        this.add(TagA(href).apply(block))
    }

    fun a(href: String, label: String, block: TagA.() -> Unit) {
        this.add(TagA(href, label).apply(block))
    }

    fun img(block: TagImg.() -> Unit) {
        this.add(TagImg("").apply(block))
    }

    fun img(src: String, block: TagImg.() -> Unit) {
        this.add(TagImg(src).apply(block))
    }

    fun li(label: String) {
        this.add(TagLI(TagText(label)))
    }


    fun li(block: TagLI.() -> Unit) {
        this.add(TagLI().apply(block))
    }

    fun li(label: String, block: TagLI.() -> Unit) {
        this.add(TagLI(TagText(label)).apply(block))
    }

    /** Raw text without any HTML tag */
    fun t(text: String) {
        this.add(TagText(text))
    }

    /** Raw text without any HTML tag */
    fun t(block: TagText.() -> Unit) {
        this.add(TagText().apply(block))
    }


    fun p(text: String) {
        val elem = TagP().apply { this.text = text }
        this.add(elem)
    }

    fun p(block: TagP.() -> Unit) {
        this.add(TagP().apply(block))
    }

    fun p(text: String, block: TagP.() -> Unit) {
        val elem = TagP().apply { this.text = text }
        this.add(elem.apply(block))
    }

    fun h1(block: TagH1.() -> Unit) {
        this.add(TagH1().apply(block))
    }

    fun h1(text: String){
        this.add(TagH1().apply{ this.text = text})
    }

    fun h2(block: TagH2.() -> Unit)
    {
        this.add(TagH2().apply(block))
    }

    fun h2(text: String)
    {
        this.add(TagH2().apply{ this.text = text})
    }

    fun h3(block: TagH3.() -> Unit) {
        this.add(TagH3().apply(block))
    }

    fun h3(text: String)
    {
        this.add(TagH3().apply{ this.text = text})
    }


}

class TagBody: TagComposite() {
    override fun getTag(): String { return "body" }
}

class TagDiv: TagComposite() {
    override fun getTag(): String { return "div" }
}

// Pseudo-html tag that represents a group of many DOM objects/elements
// without a parent object.
class TagMany: TagComposite() {
    override fun getTag(): String? { return null }
}

object HtmlBuilder {
    fun body(block: TagBody.() -> Unit): TagBody = TagBody().apply(block)
    fun div(block: TagDiv.() -> Unit): TagDiv = TagDiv().apply(block)
    fun many(block: TagMany.() -> Unit): TagMany = TagMany().apply(block)

    fun a(href: String, label: String): TagA
    {
        return TagA(href).apply{ this.child = TagText(label) }
    }

    fun a(href: String, block: TagA.() -> Unit): TagA
    {
        return TagA(href).apply(block)
    }

    fun a(href: String, label: String, block: TagA.() -> Unit): TagA
    {
        return TagA(href).apply{ this.child = TagText(label) }.apply(block)
    }

    fun empty(): TagEmpty { return TagEmpty() }
}


//fun tagA(href: String, block: TagA.() -> Unit): TagA = TagA(href).apply(block)
//fun tagP(block: TagP.() -> Unit): TagP = TagP().apply(block)

