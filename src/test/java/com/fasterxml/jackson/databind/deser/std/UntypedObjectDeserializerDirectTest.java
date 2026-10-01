package com.fasterxml.jackson.databind.deser.std;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import static org.junit.jupiter.api.Assertions.*;

// Exercises the non-"vanilla" UntypedObjectDeserializer (used when scalar/container
// deserializers are customized), plus merging and typed paths.
public class UntypedObjectDeserializerDirectTest
    extends DatabindTestUtil
{
    static class UpperString extends StdDeserializer<String> {
        private static final long serialVersionUID = 1L;
        UpperString() { super(String.class); }
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getText().toUpperCase();
        }
    }

    static class MinusOne extends StdDeserializer<Number> {
        private static final long serialVersionUID = 1L;
        MinusOne() { super(Number.class); }
        @Override
        public Number deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Integer.valueOf(-1);
        }
    }

    private ObjectMapper customMapper() {
        SimpleModule m = new SimpleModule();
        m.addDeserializer(String.class, new UpperString());
        return jsonMapperBuilder().addModule(m).build();
    }

    private ObjectMapper customNumberMapper() {
        SimpleModule m = new SimpleModule();
        m.addDeserializer(Number.class, new MinusOne());
        return jsonMapperBuilder().addModule(m).build();
    }

    private static String intArray(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; ++i) {
            if (i > 0) sb.append(',');
            sb.append(i);
        }
        return sb.append(']').toString();
    }

    @Test
    public void testCustomStringUsedForValues() throws Exception {
        ObjectMapper mapper = customMapper();
        assertEquals("ABC", mapper.readValue(q("abc"), Object.class));
        Object ob = mapper.readValue("{\"a\":\"x\",\"b\":[\"y\",1,2.5,true,false,null]}", Object.class);
        Map<?,?> map = (Map<?,?>) ob;
        assertEquals("X", map.get("a"));
        assertEquals(Arrays.asList("Y", 1, 2.5, true, false, null), map.get("b"));
    }

    @Test
    public void testCustomNumber() throws Exception {
        ObjectMapper mapper = customNumberMapper();
        assertEquals(-1, mapper.readValue("12", Object.class));
        assertEquals(-1, mapper.readValue("1.5", Object.class));
        assertEquals(Arrays.asList(-1, "x"), mapper.readValue("[3,\"x\"]", Object.class));
    }

    @Test
    public void testArrayShapes() throws Exception {
        ObjectMapper mapper = customMapper();
        assertEquals(new ArrayList<>(), mapper.readValue("[]", Object.class));
        assertEquals(Arrays.asList(1), mapper.readValue("[1]", Object.class));
        assertEquals(Arrays.asList(1, 2), mapper.readValue("[1,2]", Object.class));
        List<?> l = (List<?>) mapper.readValue(intArray(40), Object.class);
        assertEquals(40, l.size());
        assertEquals(39, l.get(39));
    }

    @Test
    public void testArrayToJavaArray() throws Exception {
        ObjectReader r = customMapper().readerFor(Object.class)
                .with(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY);
        assertEquals(0, ((Object[]) r.readValue("[]")).length);
        assertArrayEquals(new Object[] { "A", 2 }, (Object[]) r.readValue("[\"a\",2]"));
        assertEquals(50, ((Object[]) r.readValue(intArray(50))).length);
    }

    @Test
    public void testObjectShapes() throws Exception {
        ObjectMapper mapper = customMapper();
        assertEquals(new LinkedHashMap<>(), mapper.readValue("{}", Object.class));
        Map<?,?> m = (Map<?,?>) mapper.readValue("{\"a\":1,\"b\":2,\"c\":3,\"d\":4}", Object.class);
        assertEquals(4, m.size());
    }

    @Test
    public void testDuplicateKeys() throws Exception {
        ObjectMapper mapper = customMapper();
        Map<?,?> m = (Map<?,?>) mapper.readValue("{\"a\":1,\"a\":2,\"b\":3}", Object.class);
        assertEquals(2, m.get("a"));
        m = (Map<?,?>) mapper.readValue("{\"a\":1,\"b\":2,\"b\":3,\"c\":4}", Object.class);
        assertEquals(3, m.get("b"));
        m = (Map<?,?>) mapper.readValue("{\"a\":1,\"b\":2,\"c\":3,\"c\":4,\"d\":5}", Object.class);
        assertEquals(4, m.get("c"));
        assertEquals(5, m.get("d"));
    }

    @Test
    public void testIntAndFloatCoercions() throws Exception {
        ObjectMapper mapper = customMapper();
        Object ob = mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .readValue("[5]");
        assertEquals(BigInteger.valueOf(5), ((List<?>) ob).get(0));
        ob = mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_LONG_FOR_INTS)
                .readValue("7");
        assertEquals(7L, ob);
        ob = mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue("1.25");
        assertEquals(new BigDecimal("1.25"), ob);
    }

    @Test
    public void testMergeIntoExisting() throws Exception {
        ObjectMapper mapper = customMapper();
        Map<String,Object> base = new LinkedHashMap<>();
        base.put("x", 1);
        List<Object> inner = new ArrayList<>();
        inner.add(1);
        base.put("list", inner);
        Map<String,Object> innerMap = new LinkedHashMap<>();
        innerMap.put("k", "v");
        base.put("map", innerMap);
        Object result = mapper.readerForUpdating(base)
                .readValue("{\"list\":[2],\"map\":{\"z\":null},\"y\":\"s\"}");
        assertSame(base, result);
        assertEquals(Arrays.asList(1, 2), base.get("list"));
        assertTrue(base.get("map") instanceof Map<?,?>);
        assertEquals("S", base.get("y"));

        Map<String,Object> b2 = new LinkedHashMap<>();
        b2.put("a", "old");
        b2.put("b", 3);
        b2.put("c", Boolean.TRUE);
        b2.put("d", new ArrayList<>());
        mapper.readerForUpdating(b2).readValue("{\"a\":\"new\",\"b\":4,\"c\":false,\"d\":{\"q\":1}}");
        assertEquals("NEW", b2.get("a"));
        assertEquals(4, b2.get("b"));
        assertEquals(Boolean.FALSE, b2.get("c"));
        assertTrue(b2.get("d") instanceof Map<?,?>);
    }

    @Test
    public void testUpdateNonContainerTarget() throws Exception {
        ObjectMapper mapper = customMapper();
        ObjectReader r = mapper.readerFor(Object.class).withValueToUpdate("string");
        assertEquals(Arrays.asList(1, 2), r.readValue("[1,2]"));
        assertEquals(Collections.singletonMap("a", 1), r.readValue("{\"a\":1}"));
        assertEquals("X", r.readValue("\"x\""));
        assertEquals((Object) 3, r.readValue("3"));
        assertEquals((Object) 3.5, r.readValue("3.5"));
        assertEquals(Boolean.TRUE, r.readValue("true"));
        assertEquals(Boolean.FALSE, r.readValue("false"));
        assertTrue(r.with(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
                .readValue("[1]") instanceof Object[]);
        assertEquals((Object) 5L, r.with(DeserializationFeature.USE_LONG_FOR_INTS).readValue("5"));
    }

    @Test
    public void testUpdateWithCustomNumber() throws Exception {
        ObjectReader r = customNumberMapper().readerFor(Object.class).withValueToUpdate("string");
        assertEquals((Object) (-1), r.readValue("3"));
        assertEquals((Object) (-1), r.readValue("3.5"));
    }

    @Test
    public void testNonMergingRoot() throws Exception {
        ObjectMapper mapper = customMapper();
        mapper.setDefaultMergeable(false);
        Map<String,Object> base = new LinkedHashMap<>();
        base.put("a", 1);
        Object o = mapper.readerFor(Object.class).withValueToUpdate(base).readValue("{\"b\":2}");
        assertEquals(Collections.singletonMap("b", 2), o);
    }

    @Test
    public void testTypedUntyped() throws Exception {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class).build(),
                        ObjectMapper.DefaultTyping.EVERYTHING)
                .addModule(new SimpleModule().addDeserializer(String.class, new UpperString()))
                .build();
        assertEquals(Arrays.asList(1, 2),
                mapper.readValue("[\"java.util.ArrayList\",[1,2]]", Object.class));
        assertEquals(Collections.singletonMap("a", 3),
                mapper.readValue("[\"java.util.HashMap\",{\"a\":3}]", Object.class));
        assertEquals("ABC", mapper.readValue("\"abc\"", Object.class));
        assertEquals(Boolean.TRUE, mapper.readValue("true", Object.class));
        assertEquals(Boolean.FALSE, mapper.readValue("false", Object.class));
        assertNull(mapper.readValue("null", Object.class));
        assertEquals(1, mapper.readValue("1", Object.class));
        assertEquals(1.5, mapper.readValue("1.5", Object.class));
    }

    @Test
    public void testTypedWithCustomNumber() throws Exception {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class).build(),
                        ObjectMapper.DefaultTyping.EVERYTHING)
                .addModule(new SimpleModule().addDeserializer(Number.class, new MinusOne()))
                .build();
        assertEquals(-1, mapper.readValue("12", Object.class));
        assertEquals(-1, mapper.readValue("1.5", Object.class));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testBasicProperties() throws Exception {
        UntypedObjectDeserializer d = new UntypedObjectDeserializer();
        ObjectMapper mapper = jsonMapperBuilder().build();
        assertNull(d.supportsUpdate(mapper.getDeserializationConfig()));
        assertTrue(d.isCachable());
        assertEquals(LogicalType.Untyped, d.logicalType());
    }

    @Test
    public void testEmbeddedObject() throws Exception {
        ObjectMapper mapper = customMapper();
        TokenBuffer tb = new TokenBuffer(mapper, false);
        tb.writeStartArray();
        tb.writeEmbeddedObject(new AtomicInteger(5));
        tb.writeEndArray();
        Object o = mapper.readValue(tb.asParser(), Object.class);
        assertEquals(1, ((List<?>) o).size());
        assertTrue(((List<?>) o).get(0) instanceof AtomicInteger);
    }

    @Test
    public void testUnexpectedToken() throws Exception {
        try {
            customMapper().readValue("]", Object.class);
            fail("expected exception");
        } catch (JsonProcessingException e) {
            // expected
        }
    }

    @Test
    public void testCustomListAndMapTypes() throws Exception {
        ObjectMapper plain = jsonMapperBuilder().build();
        UntypedObjectDeserializer d = new UntypedObjectDeserializer(
                plain.constructType(LinkedList.class), plain.constructType(TreeMap.class));
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new SimpleModule().addDeserializer(Object.class, d)).build();
        Object o = mapper.readValue("{\"b\":[1,2],\"a\":{\"z\":1}}", Object.class);
        assertTrue(o instanceof TreeMap<?,?>);
        assertTrue(((Map<?,?>) o).get("b") instanceof LinkedList<?>);
    }
}
