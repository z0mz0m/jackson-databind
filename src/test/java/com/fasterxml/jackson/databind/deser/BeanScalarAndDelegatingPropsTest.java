package com.fasterxml.jackson.databind.deser;

import java.util.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import static com.fasterxml.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Tests for bean deserialization from non-Object tokens (scalars and
 * arrays), numeric object ids and delegating property wrappers.
 */
public class BeanScalarAndDelegatingPropsTest
{
    static class Scalar {
        public String text;
        public long num;
        public double dbl;
        public boolean flag;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Scalar fromString(String s) { Scalar r = new Scalar(); r.text = s; return r; }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Scalar fromInt(int i) { Scalar r = new Scalar(); r.num = i; return r; }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Scalar fromLong(long i) { Scalar r = new Scalar(); r.num = i; return r; }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Scalar fromDouble(double d) { Scalar r = new Scalar(); r.dbl = d; return r; }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Scalar fromBool(boolean b) { Scalar r = new Scalar(); r.flag = b; return r; }
    }

    static class ArrayDelegate {
        public List<Integer> values;
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public ArrayDelegate(List<Integer> v) { values = v; }
    }

    static class Plain {
        public int a;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    static class IntIdNode {
        public int id;
        public String name;
        public IntIdNode next;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    static class StrIdNode {
        public String id;
        public StrIdNode next;
    }

    @JsonDeserialize(builder = SimpleBuilder.class)
    static class Built {
        public int a;
    }

    static class SimpleBuilder {
        int a;
        public SimpleBuilder withA(int v) { a = v; return this; }
        public Built build() { Built b = new Built(); b.a = a; return b; }
    }

    static class Wrapper extends SettableBeanProperty.Delegating {
        private static final long serialVersionUID = 1L;
        Wrapper(SettableBeanProperty d) { super(d); }
        @Override
        protected SettableBeanProperty withDelegate(SettableBeanProperty d) { return new Wrapper(d); }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private DeserializationContext ctxtFor(JsonParser p) {
        return ((DefaultDeserializationContext) MAPPER.getDeserializationContext())
                .createInstance(MAPPER.getDeserializationConfig(), p, null);
    }

    @Test
    public void testScalarDelegates() throws Exception {
        assertEquals("abc", MAPPER.readValue("\"abc\"", Scalar.class).text);
        assertEquals(12, MAPPER.readValue("12", Scalar.class).num);
        assertEquals(1L << 40, MAPPER.readValue(String.valueOf(1L << 40), Scalar.class).num);
        assertEquals(1.5, MAPPER.readValue("1.5", Scalar.class).dbl);
        assertTrue(MAPPER.readValue("true", Scalar.class).flag);
        assertFalse(MAPPER.readValue("false", Scalar.class).flag);
    }

    @Test
    public void testBeanFromScalarWithoutCreator() throws Exception {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("\"x\"", Plain.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("5", Plain.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("1.5", Plain.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("true", Plain.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[]", Plain.class));
    }

    @Test
    public void testArrayDelegate() throws Exception {
        ArrayDelegate v = MAPPER.readValue("[1,2,3]", ArrayDelegate.class);
        assertEquals(Arrays.asList(1, 2, 3), v.values);
    }

    @Test
    public void testEmptyArrayCoercions() throws Exception {
        ObjectMapper asNull = newJsonMapper();
        asNull.coercionConfigFor(LogicalType.POJO)
            .setCoercion(CoercionInputShape.EmptyArray, CoercionAction.AsNull);
        assertNull(asNull.readValue("[]", Plain.class));
        assertNull(asNull.readValue("[]", Built.class));

        ObjectMapper asEmpty = newJsonMapper();
        asEmpty.coercionConfigFor(LogicalType.POJO)
            .setCoercion(CoercionInputShape.EmptyArray, CoercionAction.AsEmpty);
        assertNotNull(asEmpty.readValue("[]", Plain.class));
        assertNotNull(asEmpty.readValue("[]", Built.class));
    }

    @Test
    public void testUnwrapSingleValueArray() throws Exception {
        ObjectReader r = MAPPER.readerFor(Plain.class)
                .with(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        assertEquals(3, r.<Plain>readValue("[{\"a\":3}]").a);
        assertThrows(JsonMappingException.class, () -> r.readValue("[{\"a\":3},{\"a\":4}]"));

        ObjectReader br = MAPPER.readerFor(Built.class)
                .with(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        assertEquals(4, br.<Built>readValue("[{\"a\":4}]").a);
        assertThrows(JsonMappingException.class, () -> br.readValue("[{\"a\":3},{\"a\":4}]"));
        assertThrows(JsonMappingException.class, () -> br.readValue("[]"));
    }

    @Test
    public void testBuilderFromScalarAndEmptyArray() throws Exception {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[]", Built.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("\"x\"", Built.class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("3", Built.class));
    }

    @Test
    public void testNumericObjectIdReferences() throws Exception {
        IntIdNode n = MAPPER.readValue(
                "{\"id\":1,\"name\":\"a\",\"next\":{\"id\":2,\"name\":\"b\",\"next\":1}}",
                IntIdNode.class);
        assertSame(n, n.next.next);

        // string-valued reference to an int id needs conversion
        IntIdNode n2 = MAPPER.readValue(
                "{\"id\":5,\"next\":{\"id\":6,\"next\":\"5\"}}", IntIdNode.class);
        assertSame(n2, n2.next.next);
    }

    @Test
    public void testStringObjectIdFromNumber() throws Exception {
        StrIdNode n = MAPPER.readValue(
                "{\"id\":\"7\",\"next\":{\"id\":\"8\",\"next\":7}}", StrIdNode.class);
        assertSame(n, n.next.next);
    }

    @Test
    public void testObjectIdAsScalarReference() throws Exception {
        IntIdNode[] arr = MAPPER.readValue(
                "[{\"id\":1,\"name\":\"x\"},1]", IntIdNode[].class);
        assertSame(arr[0], arr[1]);
    }

    @Test
    public void testDelegatingPropertyWrapper() throws Exception {
        BeanDeserializerBase deser = (BeanDeserializerBase) ctxtFor(null)
                .findRootValueDeserializer(MAPPER.constructType(Plain.class));
        SettableBeanProperty orig = deser.findProperty("a");
        Wrapper w = new Wrapper(orig);
        assertSame(orig, w.getDelegate());
        assertEquals("a", w.getName());
        assertEquals(orig.getMember(), w.getMember());
        assertEquals(orig.getPropertyIndex(), w.getPropertyIndex());
        assertSame(orig.getValueDeserializer(), w.getValueDeserializer());
        assertEquals(orig.hasValueDeserializer(), w.hasValueDeserializer());
        assertEquals(orig.hasValueTypeDeserializer(), w.hasValueTypeDeserializer());
        assertSame(orig.getValueTypeDeserializer(), w.getValueTypeDeserializer());
        assertTrue(w.visibleInView(String.class));
        assertEquals(orig.hasViews(), w.hasViews());
        assertNull(w.getInjectableValueId());
        assertFalse(w.isInjectionOnly());
        assertNull(w.getManagedReferenceName());
        assertNull(w.getObjectIdInfo());
        assertNull(w.getAnnotation(JsonIgnore.class));
        assertThrows(IllegalStateException.class, w::getCreatorIndex);
        w.fixAccess(MAPPER.getDeserializationConfig());
        assertSame(w, w.withValueDeserializer(orig.getValueDeserializer()));
        assertTrue(w.withName(PropertyName.construct("b")) instanceof Wrapper);
        assertNotNull(w.toString());

        Plain p = new Plain();
        w.set(p, 4);
        assertEquals(4, p.a);
        assertSame(p, w.setAndReturn(p, 5));
        assertEquals(5, p.a);
        try (JsonParser parser = MAPPER.createParser("9")) {
            parser.nextToken();
            w.deserializeAndSet(parser, ctxtFor(parser), p);
            assertEquals(9, p.a);
        }
        try (JsonParser parser = MAPPER.createParser("11")) {
            parser.nextToken();
            assertSame(p, w.deserializeSetAndReturn(parser, ctxtFor(parser), p));
            assertEquals(11, p.a);
        }
        assertThrows(IllegalStateException.class, () -> w.assignIndex(3));
    }
}
