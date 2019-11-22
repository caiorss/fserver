package com.github.fserver.html

interface HtmlDom
{
    fun getTag(): String?
    fun render(): String
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
        if(text != null)
            return text ?: "<$tag>$text</$tag>"
        else
            return text ?: "<$tag></$tag>"
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

class TagA(val href: String): HtmlDom {
    var id:     String? = null
    var child:  HtmlDom? = null
    var target: String? = null

    constructor(href: String, label: String) : this(href) {
        this.child = TagText(label)
    }

    override fun getTag(): String? { return "a" }

    override fun render(): String {
        if(child != null)
            return "<a href='$href' target='$target'>${child!!.render()}</a>"
        else
            return "<a href='$href' target='$target'> </a>"
    }
}

class TagLI(var inner: HtmlDom? = null): HtmlDom {

    override fun getTag(): String? {
        return "li"
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

    fun add(tag: HtmlDom){ elements += tag }

    /** Html tag <br> for adding new line */
    fun br() { this.add(TagBR()) }

    fun a(href: String, block: TagA.() -> Unit) {
        this.add(TagA(href).apply(block))
    }

    fun a(href: String, label: String, block: TagA.() -> Unit) {
        this.add(TagA(href, label).apply(block))
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

    fun h2(block: TagH2.() -> Unit) {
        this.add(TagH2().apply(block))
    }

    fun h3(block: TagH3.() -> Unit) {
        this.add(TagH3().apply(block))
    }

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

}


//fun tagA(href: String, block: TagA.() -> Unit): TagA = TagA(href).apply(block)
//fun tagP(block: TagP.() -> Unit): TagP = TagP().apply(block)

