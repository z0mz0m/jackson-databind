package com.fasterxml.jackson.databind.deser.impl;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.testutil.DatabindTestUtil;
import com.fasterxml.jackson.databind.util.NameTransformer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for "POJO as array" deserializers ({@link BeanAsArrayDeserializer} and
 * {@link BeanAsArrayBuilderDeserializer}) and merging builder properties.
 */
public class BeanAsArrayDeserializersTest extends DatabindTestUtil
{
    static class Views {
        static class A { }
        static class B { }
    }

    // --- builder-based types

    @JsonDeserialize(builder=PointBuilder.class)
    static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "x", "y" })
    static class PointBuilder {
        public int x, y;

        public PointBuilder withX(int v) { x = v; return this; }
        public PointBuilder withY(int v) { y = v; return this; }
        public Point build() { return new Point(x, y); }
    }

    @JsonDeserialize(builder=ViewBuilder.class)
    static class ViewValue {
        final int a, b;
        ViewValue(int a, int b) { this.a = a; this.b = b; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class ViewBuilder {
        public int a, b;

        @JsonView(Views.A.class)
        public ViewBuilder withA(int v) { a = v; return this; }
        @JsonView(Views.B.class)
        public ViewBuilder withB(int v) { b = v; return this; }
        public ViewValue build() { return new ViewValue(a, b); }
    }

    @JsonDeserialize(builder=InjectBuilder.class)
    static class InjectValue {
        final int a; final String inj;
        InjectValue(int a, String inj) { this.a = a; this.inj = inj; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a" })
    static class InjectBuilder {
        public int a;
        @JacksonInject("key")
        public String inj;

        public InjectBuilder withA(int v) { a = v; return this; }
        public InjectValue build() { return new InjectValue(a, inj); }
    }

    @JsonDeserialize(builder=FailingBuilder.class)
    static class FailingValue { }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    static class FailingBuilder {
        public int a;
        public FailingBuilder withA(int v) { a = v; return this; }
        public FailingValue build() { throw new IllegalStateException("bad build"); }
    }

    @JsonDeserialize(builder=CreatorBuilder.class)
    static class CreatorValue {
        final int a, b, c;
        CreatorValue(int a, int b, int c) { this.a = a; this.b = b; this.c = c; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b", "c" })
    static class CreatorBuilder {
        int a, b, c;
        @JsonCreator
        public CreatorBuilder(@JsonProperty("a") int a, @JsonProperty("b") int b) {
            this.a = a; this.b = b;
        }
        public CreatorBuilder withC(int v) { c = v; return this; }
        public CreatorValue build() { return new CreatorValue(a, b, c); }
    }

    @JsonDeserialize(builder=MergeBuilder.class)
    static class MergeValue {
        final Inner inner; final int n;
        MergeValue(Inner i, int n) { inner = i; this.n = n; }
    }

    static class Inner {
        public int p = 1, q = 2;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "inner", "n" })
    static class MergeBuilder {
        Inner inner = new Inner();
        int n;

        @JsonMerge
        public MergeBuilder withInner(Inner i) { inner = i; return this; }
        public MergeBuilder withN(int v) { n = v; return this; }
        public MergeValue build() { return new MergeValue(inner, n); }
    }

    @JsonDeserialize(builder=NullMergeBuilder.class)
    static class NullMergeValue {
        final Inner inner;
        NullMergeValue(Inner i) { inner = i; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    static class NullMergeBuilder {
        Inner inner; // null at first: nothing to merge into

        @JsonMerge
        public NullMergeBuilder withInner(Inner i) { inner = i; return this; }
        public NullMergeValue build() { return new NullMergeValue(inner); }
    }

    // --- plain POJOs as arrays

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class Pair {
        public int a, b;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    @JsonIgnoreProperties(ignoreUnknown=true)
    static class TolerantPair {
        public int a, b;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class ViewPair {
        @JsonView(Views.A.class)
        public int a;
        @JsonView(Views.B.class)
        public int b;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    @JsonIgnoreProperties(ignoreUnknown=true)
    static class TolerantViewPair {
        @JsonView(Views.A.class)
        public int a;
        @JsonView(Views.B.class)
        public int b;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a" })
    static class InjectPair {
        public int a;
        @JacksonInject("key")
        public String inj;
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b", "c" })
    static class CreatorPojo {
        final int a, b;
        int c;
        @JsonCreator
        CreatorPojo(@JsonProperty("a") int a, @JsonProperty("b") int b) {
            this.a = a; this.b = b;
        }
        public void setC(int c) { this.c = c; }
    }

    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "id", "value" })
    static class FailingSetter {
        public int id;
        public void setValue(int v) { throw new IllegalArgumentException("nope"); }
        public int getValue() { return 0; }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    // --- builder tests

    @Test
    public void testBuilderVanilla() throws Exception {
        Point p = MAPPER.readValue("[1,2]", Point.class);
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }

    @Test
    public void testBuilderTooManyValuesFails() throws Exception {
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[1,2,3]", Point.class));
    }

    @Test
    public void testBuilderTooManyValuesIgnored() throws Exception {
        ObjectReader r = MAPPER.readerFor(Point.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Point p = r.readValue("[1,2,3,[4,5]]");
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }

    @Test
    public void testBuilderFromObjectFails() throws Exception {
        MismatchedInputException e = assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"x\":1}", Point.class));
        verifyException(e, "Cannot deserialize a POJO");
        verifyException(e, "non-Array representation");
    }

    @Test
    public void testBuilderBuildFailureWrapped() throws Exception {
        JsonMappingException e = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("[1]", FailingValue.class));
        verifyException(e, "bad build");
    }

    @Test
    public void testBuilderWithViews() throws Exception {
        ViewValue v = MAPPER.readerFor(ViewValue.class).withView(Views.A.class)
                .readValue("[3,4]");
        assertEquals(3, v.a);
        assertEquals(0, v.b);
        // view that matches neither: all skipped
        v = MAPPER.readerFor(ViewValue.class).withView(String.class)
                .readValue("[3,4]");
        assertEquals(0, v.a);
        assertEquals(0, v.b);
    }

    @Test
    public void testBuilderWithViewsTooManyValues() throws Exception {
        ObjectReader r = MAPPER.readerFor(ViewValue.class).withView(Views.A.class);
        assertThrows(MismatchedInputException.class, () -> r.readValue("[1,2,3]"));
        ViewValue v = r.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue("[1,2,3,{\"x\":1}]");
        assertEquals(1, v.a);
    }

    @Test
    public void testBuilderWithInjection() throws Exception {
        InjectValue v = MAPPER.readerFor(InjectValue.class)
                .with(new InjectableValues.Std().addValue("key", "injected"))
                .readValue("[7]");
        assertEquals(7, v.a);
        assertEquals("injected", v.inj);
    }

    @Test
    public void testBuilderWithCreator() throws Exception {
        CreatorValue v = MAPPER.readValue("[1,2,3]", CreatorValue.class);
        assertEquals(1, v.a);
        assertEquals(2, v.b);
        assertEquals(3, v.c);
        // fewer values than creator needs
        v = MAPPER.readValue("[1]", CreatorValue.class);
        assertEquals(1, v.a);
        assertEquals(0, v.b);
        // extra elements are skipped
        v = MAPPER.readValue("[1,2,3,4,5]", CreatorValue.class);
        assertEquals(3, v.c);
    }

    @Test
    public void testBuilderMerging() throws Exception {
        MergeValue v = MAPPER.readValue("[{\"p\":10},5]", MergeValue.class);
        assertEquals(10, v.inner.p);
        assertEquals(2, v.inner.q); // merged, not replaced
        assertEquals(5, v.n);
    }

    @Test
    public void testBuilderMergingWithoutOldValue() throws Exception {
        NullMergeValue v = MAPPER.readValue("[{\"p\":10}]", NullMergeValue.class);
        assertEquals(10, v.inner.p);
        assertEquals(2, v.inner.q);
    }

    @Test
    public void testBuilderDeserializerCopies() throws Exception {
        JsonDeserializer<Object> deser = ((DefaultDeserializationContext) MAPPER.getDeserializationContext())
                .createInstance(MAPPER.getDeserializationConfig(), null, null)
                .findRootValueDeserializer(MAPPER.constructType(Point.class));
        assertTrue(deser instanceof BeanAsArrayBuilderDeserializer);
        BeanAsArrayBuilderDeserializer bd = (BeanAsArrayBuilderDeserializer) deser;
        assertEquals(Boolean.FALSE, bd.supportsUpdate(MAPPER.getDeserializationConfig()));
        assertTrue(bd.withObjectIdReader(null) instanceof BeanAsArrayBuilderDeserializer);
        assertTrue(bd.withIgnoreAllUnknown(true) instanceof BeanAsArrayBuilderDeserializer);
        assertTrue(bd.withByNameInclusion(Collections.singleton("x"), null)
                instanceof BeanAsArrayBuilderDeserializer);
        assertNotNull(bd.unwrappingDeserializer(NameTransformer.NOP));
    }

    // --- plain POJO tests

    @Test
    public void testPojoVanilla() throws Exception {
        Pair p = MAPPER.readValue("[1,2]", Pair.class);
        assertEquals(1, p.a);
        assertEquals(2, p.b);
    }

    @Test
    public void testPojoTooManyValues() throws Exception {
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[1,2,3]", Pair.class));
        TolerantPair p = MAPPER.readValue("[1,2,3,[4]]", TolerantPair.class);
        assertEquals(2, p.b);
    }

    @Test
    public void testPojoFromObjectFails() throws Exception {
        MismatchedInputException e = assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("{\"a\":1}", Pair.class));
        verifyException(e, "non-Array representation");
    }

    @Test
    public void testPojoUpdate() throws Exception {
        Pair p = new Pair();
        p.a = 9;
        p = MAPPER.readerForUpdating(p).readValue("[5]");
        assertEquals(5, p.a);
        assertEquals(0, p.b);
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readerForUpdating(new Pair()).readValue("[1,2,3]"));
        Pair p2 = new Pair();
        MAPPER.readerForUpdating(p2)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue("[1,2,3,[4]]");
        assertEquals(2, p2.b);
    }

    @Test
    public void testPojoWithViews() throws Exception {
        ViewPair p = MAPPER.readerFor(ViewPair.class).withView(Views.B.class)
                .readValue("[3,4]");
        assertEquals(0, p.a);
        assertEquals(4, p.b);
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readerFor(ViewPair.class).withView(Views.B.class)
                    .readValue("[3,4,5]"));
        TolerantViewPair tp = MAPPER.readerFor(TolerantViewPair.class).withView(Views.B.class)
                .readValue("[3,4,5,[6]]");
        assertEquals(4, tp.b);
    }

    @Test
    public void testPojoWithInjection() throws Exception {
        InjectPair p = MAPPER.readerFor(InjectPair.class)
                .with(new InjectableValues.Std().addValue("key", "xyz"))
                .readValue("[3]");
        assertEquals(3, p.a);
        assertEquals("xyz", p.inj);
    }

    @Test
    public void testPojoWithCreator() throws Exception {
        CreatorPojo p = MAPPER.readValue("[1,2,3]", CreatorPojo.class);
        assertEquals(1, p.a);
        assertEquals(2, p.b);
        assertEquals(3, p.c);
        p = MAPPER.readValue("[1]", CreatorPojo.class);
        assertEquals(1, p.a);
        assertEquals(0, p.b);
        p = MAPPER.readValue("[1,2,3,4,[5]]", CreatorPojo.class);
        assertEquals(3, p.c);
    }

    @Test
    public void testPojoWithCreatorAndViews() throws Exception {
        CreatorPojo p = MAPPER.readerFor(CreatorPojo.class).withView(String.class)
                .readValue("[1,2,3]");
        // not annotated with a view, so visible by default
        assertEquals(3, p.c);
    }

    @Test
    public void testPojoSetterFailureWrapped() throws Exception {
        JsonMappingException e = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("[1,2]", FailingSetter.class));
        verifyException(e, "nope");
        assertEquals("value", e.getPath().get(0).getFieldName());
    }
}
