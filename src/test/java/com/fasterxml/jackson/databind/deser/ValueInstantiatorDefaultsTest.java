package com.fasterxml.jackson.databind.deser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import static org.junit.jupiter.api.Assertions.*;

import static com.fasterxml.jackson.databind.testutil.DatabindTestUtil.*;

public class ValueInstantiatorDefaultsTest
{
    static class Pojo { }

    static class Plain extends ValueInstantiator.Base {
        public Plain() { super(Pojo.class); }
    }

    static class Rich extends ValueInstantiator.Base {
        public Rich() { super(Pojo.class); }

        @Override public boolean canInstantiate() { return true; }
        @Override public boolean canCreateFromString() { return true; }
        @Override public boolean canCreateFromInt() { return true; }
        @Override public boolean canCreateFromLong() { return true; }
        @Override public boolean canCreateFromDouble() { return true; }
        @Override public boolean canCreateFromBoolean() { return true; }
        @Override public boolean canCreateUsingDefault() { return true; }
        @Override public boolean canCreateUsingDelegate() { return true; }
        @Override public boolean canCreateUsingArrayDelegate() { return true; }
        @Override public boolean canCreateFromObjectWith() { return true; }

        @Override public Object createUsingDefault(DeserializationContext ctxt) { return "default"; }
        @Override public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) { return "args"; }
        @Override public Object createUsingDelegate(DeserializationContext ctxt, Object d) { return "delegate"; }
        @Override public Object createUsingArrayDelegate(DeserializationContext ctxt, Object d) { return "arrayDelegate"; }
        @Override public Object createFromString(DeserializationContext ctxt, String v) { return "string:" + v; }
        @Override public Object createFromInt(DeserializationContext ctxt, int v) { return "int:" + v; }
        @Override public Object createFromLong(DeserializationContext ctxt, long v) { return "long:" + v; }
        @Override public Object createFromBigInteger(DeserializationContext ctxt, BigInteger v) { return "bigint:" + v; }
        @Override public Object createFromDouble(DeserializationContext ctxt, double v) { return "double:" + v; }
        @Override public Object createFromBigDecimal(DeserializationContext ctxt, BigDecimal v) { return "bigdec:" + v; }
        @Override public Object createFromBoolean(DeserializationContext ctxt, boolean v) { return "bool:" + v; }
        @Override public Object createUsingDefaultOrWithoutArguments(DeserializationContext ctxt) { return "orWithout"; }
    }

    static class Wrapper extends ValueInstantiator.Delegating {
        public Wrapper(ValueInstantiator d) { super(d); }
    }

    static class BooleanOnly extends Plain {
        @Override public boolean canCreateFromBoolean() { return true; }
        @Override public Object createFromBoolean(DeserializationContext ctxt, boolean v) { return "bool:" + v; }

        @SuppressWarnings("deprecation")
        public Object fallback(DeserializationContext ctxt, String s) throws IOException {
            return _createFromStringFallbacks(ctxt, s);
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private DeserializationContext ctxt(ObjectMapper m) throws IOException {
        JsonParser p = m.createParser("{}");
        return ((DefaultDeserializationContext) m.getDeserializationContext())
                .createInstance(m.getDeserializationConfig(), p, m.getInjectableValues());
    }

    @Test
    public void testBaseDefaults() throws Exception
    {
        Plain vi = new Plain();
        assertEquals(Pojo.class, vi.getValueClass());
        assertEquals(Pojo.class.getName(), vi.getValueTypeDesc());
        assertFalse(vi.canInstantiate());
        assertFalse(vi.canCreateFromString());
        assertFalse(vi.canCreateFromInt());
        assertFalse(vi.canCreateFromLong());
        assertFalse(vi.canCreateFromBigInteger());
        assertFalse(vi.canCreateFromDouble());
        assertFalse(vi.canCreateFromBigDecimal());
        assertFalse(vi.canCreateFromBoolean());
        assertFalse(vi.canCreateUsingDefault());
        assertFalse(vi.canCreateUsingDelegate());
        assertFalse(vi.canCreateUsingArrayDelegate());
        assertFalse(vi.canCreateFromObjectWith());
        assertNull(vi.getDefaultCreator());
        assertNull(vi.getDelegateCreator());
        assertNull(vi.getArrayDelegateCreator());
        assertNull(vi.getWithArgsCreator());
        assertNull(vi.getDelegateType(MAPPER.getDeserializationConfig()));
        assertNull(vi.getArrayDelegateType(MAPPER.getDeserializationConfig()));
        assertNull(vi.getFromObjectArguments(MAPPER.getDeserializationConfig()));
        ValueInstantiator bare = new ValueInstantiator() { };
        assertEquals(Object.class, bare.getValueClass());
        assertEquals(Object.class.getName(), bare.getValueTypeDesc());
    }

    @Test
    public void testMissingCreatorsFail() throws Exception
    {
        Plain vi = new Plain();
        DeserializationContext ctxt = ctxt(MAPPER);
        String[] expected = {
                "no default no-arguments constructor",
                "no creator with arguments",
                "neither default",
                "no delegate creator",
                "no array delegate creator",
                "from String value",
                "from Number value (1)",
                "from Number value (2)",
                "BigInteger-argument",
                "double/Double-argument",
                "BigDecimal/double/Double-argument",
                "from boolean value (true)",
        };
        for (int i = 0; i < expected.length; ++i) {
            try {
                switch (i) {
                case 0: vi.createUsingDefault(ctxt); break;
                case 1: vi.createFromObjectWith(ctxt, new Object[0]); break;
                case 2: vi.createUsingDefaultOrWithoutArguments(ctxt); break;
                case 3: vi.createUsingDelegate(ctxt, "x"); break;
                case 4: vi.createUsingArrayDelegate(ctxt, "x"); break;
                case 5: vi.createFromString(ctxt, "abc"); break;
                case 6: vi.createFromInt(ctxt, 1); break;
                case 7: vi.createFromLong(ctxt, 2L); break;
                case 8: vi.createFromBigInteger(ctxt, BigInteger.TEN); break;
                case 9: vi.createFromDouble(ctxt, 1.5); break;
                case 10: vi.createFromBigDecimal(ctxt, BigDecimal.ONE); break;
                default: vi.createFromBoolean(ctxt, true); break;
                }
                fail("Should fail for case " + i);
            } catch (JsonMappingException e) {
                verifyException(e, expected[i]);
            }
        }
    }

    @Test
    public void testStringFallbacks() throws Exception
    {
        BooleanOnly vi = new BooleanOnly();
        DeserializationContext ctxt = ctxt(MAPPER);
        assertEquals("bool:true", vi.fallback(ctxt, " true "));
        assertEquals("bool:false", vi.fallback(ctxt, "false"));
        try {
            vi.fallback(ctxt, "maybe");
            fail("Should not pass");
        } catch (MismatchedInputException e) {
            verifyException(e, "from String value ('maybe')");
        }

        ObjectMapper lenient = jsonMapperBuilder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT).build();
        assertNull(vi.fallback(ctxt(lenient), ""));
    }

    @Test
    public void testDelegatingForwardsEverything() throws Exception
    {
        Rich rich = new Rich();
        Wrapper w = new Wrapper(rich);
        DeserializationContext ctxt = ctxt(MAPPER);

        assertEquals(Pojo.class, w.getValueClass());
        assertEquals(Pojo.class.getName(), w.getValueTypeDesc());
        assertTrue(w.canInstantiate());
        assertTrue(w.canCreateFromString());
        assertTrue(w.canCreateFromInt());
        assertTrue(w.canCreateFromLong());
        assertTrue(w.canCreateFromDouble());
        assertTrue(w.canCreateFromBoolean());
        assertTrue(w.canCreateUsingDefault());
        assertTrue(w.canCreateUsingDelegate());
        assertTrue(w.canCreateUsingArrayDelegate());
        assertTrue(w.canCreateFromObjectWith());
        assertNull(w.getFromObjectArguments(MAPPER.getDeserializationConfig()));
        assertNull(w.getDelegateType(MAPPER.getDeserializationConfig()));
        assertNull(w.getArrayDelegateType(MAPPER.getDeserializationConfig()));

        assertEquals("default", w.createUsingDefault(ctxt));
        assertEquals("args", w.createFromObjectWith(ctxt, new Object[0]));
        assertEquals("delegate", w.createUsingDelegate(ctxt, "x"));
        assertEquals("arrayDelegate", w.createUsingArrayDelegate(ctxt, "x"));
        assertEquals("string:s", w.createFromString(ctxt, "s"));
        assertEquals("int:3", w.createFromInt(ctxt, 3));
        assertEquals("long:4", w.createFromLong(ctxt, 4L));
        assertEquals("bigint:5", w.createFromBigInteger(ctxt, BigInteger.valueOf(5)));
        assertEquals("double:1.5", w.createFromDouble(ctxt, 1.5));
        assertEquals("bigdec:6", w.createFromBigDecimal(ctxt, BigDecimal.valueOf(6)));
        assertEquals("bool:true", w.createFromBoolean(ctxt, true));
        // not forwarded by Delegating: falls back to the base failure
        try {
            w.createUsingDefaultOrWithoutArguments(ctxt);
            fail("Should not pass");
        } catch (MismatchedInputException e) {
            verifyException(e, "neither default");
        }

        assertNull(w.getDefaultCreator());
        assertNull(w.getDelegateCreator());
        assertNull(w.getArrayDelegateCreator());
        assertNull(w.getWithArgsCreator());
    }
}
