package com.fasterxml.jackson.databind.deser.std;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;
import com.fasterxml.jackson.databind.type.LogicalType;

import static org.junit.jupiter.api.Assertions.*;

// Exercises UntypedObjectDeserializer.Vanilla directly, plus primitive array and number paths.
@SuppressWarnings("deprecation")
public class VanillaAndArraysCoverageTest
    extends DatabindTestUtil
{
    private ObjectMapper vanillaMapper(boolean nonMerging) {
        SimpleModule m = new SimpleModule();
        m.addDeserializer(Object.class, UntypedObjectDeserializer.Vanilla.instance(nonMerging));
        return jsonMapperBuilder().addModule(m).build();
    }

    @Test
    public void testVanillaBasics() throws Exception {
        assertSame(UntypedObjectDeserializer.Vanilla.std, UntypedObjectDeserializer.Vanilla.instance(false));
        UntypedObjectDeserializer.Vanilla nm = UntypedObjectDeserializer.Vanilla.instance(true);
        assertNotSame(UntypedObjectDeserializer.Vanilla.std, nm);
        assertEquals(LogicalType.Untyped, nm.logicalType());
        assertEquals(Boolean.FALSE, nm.supportsUpdate(null));
        assertNull(UntypedObjectDeserializer.Vanilla.std.supportsUpdate(null));
    }

    @Test
    public void testVanillaScalarsAndStructures() throws Exception {
        ObjectMapper mapper = vanillaMapper(false);
        assertEquals(new LinkedHashMap<String, Object>(), mapper.readValue("{}", Object.class));
        assertEquals(new ArrayList<Object>(), mapper.readValue("[]", Object.class));
        assertEquals("x", mapper.readValue("\"x\"", Object.class));
        assertEquals(Integer.valueOf(3), mapper.readValue("3", Object.class));
        assertEquals(Double.valueOf(1.5), mapper.readValue("1.5", Object.class));
        assertEquals(Boolean.TRUE, mapper.readValue("true", Object.class));
        assertEquals(Boolean.FALSE, mapper.readValue("false", Object.class));
        assertNull(mapper.readValue("null", Object.class));
        assertEquals(Collections.singletonList(1), mapper.readValue("[1]", Object.class));
        assertEquals(Collections.singletonMap("a", 1), mapper.readValue("{\"a\":1}", Object.class));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 40; ++i) {
            if (i > 0) sb.append(',');
            sb.append(i);
        }
        sb.append(']');
        List<?> l = (List<?>) mapper.readValue(sb.toString(), Object.class);
        assertEquals(40, l.size());
        assertEquals(39, l.get(39));

        Map<?, ?> m = (Map<?, ?>) mapper.readValue("{\"a\":1,\"b\":[true],\"c\":{\"d\":null}}", Object.class);
        assertEquals(3, m.size());
        assertEquals(Collections.singletonList(true), m.get("b"));
    }

    @Test
    public void testVanillaFeatures() throws Exception {
        ObjectMapper mapper = vanillaMapper(false);
        Object[] arr = (Object[]) mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
                .readValue("[1,\"a\",[2]]");
        assertEquals(3, arr.length);
        Object empty = mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)
                .readValue("[]");
        assertEquals(0, ((Object[]) empty).length);
        assertEquals(BigInteger.TEN, mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).readValue("10"));
        assertEquals(new BigDecimal("1.5"), mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue("1.5"));
        assertEquals(Long.valueOf(7L), mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_LONG_FOR_INTS).readValue("7"));
    }

    @Test
    public void testVanillaDuplicates() throws Exception {
        ObjectMapper mapper = vanillaMapper(false);
        Map<?, ?> m = (Map<?, ?>) mapper.readValue("{\"a\":1,\"b\":2,\"a\":3,\"c\":4}", Object.class);
        assertEquals(3, m.get("a"));
        assertEquals(4, m.get("c"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testVanillaMerging() throws Exception {
        ObjectMapper mapper = vanillaMapper(false);
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("a", 1);
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        base.put("m", inner);
        base.put("l", new ArrayList<Object>(Arrays.asList(1)));
        Map<String, Object> res = (Map<String, Object>) mapper.readerForUpdating(base)
                .readValue("{\"b\":2,\"m\":{\"y\":2},\"l\":[2,3]}");
        assertSame(base, res);
        assertEquals(2, res.get("b"));
        assertEquals(2, ((Map<?, ?>) res.get("m")).size());
        assertEquals(Arrays.asList(1, 2, 3), res.get("l"));

        assertSame(base, mapper.readerForUpdating(base).readValue("{}"));
        List<Object> list = new ArrayList<>(Arrays.asList("z"));
        assertSame(list, mapper.readerForUpdating(list).readValue("[]"));
        assertEquals(Arrays.asList("z", 1, 2), mapper.readerForUpdating(list).readValue("[1,2]"));

        ObjectMapper nm = vanillaMapper(true);
        Map<String, Object> base2 = new LinkedHashMap<>();
        base2.put("a", 1);
        Object r2 = nm.readerForUpdating(base2).readValue("{\"b\":2}");
        assertEquals(2, ((Map<?, ?>) r2).get("b"));
    }

    @Test
    public void testVanillaTyped() throws Exception {
        SimpleModule m = new SimpleModule();
        m.addDeserializer(Object.class, UntypedObjectDeserializer.Vanilla.std);
        ObjectMapper mapper = jsonMapperBuilder().addModule(m)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                        ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT)
                .build();
        assertEquals("x", mapper.readValue("\"x\"", Object.class));
        assertEquals(Boolean.TRUE, mapper.readValue("true", Object.class));
        assertEquals(Boolean.FALSE, mapper.readValue("false", Object.class));
        assertEquals(Integer.valueOf(5), mapper.readValue("5", Object.class));
        assertEquals(Double.valueOf(1.5), mapper.readValue("1.5", Object.class));
        assertEquals(Arrays.asList(1, 2), mapper.readValue("[\"java.util.ArrayList\",[1,2]]", Object.class));
        assertEquals(BigInteger.TEN, mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).readValue("10"));
        assertEquals(new BigDecimal("1.5"), mapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue("1.5"));
    }

    @Test
    public void testVanillaUnexpectedToken() throws Exception {
        ObjectMapper mapper = vanillaMapper(false);
        assertThrows(com.fasterxml.jackson.core.JsonParseException.class, () -> mapper.readValue("]", Object.class));
    }

    /*
    /**********************************************************************
    /* Primitive arrays
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    private ObjectReader single(Class<?> t) {
        return MAPPER.readerFor(t).with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    private ObjectReader unwrap(Class<?> t) {
        return MAPPER.readerFor(t).with(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
    }

    @Test
    public void testPrimitiveArraysSingleValue() throws Exception {
        assertArrayEquals(new boolean[] { true }, (boolean[]) single(boolean[].class).readValue("true"));
        assertArrayEquals(new short[] { 3 }, (short[]) single(short[].class).readValue("3"));
        assertArrayEquals(new int[] { 3 }, (int[]) single(int[].class).readValue("3"));
        assertArrayEquals(new long[] { 3L }, (long[]) single(long[].class).readValue("3"));
        assertArrayEquals(new float[] { 1.5f }, (float[]) single(float[].class).readValue("1.5"), 0f);
        assertArrayEquals(new double[] { 1.5 }, (double[]) single(double[].class).readValue("1.5"), 0.0);
        assertArrayEquals(new byte[] { 7 }, (byte[]) single(byte[].class).readValue("7"));
        assertArrayEquals(new char[] { 'a' }, (char[]) single(char[].class).readValue("\"a\""));
    }

    @Test
    public void testPrimitiveArraysNullsAndEmpty() throws Exception {
        for (Class<?> t : new Class<?>[] { boolean[].class, short[].class, int[].class, long[].class,
                float[].class, double[].class, byte[].class }) {
            assertNull(MAPPER.readValue("null", t));
        }
        assertEquals(0, ((int[]) MAPPER.readValue("[]", int[].class)).length);
        assertEquals(0, ((double[]) MAPPER.readValue("[]", double[].class)).length);
    }

    @Test
    public void testPrimitiveArraysUnwrap() throws Exception {
        assertArrayEquals(new int[] { 4 }, (int[]) unwrap(int[].class).readValue("[[4]]"));
        assertArrayEquals(new long[] { 4 }, (long[]) unwrap(long[].class).readValue("[[4]]"));
        assertArrayEquals(new short[] { 4 }, (short[]) unwrap(short[].class).readValue("[[4]]"));
        assertArrayEquals(new boolean[] { false }, (boolean[]) unwrap(boolean[].class).readValue("[[false]]"));
        assertArrayEquals(new double[] { 4.5 }, (double[]) unwrap(double[].class).readValue("[[4.5]]"), 0.0);
        assertArrayEquals(new float[] { 4.5f }, (float[]) unwrap(float[].class).readValue("[[4.5]]"), 0f);
    }

    @Test
    public void testPrimitiveArraysContent() throws Exception {
        assertArrayEquals(new boolean[] { true, false }, MAPPER.readValue("[true,false]", boolean[].class));
        assertArrayEquals(new boolean[] { true, false }, MAPPER.readValue("[1,0]", boolean[].class));
        assertArrayEquals(new boolean[] { true, false }, MAPPER.readValue("[\"true\",\"false\"]", boolean[].class));
        assertArrayEquals(new short[] { 1, 2 }, MAPPER.readValue("[1,2.0]", short[].class));
        assertArrayEquals(new short[] { 1, 2 }, MAPPER.readValue("[\"1\",\"2\"]", short[].class));
        assertArrayEquals(new int[] { 1, 2 }, MAPPER.readValue("[1,\"2\"]", int[].class));
        assertArrayEquals(new long[] { 1, 2 }, MAPPER.readValue("[1,\"2\"]", long[].class));
        assertArrayEquals(new float[] { 1f, 2f }, MAPPER.readValue("[1,\"2\"]", float[].class), 0f);
        assertArrayEquals(new double[] { 1, 2 }, MAPPER.readValue("[1,\"2\"]", double[].class), 0.0);
        assertArrayEquals(new byte[] { 1, 2 }, MAPPER.readValue("[1,\"2\"]", byte[].class));
        assertArrayEquals(new byte[] { 1, 2 }, MAPPER.readValue("\"AQI=\"", byte[].class));
        assertArrayEquals(new char[] { 'a', 'b' }, MAPPER.readValue("\"ab\"", char[].class));
        assertArrayEquals(new char[] { 'a', 'b' }, MAPPER.readValue("[\"a\",\"b\"]", char[].class));
        assertArrayEquals(new int[] { 0, 0 }, MAPPER.readValue("[null,null]", int[].class));
        assertArrayEquals(new boolean[] { false }, MAPPER.readValue("[null]", boolean[].class));
        assertArrayEquals(new double[] { 0 }, MAPPER.readValue("[null]", double[].class), 0.0);
        assertArrayEquals(new long[] { 0 }, MAPPER.readValue("[null]", long[].class));
        assertArrayEquals(new short[] { 0 }, MAPPER.readValue("[null]", short[].class));
        assertArrayEquals(new float[] { 0 }, MAPPER.readValue("[null]", float[].class), 0f);
        assertArrayEquals(new byte[] { 0 }, MAPPER.readValue("[null]", byte[].class));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 300; ++i) {
            if (i > 0) sb.append(',');
            sb.append(i % 100);
        }
        sb.append(']');
        String json = sb.toString();
        assertEquals(300, ((int[]) MAPPER.readValue(json, int[].class)).length);
        assertEquals(300, ((long[]) MAPPER.readValue(json, long[].class)).length);
        assertEquals(300, ((double[]) MAPPER.readValue(json, double[].class)).length);
        assertEquals(300, ((float[]) MAPPER.readValue(json, float[].class)).length);
        assertEquals(300, ((short[]) MAPPER.readValue(json, short[].class)).length);
        assertEquals(300, ((byte[]) MAPPER.readValue(json, byte[].class)).length);
    }

    @Test
    public void testPrimitiveArraysFailures() throws Exception {
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("3", int[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("true", boolean[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[[1]]", int[].class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[300]", byte[].class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[70000]", short[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[\"abc\"]", int[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("[\"abc\"]", boolean[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("5", char[].class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", byte[].class));
        ObjectReader noNull = MAPPER.readerFor(int[].class).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertThrows(MismatchedInputException.class, () -> noNull.readValue("[null]"));
    }

    /*
    /**********************************************************************
    /* Number deserializers
    /**********************************************************************
     */

    @Test
    public void testNumberScalars() throws Exception {
        assertEquals(Short.valueOf((short) 5), MAPPER.readValue("\"5\"", Short.class));
        assertEquals(Byte.valueOf((byte) 5), MAPPER.readValue("\"5\"", Byte.class));
        assertEquals(Long.valueOf(5), MAPPER.readValue("5.0", Long.class));
        assertEquals(Integer.valueOf(5), MAPPER.readValue("\"5\"", Integer.class));
        assertEquals(Float.valueOf(1.5f), MAPPER.readValue("\"1.5\"", Float.class));
        assertEquals(Double.valueOf(1.5), MAPPER.readValue("\"1.5\"", Double.class));
        assertEquals(Double.valueOf(Double.NaN), MAPPER.readValue("\"NaN\"", Double.class));
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), MAPPER.readValue("\"Infinity\"", Double.class));
        assertEquals(Double.valueOf(Double.NEGATIVE_INFINITY), MAPPER.readValue("\"-Infinity\"", double.class));
        assertEquals(Float.valueOf(Float.NaN), MAPPER.readValue("\"NaN\"", Float.class));
        assertEquals(Float.valueOf(Float.POSITIVE_INFINITY), MAPPER.readValue("\"Infinity\"", float.class));
        assertEquals(Float.valueOf(Float.NEGATIVE_INFINITY), MAPPER.readValue("\"-Infinity\"", Float.class));
        assertEquals(Boolean.TRUE, MAPPER.readValue("\"true\"", Boolean.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("0", Boolean.class));
        assertEquals(Boolean.TRUE, MAPPER.readValue("2", boolean.class));
        assertEquals(Character.valueOf('x'), MAPPER.readValue("\"x\"", Character.class));
        assertEquals(Character.valueOf('x'), MAPPER.readValue("120", Character.class));
        assertEquals(Integer.valueOf(0), MAPPER.readValue("null", int.class));
        assertNull(MAPPER.readValue("null", Integer.class));
        assertNull(MAPPER.readValue("\"\"", Integer.class));
        assertEquals(Integer.valueOf(0), MAPPER.readValue("\"\"", int.class));
        assertEquals(Long.valueOf(0), MAPPER.readValue("\"\"", long.class));
        assertEquals(Boolean.FALSE, MAPPER.readValue("\"\"", boolean.class));
        assertEquals(new BigInteger("12"), MAPPER.readValue("\"12\"", BigInteger.class));
        assertEquals(new BigInteger("12"), MAPPER.readValue("12.0", BigInteger.class));
        assertEquals(new BigDecimal("1.25"), MAPPER.readValue("\"1.25\"", BigDecimal.class));
        assertEquals(new BigDecimal("12"), MAPPER.readValue("12", BigDecimal.class));
        assertEquals(Integer.valueOf(4), MAPPER.readValue("4", Number.class));
        assertEquals(Double.valueOf(4.5), MAPPER.readValue("4.5", Number.class));
        assertEquals(new BigDecimal("4.5"), MAPPER.readerFor(Number.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue("\"4.5\""));
        assertEquals(Integer.valueOf(4), MAPPER.readValue("\"4\"", Number.class));
        assertEquals(Long.valueOf(4_000_000_000L), MAPPER.readValue("4000000000", Number.class));
    }

    @Test
    public void testNumberFailures() throws Exception {
        for (Class<?> t : new Class<?>[] { Integer.class, Long.class, Short.class, Byte.class, Double.class,
                Float.class, Boolean.class, BigInteger.class, BigDecimal.class, Number.class }) {
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"abc\"", t), t.getName());
            assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", t), t.getName());
        }
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("\"ab\"", Character.class));
        assertThrows(MismatchedInputException.class, () -> MAPPER.readValue("{}", Character.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> MAPPER.readValue("300", Byte.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> MAPPER.readValue("70000", Short.class));
        assertThrows(com.fasterxml.jackson.core.exc.InputCoercionException.class,
                () -> MAPPER.readValue("5000000000", Integer.class));
    }
}
