package org.http4k.format

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.Jackson.asA
import org.http4k.format.Jackson.auto
import org.http4k.format.Jackson.autoView
import org.http4k.hamkrest.hasBody
import org.http4k.websocket.WsMessage
import org.junit.jupiter.api.Test

open class Public
class Private : Public()

data class ArbObjectWithView(@JsonView(Private::class) @JvmField val priv: Int, @JsonView(Public::class) @JvmField val pub: Int)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class")
@JsonSubTypes(
    JsonSubTypes.Type(value = FirstChild::class, name = "first"),
    JsonSubTypes.Type(value = SecondChild::class, name = "second")
)
sealed class PolymorphicParent

data class FirstChild(val something: String) : PolymorphicParent()
data class SecondChild(val somethingElse: String) : PolymorphicParent()

abstract class NotSealedParent
data class NonSealedChild(val something: String) : NotSealedParent()

interface Interface {
    val value: String
}

class InterfaceImpl : Interface {
    override val value = "hello"
    val subValue = "123"
}

class JacksonAutoTest : AutoMarshallingContract(Jackson) {

    @Test
    fun `roundtrip arbitary object to and from JSON element`() {
        val obj = ArbObject("hello", ArbObject("world", null, listOf(1), true), emptyList(), false)
        val out = Jackson.asJsonObject(obj)
        assertThat(Jackson.asA(out, ArbObject::class), equalTo(obj))
    }

    @Test
    fun `roundtrip list of arbitary objects to and from body`() {
        val body = Body.auto<List<ArbObject>>().toLens()

        val obj = ArbObject("hello", ArbObject("world", null, listOf(1), true), emptyList(), false)

        assertThat(body(Response(OK).with(body of listOf(obj))), equalTo(listOf(obj)))
    }

    @Test
    fun `roundtrip list of arbitary objects to and from node`() {
        val obj = ArbObject("hello", ArbObject("world", null, listOf(1), true), emptyList(), false)

        assertThat(Jackson.asJsonObject(listOf(obj)).asA(), equalTo(listOf(obj)))
    }

    @Test
    fun `roundtrip array of arbitary objects to and from body`() {
        val body = Body.auto<Array<ArbObject>>().toLens()

        val obj = ArbObject("hello", ArbObject("world", null, listOf(1), true), emptyList(), false)

        assertThat(body(Response(OK).with(body of arrayOf(obj))).toList(), equalTo(listOf(obj)))
    }

    @Test
    fun `roundtrip polymorphic object to and from body`() {
        val body = Body.auto<PolymorphicParent>().toLens()

        val firstChild: PolymorphicParent = FirstChild("hello")
        val secondChild: PolymorphicParent = SecondChild("world")

        println(Response(OK).with(body of firstChild))
        assertThat(body(Response(OK).with(body of firstChild)), equalTo(firstChild))
        assertThat(body(Response(OK).with(body of secondChild)), equalTo(secondChild))
    }

    @Test
    fun `write interface implementation to body`() {
        assertThat(Response(OK).with(
            Body.auto<Interface>().toLens() of InterfaceImpl()
        ).bodyString(), equalTo("""{"value":"hello","subValue":"123"}"""))

        // this is the wrong behaviour if we want to use the same auto method for both ...
        assertThat(Response(OK).with(
            Jackson.autoBody<Interface>(writeFn = { writerFor(jacksonTypeRef<Interface>()).writeValueAsString(it) }).toLens() of InterfaceImpl()
        ).bodyString(), equalTo("""{"value":"hello"}"""))
    }

    @Test
    fun `writes using non-sealed parent type`() {
        val nonSealedChild = NonSealedChild("hello")
        assertThat(Response(OK).with(Body.auto<NotSealedParent>().toLens() of nonSealedChild).bodyString(), equalTo("""{"something":"hello"}"""))

        // this is the wrong behaviour if we want to use the same auto method for both ...
        assertThat(Response(OK).with(
            Jackson.autoBody<NotSealedParent>(writeFn = { writerFor(jacksonTypeRef<NotSealedParent>()).writeValueAsString(it) }).toLens() of nonSealedChild
        ).bodyString(), equalTo("""{}"""))
    }

    @Test
    fun `roundtrip list of polymorphic objects to and from body`() {
        val body = Jackson.autoBody<List<PolymorphicParent>>(writeFn = { writerFor(jacksonTypeRef<List<PolymorphicParent>>()).writeValueAsString(it) }).toLens()

        val list = listOf(FirstChild("hello"), SecondChild("world"))

        assertThat(body(Response(OK).with(body of list)), equalTo(list))
    }

    @Test
    fun `roundtrip body using view`() {
        val arbObjectWithView = ArbObjectWithView(3, 5)
        val publicLens = Body.autoView<ArbObjectWithView, Public>().toLens()
        val privateLens = Body.autoView<ArbObjectWithView, Private>().toLens()

        assertThat(Response(OK).with(publicLens of arbObjectWithView), hasBody(equalTo("""{"pub":5}""")))
        assertThat(Response(OK).with(privateLens of arbObjectWithView), hasBody(equalTo("""{"priv":3,"pub":5}""")))

        assertThat(publicLens(Response(OK).with(privateLens of arbObjectWithView)), equalTo(ArbObjectWithView(0, 5)))
    }

    @Test
    fun `roundtrip WsMessage using view`() {
        val arbObjectWithView = ArbObjectWithView(3, 5)
        val publicLens = WsMessage.autoView<ArbObjectWithView, Public>().toLens()
        val privateLens = WsMessage.autoView<ArbObjectWithView, Private>().toLens()

        assertThat(publicLens(arbObjectWithView).bodyString(), equalTo("""{"pub":5}"""))
        assertThat(privateLens(arbObjectWithView).bodyString(), equalTo("""{"priv":3,"pub":5}"""))

        assertThat(publicLens(privateLens(arbObjectWithView)), equalTo(ArbObjectWithView(0, 5)))
    }

    override fun customJson() = object : ConfigurableJackson(KotlinModule().asConfigurable().customise()) {}
}

class JacksonTest : JsonContract<JsonNode>(Jackson) {
    override val prettyString = """{
  "hello" : "world"
}"""
}

class JacksonGenerateDataClassesTest : GenerateDataClassesContract<JsonNode>(Jackson)

class JacksonAutoEventsTest : AutoJsonEventsContract(Jackson)