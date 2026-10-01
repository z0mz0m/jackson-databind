package com.fasterxml.jackson.databind.deser;

import java.util.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.type.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import static com.fasterxml.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Tests for builder-based deserialization paths, {@link Deserializers.Base}
 * defaults and related bean deserializer behaviours.
 */
public class DeserBuilderAndBaseCoverageTest
{
    static class Simple {
        public int x;
        public String name;
        Simple(int x, String name) { this.x = x; this.name = name; }
    }

    @JsonPOJOBuilder(withPrefix = "")
    static class SimpleBuilder {
        int x;
        String name;
        public SimpleBuilder x(int v) { x = v; return this; }
        public SimpleBuilder name(String v) { name = v; return this; }
        public SimpleValue build() { return new SimpleValue(x, name); }
    }

    @JsonDeserialize(builder = SimpleBuilder.class)
    static class SimpleValue extends Simple {
        SimpleValue(int x, String name) { super(x, name); }
    }

    @JsonDeserialize(builder = ScalarBuilder.class)
    static class ScalarValue {
        public String text;
        ScalarValue(String t) { text = t; }
    }

    static class ScalarBuilder {
        String t;
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public ScalarBuilder(String s) { t = "s:" + s; }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ScalarBuilder ofInt(int i) { return new ScalarBuilder("i" + i); }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ScalarBuilder ofDouble(double d) { return new ScalarBuilder("d" + d); }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ScalarBuilder ofBool(boolean b) { return new ScalarBuilder("b" + b); }
        public ScalarValue build() { return new ScalarValue(t); }
    }

    @JsonDeserialize(builder = PropCreatorBuilder.class)
    static class PropValue {
        public int a;
        public String b;
        public Map<String, Object> extra = new LinkedHashMap<>();
    }

    static class PropCreatorBuilder {
        int a;
        String b;
        Map<String, Object> extra = new LinkedHashMap<>();
        @JsonCreator
        public PropCreatorBuilder(@JsonProperty("a") int a) { this.a = a; }
        public PropCreatorBuilder withB(String v) { b = v; return this; }
        @JsonAnySetter
        public void any(String k, Object v) { extra.put(k, v); }
        public PropValue build() {
            PropValue v = new PropValue();
            v.a = a; v.b = b; v.extra = extra;
            return v;
        }
    }

    @JsonDeserialize(builder = ViewBuilder.class)
    static class ViewValue {
        public int pub, internal;
    }

    static class Pub { }
    static class Internal extends Pub { }

    static class ViewBuilder {
        int pub, internal;
        @JsonView(Pub.class)
        public ViewBuilder withPub(int v) { pub = v; return this; }
        @JsonView(Internal.class)
        public ViewBuilder withInternal(int v) { internal = v; return this; }
        public ViewValue build() {
            ViewValue v = new ViewValue();
            v.pub = pub; v.internal = internal;
            return v;
        }
    }

    @JsonDeserialize(builder = UnwrapBuilder.class)
    static class UnwrapValue {
        public int id;
        public Inner inner;
    }

    static class Inner {
        public int p, q;
    }

    static class UnwrapBuilder {
        int id;
        Inner inner;
        public UnwrapBuilder withId(int v) { id = v; return this; }
        @JsonUnwrapped
        public UnwrapBuilder withInner(Inner i) { inner = i; return this; }
        public UnwrapValue build() {
            UnwrapValue v = new UnwrapValue();
            v.id = id; v.inner = inner;
            return v;
        }
    }

