package com.fasterxml.jackson.databind.deser.impl;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.ObjectIdGenerator.IdKey;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;
import com.fasterxml.jackson.databind.util.NameTransformer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for helper classes of the bean deserializer: {@link BeanPropertyMap},
 * {@link ObjectIdReferenceProperty}, {@link MergingSettableBeanProperty}
 * and {@link CreatorCandidate}.
 */
public class BeanPropertyHelpersTest extends DatabindTestUtil
{
    static class Inner {
        public int p = 1, q = 2;
    }

    static class Holder {
        public Inner inner;
        public int count;
        @JsonAlias("nick")
        public String name;
        public int bad;

        public void setBad(int v) { throw new IllegalStateException("bad value"); }
    }

    static class WithCreator {
        @JsonCreator
        public WithCreator(@JsonProperty("a") int a, @JacksonInject("k") String b, int c) { }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    private DeserializationContext _ctxt(JsonParser p) {
        return ((DefaultDeserializationContext) MAPPER.getDeserializationContext())
                .createInstance(MAPPER.getDeserializationConfig(), p, null);
    }

    private BeanDeserializer _holderDeserializer() throws Exception {
        return (BeanDeserializer) _ctxt(null)
                .findRootValueDeserializer(MAPPER.constructType(Holder.class));
    }

    private List<SettableBeanProperty> _holderProps() throws Exception {
        List<SettableBeanProperty> props = new ArrayList<>();
        Iterator<SettableBeanProperty> it = _holderDeserializer().properties();
        while (it.hasNext()) {
            props.add(it.next());
        }
        return props;
    }

    // --- BeanPropertyMap

    @SuppressWarnings("deprecation")
    @Test
    public void testDeprecatedFactories() throws Exception {
        List<SettableBeanProperty> props = _holderProps();
        Map<String,List<PropertyName>> aliases = new HashMap<>();
        BeanPropertyMap map = BeanPropertyMap.construct(MAPPER.getDeserializationConfig(),
                props, aliases);
        assertEquals(props.size(), map.size());
        assertFalse(map.isCaseInsensitive());
        assertFalse(map.hasAliases());

        map = BeanPropertyMap.construct(props, true, aliases);
        assertTrue(map.isCaseInsensitive());
        assertNotNull(map.find("COUNT"));
    }

    @Test
    public void testFindLookups() throws Exception {
        List<SettableBeanProperty> props = _holderProps();
        Map<String,List<PropertyName>> aliases = new HashMap<>();
        aliases.put("name", Collections.singletonList(new PropertyName("nick")));
        BeanPropertyMap map = new BeanPropertyMap(false, props, aliases, Locale.ENGLISH);
        assertTrue(map.hasAliases());
        assertNotNull(map.find("count"));
        assertNull(map.find("unknown"));
        // alias lookup
        SettableBeanProperty viaAlias = map.find("nick");
        assertNotNull(viaAlias);
        assertEquals("name", viaAlias.getName());
        assertNull(map.find("nope"));
        assertThrows(IllegalArgumentException.class, () -> map.find((String) null));

        // by index
        SettableBeanProperty byIndex = map.find(map.find("count").getPropertyIndex());
        assertEquals("count", byIndex.getName());
        assertNull(map.find(99));

        assertEquals(props.size(), map.getPropertiesInInsertionOrder().length);
        String desc = map.toString();
        assertTrue(desc.startsWith("Properties=["), desc);
        assertTrue(desc.contains("count"), desc);
        assertTrue(desc.contains("aliases"), desc);
    }

