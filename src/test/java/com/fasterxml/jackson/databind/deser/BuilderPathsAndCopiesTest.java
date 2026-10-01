package com.fasterxml.jackson.databind.deser;

import java.util.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.util.NameTransformer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import static com.fasterxml.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Tests for less common builder-based and bean deserializer paths:
 * injection, views, unwrapping with creators, failing build methods
 * and copy-on-write factory methods.
 */
public class BuilderPathsAndCopiesTest
{
    // --- injection into builder
    @JsonDeserialize(builder = InjBuilder.class)
    static class InjValue {
        public int a;
        public String injected;
    }

    static class InjBuilder {
        int a;
        @JacksonInject("key")
        String injected;
        public InjBuilder withA(int v) { a = v; return this; }
        public InjValue build() {
            InjValue v = new InjValue();
            v.a = a; v.injected = injected;
            return v;
        }
    }

    // --- failing build method
    @JsonDeserialize(builder = FailBuilder.class)
    static class FailValue { }

    static class FailBuilder {
        public FailBuilder withA(int v) { return this; }
        public FailValue build() { throw new IllegalStateException("no build"); }
    }

    // --- views with a creator-based builder
    static class V1 { }
    static class V2 { }

    @JsonDeserialize(builder = ViewCreatorBuilder.class)
    static class ViewCreatorValue {
        public int id, hidden, shown;
    }

    static class ViewCreatorBuilder {
        int id, hidden, shown;
        @JsonCreator
        public ViewCreatorBuilder(@JsonProperty("id") int id) { this.id = id; }
        @JsonView(V1.class)
        public ViewCreatorBuilder withShown(int v) { shown = v; return this; }
        public ViewCreatorBuilder withHidden(int v) { hidden = v; return this; }
        public ViewCreatorValue build() {
            ViewCreatorValue v = new ViewCreatorValue();
            v.id = id; v.hidden = hidden; v.shown = shown;
            return v;
        }
    }

    // --- unwrapped with property-based creator, any setter and ignorals
    static class Pt {
        public int p, q;
    }

    @JsonDeserialize(builder = UnwrapCreatorBuilder.class)
    static class UnwrapCreatorValue {
        public int id;
        public Pt pt;
        public Map<String, Object> extra;
    }

    @JsonIgnoreProperties({"ign"})
    static class UnwrapCreatorBuilder {
        int id;
        Pt pt;
        Map<String, Object> extra = new LinkedHashMap<>();
        @JsonCreator
        public UnwrapCreatorBuilder(@JsonProperty("id") int id) { this.id = id; }
        @JsonUnwrapped
        public UnwrapCreatorBuilder withPt(Pt p) { pt = p; return this; }
        @JsonAnySetter
        public void any(String k, Object v) { extra.put(k, v); }
        public UnwrapCreatorValue build() {
            UnwrapCreatorValue v = new UnwrapCreatorValue();
            v.id = id; v.pt = pt; v.extra = extra;
            return v;
        }
    }

    // --- unwrapped with default builder, view, ignorals, any setter
    @JsonDeserialize(builder = UnwrapDefaultBuilder.class)
    static class UnwrapDefaultValue {
        public int id, vid;
        public Pt pt;
        public Map<String, Object> extra;
    }

    @JsonIgnoreProperties({"ign"})
    static class UnwrapDefaultBuilder {
        int id, vid;
        Pt pt;
        Map<String, Object> extra = new LinkedHashMap<>();
        @JsonUnwrapped
        public UnwrapDefaultBuilder withPt(Pt p) { pt = p; return this; }
        public UnwrapDefaultBuilder withId(int v) { id = v; return this; }
        @JsonView(V1.class)
        public UnwrapDefaultBuilder withVid(int v) { vid = v; return this; }
        @JsonAnySetter
        public void any(String k, Object v) { extra.put(k, v); }
        public UnwrapDefaultValue build() {
            UnwrapDefaultValue v = new UnwrapDefaultValue();
            v.id = id; v.vid = vid; v.pt = pt; v.extra = extra;
            return v;
        }
    }