    @JsonDeserialize(builder = ArrayBuilder.class)
    static class ArrayValue {
        public int x, y;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"x", "y"})
    static class ArrayBuilder {
        int x, y;
        public ArrayBuilder x(int v) { x = v; return this; }
        public ArrayBuilder y(int v) { y = v; return this; }
        public ArrayValue build() {
            ArrayValue v = new ArrayValue();
            v.x = x; v.y = y;
            return v;
        }
    }

    @JsonDeserialize(builder = IgnoringBuilder.class)
    static class IgnoringValue {
        public int v;
    }

    @JsonIgnoreProperties(value = {"skip"}, ignoreUnknown = true)
    static class IgnoringBuilder {
        int v;
        public IgnoringBuilder withV(int x) { v = x; return this; }
        public IgnoringValue build() { IgnoringValue r = new IgnoringValue(); r.v = v; return r; }
    }

    static class NoopDeserializers extends Deserializers.Base { }

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testSimpleBuilderAndUnknown() throws Exception {
        SimpleValue v = MAPPER.readValue("{\"x\":3,\"name\":\"a\"}", SimpleValue.class);
        assertEquals(3, v.x);
        assertEquals("a", v.name);
        ObjectReader r = MAPPER.readerFor(SimpleValue.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleValue v2 = r.readValue("{\"zzz\":[1,{}],\"x\":1}");
        assertEquals(1, v2.x);
        assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"zzz\":1}", SimpleValue.class));
        assertNull(MAPPER.readValue("{\"name\":null}", SimpleValue.class).name);
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("\"str\"", SimpleValue.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("12", SimpleValue.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("1.5", SimpleValue.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("true", SimpleValue.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[1]", SimpleValue.class));
    }

    @Test
    public void testSingleArrayUnwrap() throws Exception {
        ObjectReader r = MAPPER.readerFor(SimpleValue.class)
                .with(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        SimpleValue v = r.readValue("[{\"x\":5}]");
        assertEquals(5, v.x);
    }

    @Test
    public void testSupportsUpdateAndCopies() throws Exception {
        DeserializationConfig cfg = MAPPER.getDeserializationConfig();
        DeserializationContext ctxt = ((DefaultDeserializationContext) MAPPER.getDeserializationContext())
                .createInstance(cfg, null, null);
        JsonDeserializer<?> deser = ctxt.findRootValueDeserializer(MAPPER.constructType(SimpleValue.class));
        assertTrue(deser instanceof BuilderBasedDeserializer);
        BuilderBasedDeserializer bd = (BuilderBasedDeserializer) deser;
        assertEquals(Boolean.FALSE, bd.supportsUpdate(cfg));
        assertNotSame(bd, bd.withIgnoreAllUnknown(true));
        assertNotNull(bd.withByNameInclusion(Collections.singleton("x"), null));
        assertNotNull(bd.withByNameInclusion(null, Collections.singleton("x")));
        assertNotNull(bd.unwrappingDeserializer(com.fasterxml.jackson.databind.util.NameTransformer.NOP));
        assertNotNull(bd.withObjectIdReader(null));
    }

    @Test
    public void testScalarDelegatingBuilder() throws Exception {
        assertEquals("s:abc", MAPPER.readValue("\"abc\"", ScalarValue.class).text);
        assertEquals("i7", MAPPER.readValue("7", ScalarValue.class).text.replace("s:", ""));
        assertNotNull(MAPPER.readValue("1.5", ScalarValue.class).text);
        assertNotNull(MAPPER.readValue("true", ScalarValue.class).text);
    }

    @Test
    public void testPropertyBasedBuilder() throws Exception {
        PropValue v = MAPPER.readValue("{\"b\":\"B\",\"zz\":1,\"a\":4,\"yy\":\"w\"}", PropValue.class);
        assertEquals(4, v.a);
        assertEquals("B", v.b);
        assertEquals(2, v.extra.size());
        PropValue v2 = MAPPER.readValue("{\"a\":9}", PropValue.class);
        assertEquals(9, v2.a);
    }

    @Test
    public void testViewsBuilder() throws Exception {
        ObjectReader r = MAPPER.readerFor(ViewValue.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ViewValue v = r.withView(Pub.class).readValue("{\"pub\":1,\"internal\":2}");
        assertEquals(1, v.pub);
        assertEquals(0, v.internal);
        v = r.withView(Internal.class).readValue("{\"pub\":1,\"internal\":2}");
        assertEquals(2, v.internal);
    }

    @Test
    public void testUnwrappedBuilder() throws Exception {
        UnwrapValue v = MAPPER.readValue("{\"id\":1,\"p\":2,\"q\":3}", UnwrapValue.class);
        assertEquals(1, v.id);
        assertEquals(2, v.inner.p);
        assertEquals(3, v.inner.q);
    }

    @Test
    public void testArrayShapedBuilder() throws Exception {
        ArrayValue v = MAPPER.readValue("[3,4]", ArrayValue.class);
        assertEquals(3, v.x);
        assertEquals(4, v.y);
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[1,2,3]", ArrayValue.class));
    }

    @Test
    public void testIgnoredPropertiesBuilder() throws Exception {
        IgnoringValue v = MAPPER.readValue("{\"skip\":[1],\"v\":5,\"other\":{}}", IgnoringValue.class);
        assertEquals(5, v.v);
    }

    @Test
    public void testDeserializersBaseDefaults() throws Exception {
        Deserializers d = new NoopDeserializers();
        DeserializationConfig cfg = MAPPER.getDeserializationConfig();
        TypeFactory tf = MAPPER.getTypeFactory();
        assertNull(d.findEnumDeserializer(Thread.State.class, cfg, null));
        assertNull(d.findTreeNodeDeserializer(com.fasterxml.jackson.databind.node.ObjectNode.class, cfg, null));
        assertNull(d.findBeanDeserializer(tf.constructType(Simple.class), cfg, null));
        assertNull(d.findReferenceDeserializer((ReferenceType) tf.constructReferenceType(Optional.class, tf.constructType(String.class)),
                cfg, null, null, null));
        assertNull(d.findArrayDeserializer(tf.constructArrayType(String.class), cfg, null, null, null));
        assertNull(d.findCollectionDeserializer(tf.constructCollectionType(List.class, String.class),
                cfg, null, null, null));
        assertNull(d.findCollectionLikeDeserializer(tf.constructCollectionLikeType(List.class, String.class),
                cfg, null, null, null));
        assertNull(d.findMapDeserializer(tf.constructMapType(Map.class, String.class, String.class),
                cfg, null, null, null, null));
        assertNull(d.findMapLikeDeserializer(tf.constructMapLikeType(Map.class, String.class, String.class),
                cfg, null, null, null, null));
        assertFalse(d.hasDeserializerFor(cfg, String.class));
    }
}
