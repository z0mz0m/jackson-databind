package com.fasterxml.jackson.databind.deser.impl;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.deser.UnresolvedForwardReference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import static org.junit.jupiter.api.Assertions.*;

public class BeanAsArrayAndObjectIdDeserTest
{
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class ArrayPojo {
        public int a;
        public String b;
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LenientArrayPojo {
        public int a;
        public String b;
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "x", "y" })
    static class CreatorArrayPojo {
        final int x;
        final int y;
        @JsonCreator
        public CreatorArrayPojo(@JsonProperty("x") int x, @JsonProperty("y") int y) {
            this.x = x;
            this.y = y;
        }
    }

    @JsonDeserialize(builder = ArrayBuilder.class)
    static class BuiltValue {
        final int a;
        final String b;
        BuiltValue(int a, String b) { this.a = a; this.b = b; }
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class ArrayBuilder {
        int a;
        String b;
        public ArrayBuilder a(int v) { a = v; return this; }
        public ArrayBuilder b(String v) { b = v; return this; }
        public BuiltValue build() { return new BuiltValue(a, b); }
    }

    @JsonDeserialize(builder = CreatorBuilder.class)
    static class CreatorBuiltValue {
        final int a;
        final String b;
        CreatorBuiltValue(int a, String b) { this.a = a; this.b = b; }
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    static class CreatorBuilder {
        final int a;
        final String b;
        @JsonCreator
        public CreatorBuilder(@JsonProperty("a") int a, @JsonProperty("b") String b) {
            this.a = a;
            this.b = b;
        }
        public CreatorBuiltValue build() { return new CreatorBuiltValue(a, b); }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    static class Node {
        public int id;
        public Node next;
    }

    static class NodeHolder {
        public Node first;
        public Node second;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    static class Owner {
        public int id;
        public List<Owner> children;
    }

    static class Merged {
        public Map<String, Integer> map = new LinkedHashMap<>();
        public List<String> list = new ArrayList<>();
    }

    private final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testBeanAsArrayBasics() throws Exception {
        ArrayPojo p = MAPPER.readValue("[7,\"x\"]", ArrayPojo.class);
        assertEquals(7, p.a);
        assertEquals("x", p.b);
        p = MAPPER.readValue("[8]", ArrayPojo.class);
        assertEquals(8, p.a);
        assertNull(p.b);
        assertNull(MAPPER.readValue("null", ArrayPojo.class));
    }

    @Test
    public void testBeanAsArrayTooManyElements() throws Exception {
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[1,\"x\",3]", ArrayPojo.class));
        LenientArrayPojo p = MAPPER.readValue("[1,\"x\",3,[4,5]]", LenientArrayPojo.class);
        assertEquals(1, p.a);
        assertEquals("x", p.b);
    }

    @Test
    public void testBeanAsArrayNested() throws Exception {
        ArrayPojo[] arr = MAPPER.readValue("[[1,\"a\"],[2,\"b\"]]", ArrayPojo[].class);
        assertEquals(2, arr.length);
        assertEquals("b", arr[1].b);
    }

    @Test
    public void testBeanAsArrayWithCreator() throws Exception {
        CreatorArrayPojo p = MAPPER.readValue("[3,4]", CreatorArrayPojo.class);
        assertEquals(3, p.x);
        assertEquals(4, p.y);
        p = MAPPER.readValue("[3]", CreatorArrayPojo.class);
        assertEquals(3, p.x);
        assertEquals(0, p.y);
    }

    @Test
    public void testBeanAsArrayBuilder() throws Exception {
        BuiltValue v = MAPPER.readValue("[5,\"five\"]", BuiltValue.class);
        assertEquals(5, v.a);
        assertEquals("five", v.b);
        v = MAPPER.readValue("[6]", BuiltValue.class);
        assertEquals(6, v.a);
        assertNull(v.b);
        assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue("[1,\"x\",3]", BuiltValue.class));
    }

    @Test
    public void testBeanAsArrayBuilderWithCreator() throws Exception {
        CreatorBuiltValue v = MAPPER.readValue("[9,\"nine\"]", CreatorBuiltValue.class);
        assertEquals(9, v.a);
        assertEquals("nine", v.b);
        v = MAPPER.readValue("[9]", CreatorBuiltValue.class);
        assertEquals(9, v.a);
        assertNull(v.b);
    }

    @Test
    public void testForwardObjectIdReference() throws Exception {
        NodeHolder h = MAPPER.readValue(
                "{\"second\":2,\"first\":{\"id\":1,\"next\":{\"id\":2}}}", NodeHolder.class);
        assertNotNull(h.second);
        assertSame(h.first.next, h.second);
    }

    @Test
    public void testForwardObjectIdReferenceInCollection() throws Exception {
        Owner o = MAPPER.readValue(
                "{\"id\":1,\"children\":[2,{\"id\":2,\"children\":[1]}]}", Owner.class);
        assertEquals(2, o.children.size());
        assertSame(o.children.get(0), o.children.get(1));
        assertSame(o, o.children.get(0).children.get(0));
    }

    @Test
    public void testUnresolvedObjectId() throws Exception {
        assertThrows(UnresolvedForwardReference.class,
                () -> MAPPER.readValue("{\"second\":99}", NodeHolder.class));
    }

    @Test
    public void testMergeIntoExisting() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configOverride(Map.class).setMergeable(true);
        mapper.configOverride(List.class).setMergeable(true);
        Merged target = new Merged();
        target.map.put("a", 1);
        target.list.add("x");
        Merged result = mapper.readerForUpdating(target)
                .readValue("{\"map\":{\"b\":2},\"list\":[\"y\"]}");
        assertSame(target, result);
        assertEquals(2, result.map.size());
        assertEquals(Arrays.asList("x", "y"), result.list);
    }
}
