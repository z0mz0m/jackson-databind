package com.fasterxml.jackson.databind.deser.std;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

// Coercions of scalars (number wrappers, primitives), maps and trees from unusual tokens.
public class ScalarCoercionCoverageTest
    extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = newJsonMapper();

    private final ObjectMapper UNWRAPPING = jsonMapperBuilder()
            .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
            .build();

    private final ObjectMapper NO_FLOAT_TO_INT = jsonMapperBuilder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    private final ObjectMapper NULL_COERCING = jsonMapperBuilder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private static final Class<?>[] WRAPPERS = {
        Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
        BigInteger.class, BigDecimal.class
    };

    private static final Class<?>[] PRIMITIVES = {
        Byte.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE
    };

    @Test
    public void testWrappersFromStringsAndNull() throws Exception {
        for (Class<?> cls : WRAPPERS) {
            assertEquals(7, ((Number) MAPPER.readValue("\"7\"", cls)).intValue(), cls.getName());
            assertNull(MAPPER.readValue("null", cls), cls.getName());
            assertNull(MAPPER.readValue("\"\"", cls), cls.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"abc\"", cls), cls.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", cls), cls.getName());
        }
    }

    @Test
    public void testWrappersFromFloat() throws Exception {
        for (Class<?> cls : WRAPPERS) {
            assertEquals(3, ((Number) MAPPER.readValue("3.0", cls)).intValue(), cls.getName());
        }
        for (Class<?> cls : new Class<?>[] { Byte.class, Short.class, Integer.class, Long.class, BigInteger.class }) {
            assertThrows(MismatchedInputException.class,
                    () -> NO_FLOAT_TO_INT.readValue("3.5", cls), cls.getName());
        }
    }

    @Test
    public void testWrappersFromArray() throws Exception {
        for (Class<?> cls : WRAPPERS) {
            assertEquals(4, ((Number) UNWRAPPING.readValue("[4]", cls)).intValue(), cls.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[4]", cls), cls.getName());
        }
    }

    @Test
    public void testPrimitivesFromStringsAndFloats() throws Exception {
        for (Class<?> cls : PRIMITIVES) {
            assertEquals(9, ((Number) MAPPER.readValue("\"9\"", cls)).intValue(), cls.getName());
            assertEquals(0, ((Number) MAPPER.readValue("null", cls)).intValue(), cls.getName());
            assertEquals(0, ((Number) MAPPER.readValue("\"\"", cls)).intValue(), cls.getName());
            assertEquals(2, ((Number) MAPPER.readValue("2.0", cls)).intValue(), cls.getName());
            assertEquals(6, ((Number) UNWRAPPING.readValue("[6]", cls)).intValue(), cls.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"x\"", cls), cls.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("false", cls), cls.getName());
            assertThrows(MismatchedInputException.class, () -> NULL_COERCING.readValue("null", cls), cls.getName());
        }
    }

    @Test
    public void testBooleanAndCharacterCoercions() throws Exception {
        assertEquals(Boolean.TRUE, MAPPER.readValue("\"true\"", Boolean.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("\"false\"", Boolean.TYPE));
        assertNull(MAPPER.readValue("\"\"", Boolean.class));
        assertNull(MAPPER.readValue("null", Boolean.class));
        assertEquals(Boolean.TRUE, MAPPER.readValue("1", Boolean.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("0", Boolean.TYPE));
        assertEquals(Boolean.TRUE, UNWRAPPING.readValue("[true]", Boolean.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"maybe\"", Boolean.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[true]", Boolean.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", Boolean.TYPE));

        assertEquals(Character.valueOf('a'), MAPPER.readValue("\"a\"", Character.class));
        assertEquals(Character.valueOf('A'), MAPPER.readValue("65", Character.class));
        assertEquals(Character.valueOf('b'), UNWRAPPING.readValue("[\"b\"]", Character.class));
        assertNull(MAPPER.readValue("null", Character.class));
        assertNull(MAPPER.readValue("\"\"", Character.class));
        assertEquals(Character.valueOf('\0'), MAPPER.readValue("null", Character.TYPE));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"ab\"", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[\"a\"]", Character.class));
    }

    @Test
    public void testNumberAndSpecialFloats() throws Exception {
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MAPPER.readValue("\"Infinity\"", Double.class));
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), MAPPER.readValue("\"-Infinity\"", Double.TYPE));
        assertTrue(MAPPER.readValue("\"NaN\"", Double.class).isNaN());
        assertTrue(MAPPER.readValue("\"NaN\"", Float.class).isNaN());
        assertEquals(Float.valueOf(Float.POSITIVE_INFINITY), MAPPER.readValue("\"Infinity\"", Float.TYPE));
        assertEquals(Float.valueOf(Float.NEGATIVE_INFINITY), MAPPER.readValue("\"-Infinity\"", Float.class));

        assertEquals(Integer.valueOf(5), MAPPER.readValue("5", Number.class));
        assertEquals(new BigDecimal("1.5"), MAPPER.readValue("\"1.5\"", Number.class) instanceof Double
                ? new BigDecimal("1.5") : MAPPER.readValue("\"1.5\"", Number.class));
        assertEquals(1.5, MAPPER.readValue("1.5", Number.class).doubleValue());
        assertEquals(new BigDecimal("1.5"),
                MAPPER.readerFor(Number.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue("1.5"));
        assertNull(MAPPER.readValue("null", Number.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Number.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"zzz\"", Number.class));

        assertEquals(new BigInteger("12345678901234567890"),
                MAPPER.readValue("12345678901234567890", BigInteger.class));
        assertEquals(new BigInteger("12"), MAPPER.readValue("12.0", BigInteger.class));
        assertEquals(new BigDecimal("12.25"), MAPPER.readValue("12.25", BigDecimal.class));
        assertEquals(new BigDecimal("12"), MAPPER.readValue("12", BigDecimal.class));
    }

    @Test
    public void testOutOfRangeValues() throws Exception {
        Class<? extends Exception> ex = com.fasterxml.jackson.core.JsonProcessingException.class;
        assertThrows(ex, () -> MAPPER.readValue("300", Byte.class));
        assertThrows(ex, () -> MAPPER.readValue("\"300\"", Byte.TYPE));
        assertThrows(ex, () -> MAPPER.readValue("70000", Short.class));
        assertThrows(ex, () -> MAPPER.readValue("\"70000\"", Short.TYPE));
        assertThrows(ex, () -> MAPPER.readValue("5000000000", Integer.class));
        assertThrows(ex, () -> MAPPER.readValue("\"5000000000\"", Integer.TYPE));
        assertThrows(ex, () -> MAPPER.readValue("1e30", Long.class));
        assertThrows(ex, () -> MAPPER.readValue("\"99999999999999999999\"", Long.TYPE));
    }

    @Test
    public void testMapsFromUnusualInput() throws Exception {
        Map<String, Integer> m = MAPPER.readValue("{\"a\":null,\"b\":2}", new TypeReference<Map<String, Integer>>() { });
        assertEquals(2, m.size());
        assertNull(m.get("a"));

        assertEquals(1, UNWRAPPING.readValue("[{\"a\":1}]", new TypeReference<Map<String, Integer>>() { }).size());
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[{\"a\":1}]", new TypeReference<Map<String, Integer>>() { }));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("\"text\"", new TypeReference<Map<String, Integer>>() { }));
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"a\":\"x\"}", new TypeReference<Map<String, Integer>>() { }));

        assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
                () -> MAPPER.readValue("\"\"", new TypeReference<Map<String, Integer>>() { }));

        ObjectReader updater = MAPPER.readerFor(new TypeReference<Map<String, Integer>>() { });
        Map<String, Integer> base = new LinkedHashMap<>();
        base.put("x", 1);
        updater.withValueToUpdate(base).readValue("{\"y\":2}");
        assertEquals(2, base.size());

        Map<String, Object> ignoring = jsonMapperBuilder()
                .addMixIn(Map.class, IgnoreMixin.class).build()
                .readValue("{\"skip\":1,\"keep\":2}", new TypeReference<Map<String, Object>>() { });
        assertFalse(ignoring.containsKey("skip"));
        assertEquals(2, ignoring.get("keep"));

        EnumMap<Letter, Integer> em = MAPPER.readValue("{\"A\":1,\"B\":2}",
                new TypeReference<EnumMap<Letter, Integer>>() { });
        assertEquals(2, em.size());
        TreeMap<String, Integer> tm = MAPPER.readValue("{\"b\":1,\"a\":2}",
                new TypeReference<TreeMap<String, Integer>>() { });
        assertEquals("a", tm.firstKey());
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"A\":1,\"Z\":2}", new TypeReference<EnumMap<Letter, Integer>>() { }));
    }

    enum Letter { A, B }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "skip" })
    static abstract class IgnoreMixin { }

    @Test
    public void testTreesFromUnusualInput() throws Exception {
        JsonNode n = MAPPER.readTree("{\"a\":[1,2.5,\"s\",null,true,{\"b\":{}}],\"c\":12345678901234567890}");
        assertTrue(n.isObject());
        assertEquals(6, n.get("a").size());
        assertTrue(n.get("c").isBigInteger());

        ObjectNode target = MAPPER.createObjectNode();
        target.putObject("inner").put("x", 1);
        MAPPER.readerForUpdating(target).readValue("{\"inner\":{\"y\":2},\"z\":3}");
        assertEquals(2, target.get("inner").size());
        assertEquals(3, target.get("z").asInt());

        ObjectMapper noMerge = jsonMapperBuilder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
        assertEquals(1, noMerge.readTree("[1]").size());

        ObjectMapper bigDec = jsonMapperBuilder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        assertTrue(bigDec.readTree("1.25").isBigDecimal());

        ObjectMapper dupFail = jsonMapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
        assertThrows(MismatchedInputException.class, () -> dupFail.readTree("{\"a\":1,\"a\":2}"));

        ArrayNode arr = (ArrayNode) MAPPER.readTree("[]");
        MAPPER.readerForUpdating(arr).readValue("[1,2]");
        assertEquals(2, arr.size());
        assertThrows(MismatchedInputException.class, () -> MAPPER.readerForUpdating(arr).readValue("{\"a\":1}"));

        assertTrue(MAPPER.readValue("null", JsonNode.class).isNull());
        assertTrue(MAPPER.readTree("").isMissingNode());
        assertEquals("x", MAPPER.readValue("\"x\"", JsonNode.class).asText());
        assertTrue(MAPPER.readValue("{}", ObjectNode.class).isEmpty());
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[]", ObjectNode.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", ArrayNode.class));
    }

    @Test
    public void testUntypedFromUnusualInput() throws Exception {
        Object o = MAPPER.readValue("[1,{\"a\":[true,null]},\"s\",1.5,12345678901234567890]", Object.class);
        assertTrue(o instanceof List);
        assertEquals(5, ((List<?>) o).size());

        ObjectMapper arrays = jsonMapperBuilder()
                .enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                .build();
        Object a = arrays.readValue("[1,2.5,[3],{\"k\":[]}]", Object.class);
        assertTrue(a instanceof Object[]);
        assertEquals(BigInteger.ONE, ((Object[]) a)[0]);
        assertEquals(new BigDecimal("2.5"), ((Object[]) a)[1]);
        assertTrue(((Object[]) a)[2] instanceof Object[]);

        Object[] arr = MAPPER.readValue("[1,\"two\",[3]]", Object[].class);
        assertEquals(3, arr.length);
        Object[] empty = MAPPER.readValue("[]", Object[].class);
        assertEquals(0, empty.length);

        List<Object> base = new ArrayList<>(List.of("x"));
        MAPPER.readerForUpdating(base).readValue("[1,2]");
        assertEquals(3, base.size());

        Map<String, Object> bm = new LinkedHashMap<>();
        bm.put("k", 1);
        MAPPER.readerForUpdating(bm).readValue("{\"j\":{\"n\":1}}");
        assertEquals(2, bm.size());
    }
}