    // --- plain beans
    static class Bean {
        public int a, b;
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private BeanDeserializerBase deserFor(Class<?> cls) throws Exception {
        DeserializationConfig cfg = MAPPER.getDeserializationConfig();
        DeserializationContext ctxt = ((DefaultDeserializationContext) MAPPER.getDeserializationContext())
                .createInstance(cfg, null, null);
        return (BeanDeserializerBase) ctxt.findRootValueDeserializer(MAPPER.constructType(cls));
    }

    @Test
    public void testBuilderInjection() throws Exception {
        InjValue v = MAPPER.readerFor(InjValue.class)
                .with(new InjectableValues.Std().addValue("key", "val"))
                .readValue("{\"a\":4}");
        assertEquals(4, v.a);
        assertEquals("val", v.injected);
    }

    @Test
    public void testBuildMethodFailure() throws Exception {
        assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"a\":1}", FailValue.class));
    }

    @Test
    public void testViewWithCreatorBuilder() throws Exception {
        ObjectReader r = MAPPER.readerFor(ViewCreatorValue.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withView(V1.class);
        ViewCreatorValue v = r.readValue("{\"hidden\":2,\"shown\":3,\"id\":1}");
        assertEquals(1, v.id);
        assertEquals(3, v.shown);
        assertEquals(2, v.hidden);
        ViewCreatorValue v2 = MAPPER.readValue("{\"shown\":3,\"hidden\":2,\"id\":1}", ViewCreatorValue.class);
        assertEquals(2, v2.hidden);
    }

    @Test
    public void testUnexpectedViewPropertyFails() throws Exception {
        ObjectReader r = MAPPER.readerFor(ViewCreatorValue.class)
                .with(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES)
                .withView(V2.class);
        assertThrows(JsonMappingException.class, () -> r.readValue("{\"id\":1,\"shown\":3}"));
    }

    @Test
    public void testUnwrappedWithCreatorBuilder() throws Exception {
        UnwrapCreatorValue v = MAPPER.readValue(
                "{\"p\":1,\"ign\":5,\"zz\":\"w\",\"id\":7,\"q\":2}", UnwrapCreatorValue.class);
        assertEquals(7, v.id);
        assertEquals(1, v.pt.p);
        assertEquals(2, v.pt.q);
        assertTrue(v.extra.containsKey("zz"));
        assertFalse(v.extra.containsKey("ign"));

        UnwrapCreatorValue v2 = MAPPER.readValue("{\"id\":3,\"p\":4}", UnwrapCreatorValue.class);
        assertEquals(3, v2.id);
        assertEquals(4, v2.pt.p);
    }

    @Test
    public void testUnwrappedDefaultBuilder() throws Exception {
        UnwrapDefaultValue v = MAPPER.readValue(
                "{\"id\":1,\"ign\":[1],\"zz\":\"w\",\"p\":9}", UnwrapDefaultValue.class);
        assertEquals(1, v.id);
        assertEquals(9, v.pt.p);
        assertTrue(v.extra.containsKey("zz"));
        assertFalse(v.extra.containsKey("ign"));

        ObjectReader r = MAPPER.readerFor(UnwrapDefaultValue.class).withView(V1.class);
        UnwrapDefaultValue v2 = r.readValue("{\"vid\":5,\"id\":8}");
        assertEquals(5, v2.vid);
        assertEquals(8, v2.id);
    }

    @Test
    public void testBeanDeserializerCopies() throws Exception {
        BeanDeserializerBase deser = deserFor(Bean.class);
        assertTrue(deser instanceof BeanDeserializer);
        assertNotSame(deser, deser.withIgnorableProperties(Collections.singleton("a")));
        assertNotSame(deser, deser.withIgnoreAllUnknown(true));
        assertNotNull(deser.withByNameInclusion(null, Collections.singleton("a")));
        assertNotNull(deser.unwrappingDeserializer(NameTransformer.NOP));
    }
}
