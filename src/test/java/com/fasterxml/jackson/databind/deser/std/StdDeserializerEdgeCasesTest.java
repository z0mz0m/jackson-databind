package com.fasterxml.jackson.databind.deser.std;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

// Edge cases of number, character and boolean deserializers and of untyped/map handling.
public class StdDeserializerEdgeCasesTest
    extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = newJsonMapper();

    private final ObjectMapper UNWRAPPING = jsonMapperBuilder()
            .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
            .build();

    private ObjectMapper coercing(CoercionAction a) {
        ObjectMapper m = newJsonMapper();
        m.coercionConfigDefaults()
            .setCoercion(CoercionInputShape.Integer, a)
            .setCoercion(CoercionInputShape.Float, a)
            .setCoercion(CoercionInputShape.EmptyString, a)
            .setCoercion(CoercionInputShape.String, a);
        return m;
    }

    @Test
    public void testCharacterVariants() throws Exception {
        assertEquals(Character.valueOf('A'), MAPPER.readValue("65", Character.class));
        assertEquals(Character.valueOf('x'), MAPPER.readValue("\"x\"", Character.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("70000", Character.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"ab\"", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Character.class));
        assertNull(MAPPER.readValue("null", Character.class));
        assertNull(MAPPER.readValue("\"\"", Character.class));
        assertEquals(Character.valueOf('q'), UNWRAPPING.readValue("[\"q\"]", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[\"q\"]", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{\"a\":\"q\"}", Character.class));
        assertEquals('\0', (char) MAPPER.readValue("null", Character.TYPE));
    }

    @Test
    public void testCharacterCoercionToNullAndEmpty() throws Exception {
        assertNull(coercing(CoercionAction.AsNull).readValue("65", Character.class));
        assertNull(coercing(CoercionAction.AsNull).readValue("\"ab\"", Character.class));
        assertNotNull(coercing(CoercionAction.AsEmpty).readValue("65", Character.class));
        assertNotNull(coercing(CoercionAction.AsEmpty).readValue("\"ab\"", Character.class));
        assertThrows(MismatchedInputException.class,
                () -> coercing(CoercionAction.Fail).readValue("65", Character.class));
    }

    @Test
    public void testPrimitiveCharFailOnNull() throws Exception {
        ObjectMapper m = jsonMapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        assertThrows(MismatchedInputException.class, () -> m.readValue("null", Character.TYPE));
    }

    @Test
    public void testShortAndByteEdgeCases() throws Exception {
        assertEquals(Short.valueOf((short) 5), MAPPER.readValue("\" 5 \"", Short.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"70000\"", Short.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"x\"", Short.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"300\"", Byte.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"x\"", Byte.class));
        assertEquals(Short.valueOf((short) 4), UNWRAPPING.readValue("[4]", Short.class));
        assertEquals(Byte.valueOf((byte) 4), UNWRAPPING.readValue("[4]", Byte.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{\"a\":1}", Short.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{\"a\":1}", Byte.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[4]", Byte.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[4]", Short.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Short.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Byte.class));
    }

    @Test
    public void testNumberCoercionsToNullAndEmpty() throws Exception {
        for (Class<?> cls : new Class<?>[] { Byte.class, Short.class, Integer.class, Long.class,
                Float.class, Double.class }) {
            for (CoercionAction a : new CoercionAction[] { CoercionAction.AsNull, CoercionAction.AsEmpty }) {
                ObjectMapper m = coercing(a);
                assertDoesNotThrow(() -> m.readValue("3.5", cls), cls + " float " + a);
                assertDoesNotThrow(() -> m.readValue("\"3\"", cls), cls + " string " + a);
                assertDoesNotThrow(() -> m.readValue("\"\"", cls), cls + " empty " + a);
            }
            assertThrows(MismatchedInputException.class,
                    () -> coercing(CoercionAction.Fail).readValue("\"\"", cls), cls.getName());
        }
        assertNull(coercing(CoercionAction.AsNull).readValue("\"3\"", Short.class));
        assertNull(coercing(CoercionAction.AsNull).readValue("\"3\"", Byte.class));
        assertEquals(Short.valueOf((short) 0), coercing(CoercionAction.AsEmpty).readValue("\"3\"", Short.class));
    }

    @Test
    public void testBooleanVariants() throws Exception {
        assertEquals(Boolean.TRUE, MAPPER.readValue("true", Boolean.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("\"false\"", Boolean.class));
        assertEquals(Boolean.TRUE, MAPPER.readValue("1", Boolean.class));
        assertNull(MAPPER.readValue("null", Boolean.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"maybe\"", Boolean.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"maybe\"", Boolean.TYPE));
        assertEquals(Boolean.TRUE, UNWRAPPING.readValue("[true]", Boolean.TYPE));
        assertEquals(Boolean.TRUE, UNWRAPPING.readValue("[true]", Boolean.class));
        assertFalse(MAPPER.readValue("0", Boolean.TYPE));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[true]", Boolean.class));
    }

    @Test
    public void testBooleanWithTypeInfo() throws Exception {
        ObjectMapper m = jsonMapperBuilder().build();
        m.activateDefaultTyping(m.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        Object[] in = new Object[] { Boolean.TRUE, Boolean.FALSE };
        String json = m.writerFor(Object[].class).writeValueAsString(in);
        Object[] out = m.readValue(json, Object[].class);
        assertEquals(Boolean.TRUE, out[0]);
        assertEquals(Boolean.FALSE, out[1]);
        assertEquals(Boolean.TRUE, m.readValue("true", Boolean.class));
        assertEquals(Boolean.TRUE, m.readValue("\"true\"", Boolean.class));
        assertEquals(Boolean.TRUE, m.readValue("\"true\"", Boolean.TYPE));
        assertNull(m.readValue("null", Boolean.class));
    }

    @Test
    public void testMapVariants() throws Exception {
        Map<String, Integer> map = MAPPER.readValue("{\"a\":1,\"b\":2}", new TypeReference<Map<String, Integer>>() { });
        assertEquals(2, map.size());
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[1]", new TypeReference<Map<String, Integer>>() { }));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("\"abc\"", new TypeReference<Map<String, Integer>>() { }));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"a\":\"x\"}", new TypeReference<Map<String, Integer>>() { }));
        assertThrows(InvalidFormatException.class,
                () -> MAPPER.readValue("\"\"", new TypeReference<Map<String, Integer>>() { }));
        Map<String, Integer> empty = coercing(CoercionAction.AsEmpty)
                .readValue("\"\"", new TypeReference<Map<String, Integer>>() { });
        assertTrue(empty.isEmpty());
        assertNull(coercing(CoercionAction.AsNull)
                .readValue("\"\"", new TypeReference<Map<String, Integer>>() { }));
        Map<String, Integer> nulls = MAPPER.readValue("{\"a\":null}", new TypeReference<Map<String, Integer>>() { });
        assertTrue(nulls.containsKey("a"));
        assertNull(nulls.get("a"));
        Map<String, Integer> dup = jsonMapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .build().readValue("{\"a\":1,\"a\":2}", new TypeReference<Map<String, Integer>>() { });
        assertEquals(Integer.valueOf(2), dup.get("a"));
    }

    @Test
    public void testMapVariantsContinued() throws Exception {
        Map<Integer, String> intKeys = MAPPER.readValue("{\"1\":\"a\"}", new TypeReference<Map<Integer, String>>() { });
        assertEquals("a", intKeys.get(1));
        assertThrows(InvalidFormatException.class,
                () -> MAPPER.readValue("{\"x\":\"a\"}", new TypeReference<Map<Integer, String>>() { }));
        Map<String, List<Integer>> nested = MAPPER.readValue("{\"a\":[1,2]}",
                new TypeReference<Map<String, List<Integer>>>() { });
        assertEquals(Arrays.asList(1, 2), nested.get("a"));
        EnumMap<Thread.State, Integer> em = MAPPER.readValue("{\"NEW\":1}",
                new TypeReference<EnumMap<Thread.State, Integer>>() { });
        assertEquals(1, em.size());
        SortedMap<String, Integer> sm = MAPPER.readValue("{\"b\":1,\"a\":2}",
                new TypeReference<SortedMap<String, Integer>>() { });
        assertEquals("a", sm.firstKey());
        LinkedHashMap<String, Object> lhm = MAPPER.readValue("{\"b\":1,\"a\":[1]}",
                new TypeReference<LinkedHashMap<String, Object>>() { });
        assertEquals("b", lhm.keySet().iterator().next());
        Map<String, Object> ignore = jsonMapperBuilder()
                .withConfigOverride(Map.class, o -> o.setIgnorals(
                        com.fasterxml.jackson.annotation.JsonIgnoreProperties.Value.forIgnoredProperties("skip")))
                .build().readValue("{\"skip\":1,\"keep\":2}", new TypeReference<Map<String, Object>>() { });
        assertEquals(1, ignore.size());
    }

    @Test
    public void testUntypedVariants() throws Exception {
        Object o = MAPPER.readValue("{\"a\":[1,2.5,\"x\",null,true],\"b\":{\"c\":1}}", Object.class);
        assertTrue(o instanceof Map);
        ObjectMapper big = jsonMapperBuilder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
                .build();
        Object arr = big.readValue("[1,2.5,{\"a\":[3]}]", Object.class);
        assertTrue(arr instanceof Object[]);
        assertEquals(java.math.BigInteger.ONE, ((Object[]) arr)[0]);
        assertEquals(new java.math.BigDecimal("2.5"), ((Object[]) arr)[1]);
        assertTrue(big.readValue("[]", Object.class) instanceof Object[]);
        assertEquals(1L, MAPPER.readValue("1", Object.class) instanceof Integer ? 1L : 0L);
        assertEquals(Long.MAX_VALUE, MAPPER.readValue(String.valueOf(Long.MAX_VALUE), Object.class));
        assertTrue(MAPPER.readValue("123456789012345678901234567890", Object.class) instanceof java.math.BigInteger);
        assertEquals("", MAPPER.readValue("\"\"", Object.class));
        assertEquals(Collections.emptyMap(), MAPPER.readValue("{}", Object.class));
        assertEquals(Collections.emptyList(), MAPPER.readValue("[]", Object.class));
        assertEquals(Arrays.asList(Arrays.asList(1)), MAPPER.readValue("[[1]]", Object.class));
        assertEquals(Double.valueOf(1.5), MAPPER.readValue("1.5", Object.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("false", Object.class));
        assertTrue(MAPPER.readValue("{\"a\":1,\"a\":2}", Object.class) instanceof Map);
    }

    @Test
    public void testJsonNodeVariants() throws Exception {
        JsonNode n = MAPPER.readTree("{\"a\":[1,2,{\"b\":null}],\"c\":1.5,\"d\":\"s\",\"e\":true}");
        assertEquals(4, n.size());
        assertTrue(n.get("a").get(2).get("b").isNull());
        ObjectMapper dup = jsonMapperBuilder().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
        assertThrows(MismatchedInputException.class, () -> dup.readTree("{\"a\":1,\"a\":2}"));
        assertThrows(MismatchedInputException.class, () -> dup.readTree("[{\"a\":1,\"a\":2}]"));
        ObjectMapper big = jsonMapperBuilder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        assertTrue(big.readTree("1.5").isBigDecimal());
        assertTrue(MAPPER.readTree("12345678901234567890123").isBigInteger());
        assertTrue(MAPPER.readTree("12345678901").isLong());
        assertTrue(MAPPER.readTree("\"\"").isTextual());
        assertTrue(MAPPER.readValue("null", JsonNode.class).isNull());
        assertTrue(MAPPER.readValue("[]", com.fasterxml.jackson.databind.node.ArrayNode.class).isEmpty());
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[]", com.fasterxml.jackson.databind.node.ObjectNode.class));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{}", com.fasterxml.jackson.databind.node.ArrayNode.class));
        JsonNode merged = MAPPER.readerForUpdating(MAPPER.createObjectNode().put("x", 1))
                .readValue("{\"y\":2}");
        assertEquals(2, merged.size());
        assertTrue(MAPPER.readValue("{\"a\":{\"b\":[]}}", com.fasterxml.jackson.databind.node.ObjectNode.class)
                .get("a").get("b").isArray());
    }
}
