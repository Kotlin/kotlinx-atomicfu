import org.gradle.api.publish.maven.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

fun MavenPom.configureMavenPluginPomAttributes(projectName: String) {
    name.set(projectName)
    packaging = "maven-plugin"
    description.set("Atomicfu Maven Plugin")
}


internal fun Element.getElement(tagName: String): Node =
    getElementsByTagName(tagName).item(0)
        ?: error("No element named '$tagName' in Element $this")

fun Document.createElement(nodeName: String, configure: Element.() -> Unit = {}): Element =
    createElement(nodeName).apply(configure)

fun Document.createElement(tag: String, value: String): Element {
    val element = createElement(tag)
    element.textContent = value
    return element
}

// https://stackoverflow.com/a/979606/4161471
fun Node.removeWhitespaceNodes() {
    val xpathFactory = XPathFactory.newInstance()

    // XPath to find empty text nodes
    val xpathExp = xpathFactory.newXPath().compile("//text()[normalize-space(.) = '']")
    val emptyTextNodes = xpathExp.evaluate(this, XPathConstants.NODESET) as NodeList

    // Remove each empty text node from document
    for (i in 0 until emptyTextNodes.length) {
        val emptyTextNode = emptyTextNodes.item(i)
        emptyTextNode.getParentNode().removeChild(emptyTextNode)
    }
}