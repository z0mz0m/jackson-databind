package com.fasterxml.jackson.databind.deser.std;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.databind.util.StdConverter;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class StdDelegatingDeserializerCoverageTest extends DatabindTestUtil
{
    static class Box {
        public int v;
        Box(int v) { this.v = v; }
    }

    static class ToBox extends StdConverter<Integer, Box> {
        @Override
        public Box convert(Integer value) { return new Box(value + 1); }
    }

    static class Sub<T> extends StdDelegatingDeserializer<T> {
        private static final long serialVersionUID = 1L;

        Sub(Converter<Object, T> c) { super(c); }

        Sub(Converter<Object, T> c, JavaType t, JsonDeserializer<?> d) { super(c, t, d); }

        @Override
        protected StdDelegatingDeserializer<T> withDelegate(Converter<Object, T> converter,
                JavaType delegateType, JsonDeserializer<?> delegateDeserializer) {
            return new Sub<T>(converter, delegateType, delegateDeserializer);
        }

        @Override
        public JsonDeserializer<T> unwrappingDeserializer(NameTransformer u) {
            return this;
        }

        @Override
        public JsonDeserializer<T> replaceDelegatee(JsonDeserializer<?> delegatee) {
            return new Sub<T>(_converter, _delegateType, delegatee);
        }
    }

    // simple delegate that "updates" by returning the given value
    static class UpdatingStub extends JsonDeserializer<Object> {
        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) {
            return "converted";
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue) {
            return intoValue;
        }
    }

    @Test
    public void testContextualizeAndDeserialize() throws Exception
    {
        SimpleModule m = new SimpleModule();
        m.addDeserializer(Box.class, new Sub<Box>((Converter) new ToBox()));
        ObjectMapper mapper = jsonMapperBuilder().addModule(m).build();
        // delegate type is found from converter input type
        assertEquals(8, mapper.readValue("7", Box.class).v);
        assertNull(mapper.readValue("null", Box.class));
    }

    @Test
    public void testAccessorsViaDelegate() throws Exception
    {
        JavaType intType = TypeFactory.defaultInstance().constructType(Integer.class);
        JsonDeserializer<Object> del = (JsonDeserializer<Object>)
                NumberDeserializers.find(Integer.class, "java.lang.Integer");
        Sub<Box> d = new Sub<Box>((Converter) new ToBox(), intType, del);

        assertSame(del, d.getDelegatee());
        assertEquals(Integer.class, d.handledType());
        assertEquals(del.logicalType(), d.logicalType());
        assertTrue(d.isCachable());
        assertNull(d.getKnownPropertyNames());
        assertEquals(del.getNullAccessPattern(), d.getNullAccessPattern());
        assertEquals(del.getEmptyAccessPattern(), d.getEmptyAccessPattern());
        assertEquals(del.supportsUpdate(null), d.supportsUpdate(null));
        assertNull(d.getNullValue(null));
        assertNull(d.getAbsentValue(null));
        assertNotNull(d.getEmptyValue(null));
        assertNotNull(d.replaceDelegatee(del));
        assertSame(d, d.unwrappingDeserializer(null));
        // delegate is not resolvable: no-op
        d.resolve(null);
        assertFalse(new Sub<Box>((Converter) new ToBox()).isCachable());
    }

    @Test
    public void testUpdateAndWithType() throws Exception
    {
        JavaType listType = TypeFactory.defaultInstance().constructType(ArrayList.class);
        Sub d = new Sub((Converter) new ToBox(), listType, new UpdatingStub());

        JsonParser p = createParser(newJsonMapper(), "[]");
        p.nextToken();
        ArrayList<Object> into = new ArrayList<>();
        assertSame(into, d.deserialize(p, null, into));
        assertSame(into, d.deserializeWithType(p, null, null, into));

        try {
            d.deserialize(p, null, "text");
            fail("Should not pass");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Cannot update object of type java.lang.String");
        }
        try {
            d.deserializeWithType(p, null, null, "text");
            fail("Should not pass");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Cannot update object of type java.lang.String");
        }

        // non-null delegate value is converted
        Sub<Box> d2 = new Sub<Box>((Converter) new StdConverter<String, Box>() {
            @Override
            public Box convert(String s) { return new Box(s.length()); }
        }, listType, new UpdatingStub());
        assertEquals(9, ((Box) d2.deserialize(p, null)).v);
        assertEquals(9, ((Box) d2.deserializeWithType(p, null, null)).v);
    }

    @Test
    public void testBaseClassMustBeSubclassed() throws Exception
    {
        JavaType intType = TypeFactory.defaultInstance().constructType(Integer.class);
        JsonDeserializer<Object> del = (JsonDeserializer<Object>)
                NumberDeserializers.find(Integer.class, "java.lang.Integer");
        // exact base class: replacing delegate creates a new base instance
        StdDelegatingDeserializer<Box> base = new StdDelegatingDeserializer<Box>(
                (Converter) new ToBox(), intType, del);
        assertSame(base, base.replaceDelegatee(del));
        assertNotSame(base, base.replaceDelegatee(new UpdatingStub()));
        assertNotNull(base.unwrappingDeserializer(NameTransformer.NOP));

        // sub-class that does not override the factory methods fails
        StdDelegatingDeserializer<Box> sub = new StdDelegatingDeserializer<Box>(
                (Converter) new ToBox(), intType, del) { };
        try {
            sub.unwrappingDeserializer(null);
            fail("Should not pass");
        } catch (IllegalStateException e) {
            verifyException(e, "unwrappingDeserializer");
        }
        try {
            sub.replaceDelegatee(null);
            fail("Should not pass");
        } catch (IllegalStateException e) {
            verifyException(e, "replaceDelegatee");
        }
    }

    private JsonParser createParser(ObjectMapper m, String json) throws Exception {
        return m.createParser(json);
    }
}
