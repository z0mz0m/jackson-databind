package com.fasterxml.jackson.databind.deser.std;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class CharArrayAndNullCoverageTest
{
    private final ObjectMapper MAPPER = new ObjectMapper();

    private char[] fromEmbedded(Object value) throws Exception {
        TokenBuffer buf = new TokenBuffer(MAPPER, false);
        buf.writeEmbeddedObject(value);
        try (JsonParser p = buf.asParser()) {
            p.nextToken();
            return MAPPER.readValue(p, char[].class);
        }
    }

    @Test
    public void testCharArrayFromString() throws Exception {
        assertArrayEquals(new char[] { 'a', 'b' }, MAPPER.readValue("\"ab\"", char[].class));
    }

    @Test
    public void testCharArrayFromArrayOfStrings() throws Exception {
        assertArrayEquals(new char[] { 'x', 'y', 'z' },
                MAPPER.readValue("[\"x\",\"y\",\"z\"]", char[].class));
    }

    @Test
    public void testCharArrayNullElementBecomesNul() throws Exception {
        assertArrayEquals(new char[] { 'a', '\0' }, MAPPER.readValue("[\"a\",null]", char[].class));
    }

    @Test
    public void testCharArrayNullElementFailsForPrimitives() throws Exception {
        ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertThrows(JsonMappingException.class, () -> m.readValue("[null]", char[].class));
    }

    @Test
    public void testCharArrayLongElementFails() throws Exception {
        JsonMappingException e = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("[\"ab\"]", char[].class));
        assertTrue(e.getMessage().contains("length 2"));
    }

    @Test
    public void testCharArrayNumberElementFails() throws Exception {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[12]", char[].class));
    }

    @Test
    public void testCharArrayFromScalarFails() throws Exception {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("12", char[].class));
        ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        assertThrows(JsonMappingException.class, () -> m.readValue("12", char[].class));
    }

    @Test
    public void testCharArrayFromEmbeddedObjects() throws Exception {
        char[] chars = { 'q', 'r' };
        assertSame(chars, fromEmbedded(chars));
        assertArrayEquals(new char[] { 'h', 'i' }, fromEmbedded("hi"));
        assertArrayEquals("AQID".toCharArray(), fromEmbedded(new byte[] { 1, 2, 3 }));
        assertNull(fromEmbedded(null));
        assertThrows(JsonMappingException.class, () -> fromEmbedded(Integer.valueOf(3)));
    }

    @Test
    public void testPrimitiveArraysSingleValueUnwrapped() throws Exception {
        ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        assertArrayEquals(new int[] { 5 }, m.readValue("5", int[].class));
        assertArrayEquals(new long[] { 5L }, m.readValue("5", long[].class));
        assertArrayEquals(new short[] { 5 }, m.readValue("5", short[].class));
        assertArrayEquals(new float[] { 1.5f }, m.readValue("1.5", float[].class), 0f);
        assertArrayEquals(new double[] { 1.5 }, m.readValue("1.5", double[].class), 0d);
        assertArrayEquals(new boolean[] { true }, m.readValue("true", boolean[].class));
    }

    @Test
    public void testPrimitiveArraysRejectScalarByDefault() throws Exception {
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("5", int[].class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("5", double[].class));
        assertThrows(JsonMappingException.class, () -> MAPPER.readValue("true", boolean[].class));
    }

    @Test
    public void testPrimitiveArraysNullElements() throws Exception {
        assertArrayEquals(new int[] { 0, 1 }, MAPPER.readValue("[null,1]", int[].class));
        assertArrayEquals(new long[] { 0, 1 }, MAPPER.readValue("[null,1]", long[].class));
        assertArrayEquals(new boolean[] { false, true }, MAPPER.readValue("[null,true]", boolean[].class));
        ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        assertThrows(JsonMappingException.class, () -> m.readValue("[null]", int[].class));
        assertThrows(JsonMappingException.class, () -> m.readValue("[null]", double[].class));
    }
}