    @Test
    public void testMutantFactories() throws Exception {
        List<SettableBeanProperty> props = _holderProps();
        BeanPropertyMap map = new BeanPropertyMap(false, props,
                new HashMap<String,List<PropertyName>>(), Locale.ENGLISH);

        assertSame(map, map.withoutProperties(null));
        assertSame(map, map.withoutProperties(Collections.<String>emptyList(), null));

        BeanPropertyMap without = map.withoutProperties(Collections.singleton("count"));
        assertNull(without.find("count"));
        assertEquals(map.size() - 1, without.size());

        BeanPropertyMap only = map.withoutProperties(Collections.<String>emptySet(),
                Collections.singleton("count"));
        assertEquals(1, only.size());
        assertNotNull(only.find("count"));

        // case insensitivity
        BeanPropertyMap ci = map.withCaseInsensitivity(true);
        assertNotSame(map, ci);
        assertNotNull(ci.find("COUNT"));
        assertSame(ci, ci.withCaseInsensitivity(true));
        assertNull(map.withCaseInsensitivity(false).find("COUNT"));

        // replace and remove
        SettableBeanProperty count = map.find("count");
        SettableBeanProperty renamed = count.withSimpleName("total");
        map.replace(count, renamed);
        assertSame(renamed, map.find("count"));
        SettableBeanProperty bad = map.find("bad");
        map.remove(bad);
        assertNull(map.find("bad"));
        assertEquals(props.size() - 1, map.size());

        // removing or replacing a non-existing property fails
        assertThrows(NoSuchElementException.class, () -> map.remove(bad));
        assertThrows(NoSuchElementException.class, () -> map.replace(bad, renamed));

        // rename all
        BeanPropertyMap prefixed = new BeanPropertyMap(false, props,
                new HashMap<String,List<PropertyName>>(), Locale.ENGLISH)
                .renameAll(NameTransformer.simpleTransformer("pre_", null));
        assertNotNull(prefixed.find("pre_count"));
        assertNull(prefixed.find("count"));
        // no-op transformer
        BeanPropertyMap same = map.renameAll(NameTransformer.NOP);
        assertSame(map, same);

        // adding property
        SettableBeanProperty extra = props.get(0).withSimpleName("extra");
        BeanPropertyMap bigger = map.withProperty(extra);
        assertNotNull(bigger.find("extra"));
        // same name replaces
        assertSame(extra, bigger.withProperty(extra).find("extra"));
    }

    @Test
    public void testFindDeserializeAndSet() throws Exception {
        BeanPropertyMap map = new BeanPropertyMap(false, _holderProps(),
                new HashMap<String,List<PropertyName>>(), Locale.ENGLISH);
        Holder h = new Holder();
        try (JsonParser p = MAPPER.createParser("{\"count\":7}")) {
            p.nextToken();
            p.nextToken(); // FIELD_NAME
            p.nextToken(); // value
            assertTrue(map.findDeserializeAndSet(p, _ctxt(p), h, "count"));
        }
        assertEquals(7, h.count);
        try (JsonParser p = MAPPER.createParser("5")) {
            p.nextToken();
            assertFalse(map.findDeserializeAndSet(p, _ctxt(p), h, "missing"));
        }
    }

    @Test
    public void testFindDeserializeAndSetWraps() throws Exception {
        BeanPropertyMap map = new BeanPropertyMap(false, _holderProps(),
                new HashMap<String,List<PropertyName>>(), Locale.ENGLISH);
        Holder h = new Holder();
        try (JsonParser p = MAPPER.createParser("3")) {
            p.nextToken();
            DeserializationContext ctxt = _ctxt(p);
            JsonMappingException e = assertThrows(JsonMappingException.class,
                    () -> map.findDeserializeAndSet(p, ctxt, h, "bad"));
            verifyException(e, "bad value");
            assertEquals("bad", e.getPath().get(0).getFieldName());
        }
    }

    // --- ObjectIdReferenceProperty

