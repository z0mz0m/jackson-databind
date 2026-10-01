package com.fasterxml.jackson.databind.deser.std;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

// Map keys of many scalar types (StdKeyDeserializer) and number deserializers.
public class KeyAndNumberCoverageTest
    extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = newJsonMapper();

    private <K> K firstKey(String json, TypeReference<Map<K, Integer>> t) throws Exception {
        return MAPPER.readValue(json, t).keySet().iterator().next();
    }

    @Test
    public void testScalarKeys() throws Exception {
        assertEquals(Boolean.TRUE, firstKey("{\"true\":1}", new TypeReference<Map<Boolean, Integer>>() { }));
        assertEquals(Byte.valueOf((byte) 12), firstKey("{\"12\":1}", new TypeReference<Map<Byte, Integer>>() { }));
        assertEquals(Short.valueOf((short) 300), firstKey("{\"300\":1}", new TypeReference<Map<Short, Integer>>() { }));
        assertEquals(Character.valueOf('x'), firstKey("{\"x\":1}", new TypeReference<Map<Character, Integer>>() { }));
        assertEquals(Long.valueOf(5L), firstKey("{\"5\":1}", new TypeReference<Map<Long, Integer>>() { }));
        assertEquals(Float.valueOf(1.5f), firstKey("{\"1.5\":1}", new TypeReference<Map<Float, Integer>>() { }));
        assertEquals(Double.valueOf(2.5), firstKey("{\"2.5\":1}", new TypeReference<Map<Double, Integer>>() { }));
        assertEquals(Locale.FRANCE, firstKey("{\"fr_FR\":1}", new TypeReference<Map<Locale, Integer>>() { }));
        assertEquals(Currency.getInstance("EUR"), firstKey("{\"EUR\":1}", new TypeReference<Map<Currency, Integer>>() { }));
        UUID u = UUID.randomUUID();
        assertEquals(u, firstKey("{\"" + u + "\":1}", new TypeReference<Map<UUID, Integer>>() { }));
        assertEquals(URI.create("http://a/b"), firstKey("{\"http://a/b\":1}", new TypeReference<Map<URI, Integer>>() { }));
        assertEquals("http://a/b", firstKey("{\"http://a/b\":1}", new TypeReference<Map<URL, Integer>>() { }).toString());
        assertEquals(String.class, firstKey("{\"java.lang.String\":1}", new TypeReference<Map<Class<?>, Integer>>() { }));
        assertNotNull(firstKey("{\"2020-01-02T03:04:05.000+00:00\":1}", new TypeReference<Map<Date, Integer>>() { }));
        assertNotNull(firstKey("{\"2020-01-02T03:04:05.000+00:00\":1}", new TypeReference<Map<Calendar, Integer>>() { }));
        assertArrayEquals(new byte[] {1, 2, 3}, firstKey("{\"AQID\":1}", new TypeReference<Map<byte[], Integer>>() { }));
    }

    private void assertBadKey(Class<?> keyType, String key) {
        JavaType t = MAPPER.getTypeFactory().constructMapType(Map.class, keyType, Integer.class);
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"" + key + "\":1}", t), "key type " + keyType.getSimpleName());
    }

    @Test
    public void testBadKeys() throws Exception {
        assertBadKey(Boolean.class, "maybe");
        assertBadKey(Byte.class, "999");
        assertBadKey(Short.class, "70000");
        assertBadKey(Character.class, "ab");
        assertBadKey(Long.class, "abc");
        assertBadKey(Float.class, "abc");
        assertBadKey(Double.class, "abc");
        assertBadKey(UUID.class, "abc");
        assertBadKey(URI.class, "http://a b");
        assertBadKey(URL.class, "nope");
        assertBadKey(Class.class, "no.such.Clazz");
        assertBadKey(byte[].class, "!!!");
        assertBadKey(Currency.class, "zzzz");
        assertBadKey(Integer.class, "xx");
        assertBadKey(Date.class, "notadate");
    }

    enum E { A, B }

    @Test
    public void testEnumKeys() throws Exception {
        Map<E, Integer> m = MAPPER.readValue("{\"A\":1}", new TypeReference<Map<E, Integer>>() { });
        assertEquals(1, m.get(E.A));
        ObjectMapper lenient = newJsonMapper().enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        Map<E, Integer> m2 = lenient.readValue("{\"Z\":1}", new TypeReference<Map<E, Integer>>() { });
        assertEquals(1, m2.get(null));
        assertThrows(InvalidFormatException.class,
                () -> MAPPER.readValue("{\"Z\":1}", new TypeReference<Map<E, Integer>>() { }));
    }

    @Test
    public void testNumberCoercions() throws Exception {
        assertEquals(5, MAPPER.readValue("5.0", int.class));
        assertEquals(5L, MAPPER.readValue("\"5\"", Long.class));
        assertEquals((short) 7, MAPPER.readValue("7.0", Short.class));
        assertEquals((byte) 7, MAPPER.readValue("\"7\"", byte.class));
        assertEquals(1.5f, MAPPER.readValue("\"1.5\"", float.class));
        assertEquals(1.5, MAPPER.readValue("\"1.5\"", Double.class));
        assertEquals(Double.POSITIVE_INFINITY, MAPPER.readValue("\"Infinity\"", double.class));
        assertEquals(Double.NEGATIVE_INFINITY, MAPPER.readValue("\"-Infinity\"", Double.class));
        assertTrue(Double.isNaN(MAPPER.readValue("\"NaN\"", Double.class)));
        assertTrue(Float.isNaN(MAPPER.readValue("\"NaN\"", float.class)));
        assertEquals(Float.POSITIVE_INFINITY, MAPPER.readValue("\"Infinity\"", Float.class));
        assertEquals(Boolean.TRUE, MAPPER.readValue("\"true\"", Boolean.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("0", boolean.class));
        assertEquals(Character.valueOf('a'), MAPPER.readValue("\"a\"", Character.class));
        assertEquals('a', MAPPER.readValue("97", char.class));
        assertEquals(new BigInteger("12345678901234567890"),
                MAPPER.readValue("\"12345678901234567890\"", BigInteger.class));
        assertEquals(new BigDecimal("1.25"), MAPPER.readValue("\"1.25\"", BigDecimal.class));
        assertEquals(new BigDecimal("3"), MAPPER.readValue("3", BigDecimal.class));
    }

    @Test
    public void testNumberNulls() throws Exception {
        assertNull(MAPPER.readValue("null", Integer.class));
        assertNull(MAPPER.readValue("\"\"", Integer.class));
        assertNull(MAPPER.readValue("\"\"", Double.class));
        assertEquals(0, MAPPER.readValue("null", int.class));
        assertEquals(0.0, MAPPER.readValue("null", double.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("null", boolean.class));
        assertEquals(0L, MAPPER.readValue("\"\"", long.class));
        ObjectMapper strict = newJsonMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        for (Class<?> prim : new Class<?>[] { int.class, long.class, double.class, float.class,
                short.class, byte.class, boolean.class, char.class }) {
            assertThrows(MismatchedInputException.class, () -> strict.readValue("null", prim),
                    prim.getSimpleName());
        }
    }

    @Test
    public void testNumberFailures() throws Exception {
        for (Class<?> t : new Class<?>[] { int.class, Long.class, Double.class, float.class, Short.class,
                Byte.class, BigInteger.class, BigDecimal.class, Boolean.class }) {
            assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"abc\"", t),
                    t.getSimpleName());
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> MAPPER.readValue("300", byte.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> MAPPER.readValue("70000", short.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> MAPPER.readValue("3000000000", int.class));
        assertThrows(com.fasterxml.jackson.core.exc.InputCoercionException.class,
                () -> MAPPER.readValue("1e30", long.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("70000", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", Integer.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", Boolean.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", Character.class));
    }

    @Test
    public void testStrictCoercions() throws Exception {
        ObjectMapper noFloat = newJsonMapper().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        assertThrows(MismatchedInputException.class, () -> noFloat.readValue("5.5", int.class));
        assertThrows(MismatchedInputException.class, () -> noFloat.readValue("5.5", Long.class));
        assertThrows(MismatchedInputException.class, () -> noFloat.readValue("5.5", short.class));
        assertThrows(MismatchedInputException.class, () -> noFloat.readValue("5.5", Byte.class));
        ObjectMapper wrapped = newJsonMapper().enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
        assertEquals(3, wrapped.readValue("[3]", int.class));
        assertEquals(3L, wrapped.readValue("[3]", Long.class));
        assertEquals(3.0, wrapped.readValue("[3]", double.class));
        assertEquals(3.0f, wrapped.readValue("[3]", Float.class));
        assertEquals(Boolean.TRUE, wrapped.readValue("[true]", Boolean.class));
        assertEquals((short) 3, wrapped.readValue("[3]", short.class));
        assertEquals((byte) 3, wrapped.readValue("[3]", Byte.class));
        assertEquals('c', wrapped.readValue("[\"c\"]", char.class));
        assertEquals(new BigDecimal("3"), wrapped.readValue("[3]", BigDecimal.class));
        assertEquals(BigInteger.valueOf(3), wrapped.readValue("[3]", BigInteger.class));
        assertEquals(3, wrapped.readValue("[3]", Number.class));
        assertThrows(MismatchedInputException.class, () -> wrapped.readValue("[3,4]", int.class));
    }

    @Test
    public void testNumberTypeAndBigNumbers() throws Exception {
        assertEquals(Integer.valueOf(5), MAPPER.readValue("5", Number.class));
        assertEquals(Double.valueOf(5.5), MAPPER.readValue("5.5", Number.class));
        assertEquals(Double.valueOf(5.5), MAPPER.readValue("\"5.5\"", Number.class));
        assertEquals(Integer.valueOf(5), MAPPER.readValue("\"5\"", Number.class));
        assertEquals(Long.valueOf(5000000000L), MAPPER.readValue("\"5000000000\"", Number.class));
        assertEquals(new BigDecimal("1.5"),
                newJsonMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .readValue("1.5", Number.class));
        assertEquals(new BigInteger("100000000000000000000"),
                MAPPER.readValue("100000000000000000000", BigInteger.class));
        assertEquals(new BigInteger("7"), MAPPER.readValue("7.0", BigInteger.class));
        assertNull(MAPPER.readValue("\"\"", Number.class));
        assertThrows(InvalidFormatException.class, () -> MAPPER.readValue("\"abc\"", Number.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", Number.class));
        assertEquals(5, MAPPER.readValue("5", AtomicInteger.class).get());
        assertTrue(MAPPER.readValue("true", AtomicBoolean.class).get());
        assertEquals(9L, MAPPER.readValue("9", AtomicLong.class).get());
    }
}