    @Test
    public void testObjectIdReferencePropertyDelegation() throws Exception {
        SettableBeanProperty forward = _holderDeserializer().findProperty("count");
        ObjectIdReferenceProperty prop = new ObjectIdReferenceProperty(forward, null);

        assertSame(forward.getMember(), prop.getMember());
        assertNull(prop.getAnnotation(JsonIgnore.class));
        prop.fixAccess(MAPPER.getDeserializationConfig());

        SettableBeanProperty renamed = prop.withName(new PropertyName("other"));
        assertTrue(renamed instanceof ObjectIdReferenceProperty);
        assertEquals("other", renamed.getName());

        assertSame(prop, prop.withValueDeserializer(prop.getValueDeserializer()));
        JsonDeserializer<?> other = MAPPER.getDeserializationContext() == null ? null
                : _ctxt(null).findRootValueDeserializer(MAPPER.constructType(String.class));
        SettableBeanProperty withDeser = prop.withValueDeserializer(other);
        assertNotSame(prop, withDeser);
        assertSame(other, withDeser.getValueDeserializer());
        assertTrue(prop.withNullProvider(null) instanceof ObjectIdReferenceProperty);

        Holder h = new Holder();
        prop.set(h, Integer.valueOf(4));
        assertEquals(4, h.count);
        assertSame(h, prop.setAndReturn(h, Integer.valueOf(5)));
        assertEquals(5, h.count);

        try (JsonParser p = MAPPER.createParser("9")) {
            p.nextToken();
            prop.deserializeAndSet(p, _ctxt(p), h);
        }
        assertEquals(9, h.count);
    }

    @Test
    public void testPropertyReferringRejectsUnknownId() throws Exception {
        SettableBeanProperty forward = _holderDeserializer().findProperty("count");
        ObjectIdReferenceProperty prop = new ObjectIdReferenceProperty(forward, null);
        ReadableObjectId roid = new ReadableObjectId(new IdKey(Object.class, Object.class, "1"));
        Holder h = new Holder();
        try (JsonParser p = MAPPER.createParser("1")) {
            UnresolvedForwardReference ref = new UnresolvedForwardReference(p, "unresolved",
                    p.currentLocation(), roid);
            ObjectIdReferenceProperty.PropertyReferring referring =
                    new ObjectIdReferenceProperty.PropertyReferring(prop, ref, Integer.class, h);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> referring.handleResolvedForwardReference("2", 3));
            verifyException(e, "wasn't previously seen");
            referring.handleResolvedForwardReference("1", 3);
            assertEquals(3, h.count);
        }
    }

    // --- MergingSettableBeanProperty

    @Test
    public void testMergingPropertySetters() throws Exception {
        SettableBeanProperty delegate = _holderDeserializer().findProperty("inner");
        MergingSettableBeanProperty prop = MergingSettableBeanProperty.construct(delegate,
                delegate.getMember());
        Holder h = new Holder();
        Inner in = new Inner();

        prop.set(h, null); // ignored
        assertNull(h.inner);
        prop.set(h, in);
        assertSame(in, h.inner);

        h.inner = null;
        assertSame(h, prop.setAndReturn(h, null));
        assertNull(h.inner);
        assertSame(h, prop.setAndReturn(h, in));
        assertSame(in, h.inner);

        // copy-constructor variant
        MergingSettableBeanProperty copy = new MergingSettableBeanProperty(prop, delegate);
        Holder h2 = new Holder();
        copy.set(h2, in);
        assertSame(in, h2.inner);
    }

    @Test
    public void testMergingPropertyDeserialization() throws Exception {
        SettableBeanProperty delegate = _holderDeserializer().findProperty("inner");
        MergingSettableBeanProperty prop = MergingSettableBeanProperty.construct(delegate,
                delegate.getMember());

        // no old value: plain deserialization
        Holder h = new Holder();
        try (JsonParser p = MAPPER.createParser("{\"p\":10}")) {
            p.nextToken();
            prop.deserializeAndSet(p, _ctxt(p), h);
        }
        assertEquals(10, h.inner.p);
        assertEquals(2, h.inner.q);

        // old value: merged in place
        Inner old = h.inner;
        try (JsonParser p = MAPPER.createParser("{\"q\":20}")) {
            p.nextToken();
            prop.deserializeAndSet(p, _ctxt(p), h);
        }
        assertSame(old, h.inner);
        assertEquals(10, old.p);
        assertEquals(20, old.q);

        // set-and-return flavour, with and without old value
        Holder h2 = new Holder();
        try (JsonParser p = MAPPER.createParser("{\"p\":3}")) {
            p.nextToken();
            assertSame(h2, prop.deserializeSetAndReturn(p, _ctxt(p), h2));
        }
        assertEquals(3, h2.inner.p);
        Inner old2 = h2.inner;
        try (JsonParser p = MAPPER.createParser("{\"q\":4}")) {
            p.nextToken();
            assertSame(h2, prop.deserializeSetAndReturn(p, _ctxt(p), h2));
        }
        assertSame(old2, h2.inner);
        assertEquals(4, old2.q);

        // explicit null cannot contribute to merging
        try (JsonParser p = MAPPER.createParser("null")) {
            p.nextToken();
            assertSame(h2, prop.deserializeSetAndReturn(p, _ctxt(p), h2));
        }
        assertSame(old2, h2.inner);
    }

    // --- CreatorCandidate

    @SuppressWarnings("deprecation")
    @Test
    public void testCreatorCandidate() throws Exception {
        BeanDescription desc = MAPPER.getDeserializationConfig()
                .introspect(MAPPER.constructType(WithCreator.class));
        AnnotatedConstructor ctor = desc.getConstructors().get(0);
        AnnotationIntrospector intr = MAPPER.getDeserializationConfig().getAnnotationIntrospector();

        // without property definitions
        CreatorCandidate bare = CreatorCandidate.construct(intr, ctor, null);
        assertEquals(3, bare.paramCount());
        assertSame(ctor, bare.creator());
        assertNull(bare.paramName(0));
        assertNull(bare.explicitParamName(0));
        assertNull(bare.propertyDef(0));
        assertNull(bare.injection(0));
        assertNotNull(bare.injection(1));
        assertEquals("k", bare.injection(1).getId());
        assertEquals(2, bare.parameter(2).getIndex());
        assertEquals(ctor.toString(), bare.toString());
        // injected parameter does not count; there are two others
        assertEquals(-1, bare.findOnlyParamWithoutInjectionX());
        // no implicit-name introspector registered
        assertNull(bare.findImplicitParamName(0));
        CreatorCandidate.Param noDef = new CreatorCandidate.Param(ctor.getParameter(0), null, null);
        assertNull(noDef.fullName());
        assertFalse(noDef.hasFullName());

        // with property definitions
        List<BeanPropertyDefinition> defs = desc.findProperties();
        BeanPropertyDefinition[] byParam = new BeanPropertyDefinition[3];
        for (BeanPropertyDefinition def : defs) {
            AnnotatedParameter ap = def.getConstructorParameter();
            if (ap != null) {
                byParam[ap.getIndex()] = def;
            }
        }
        CreatorCandidate full = CreatorCandidate.construct(intr, ctor, byParam);
        assertEquals(PropertyName.construct("a"), full.paramName(0));
        assertEquals(PropertyName.construct("a"), full.explicitParamName(0));
        assertNotNull(full.propertyDef(0));
        CreatorCandidate.Param withDef = new CreatorCandidate.Param(ctor.getParameter(0),
                byParam[0], null);
        assertEquals(PropertyName.construct("a"), withDef.fullName());
        assertTrue(withDef.hasFullName());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testCreatorCandidateOnlyParamWithoutInjection() throws Exception {
        BeanDescription desc = MAPPER.getDeserializationConfig()
                .introspect(MAPPER.constructType(SingleFree.class));
        AnnotatedConstructor ctor = desc.getConstructors().get(0);
        CreatorCandidate cand = CreatorCandidate.construct(
                MAPPER.getDeserializationConfig().getAnnotationIntrospector(), ctor, null);
        assertEquals(0, cand.findOnlyParamWithoutInjectionX());
    }

    static class SingleFree {
        @JsonCreator
        public SingleFree(int a, @JacksonInject("k") String b) { }
    }
}
