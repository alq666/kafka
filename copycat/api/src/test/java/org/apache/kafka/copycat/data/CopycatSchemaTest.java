/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package org.apache.kafka.copycat.data;

import org.apache.kafka.copycat.errors.DataException;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class CopycatSchemaTest {
    private static final Schema MAP_INT_STRING_SCHEMA = SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.STRING_SCHEMA).build();
    private static final Schema FLAT_STRUCT_SCHEMA = SchemaBuilder.struct()
            .field("field", Schema.INT32_SCHEMA)
            .build();
    private static final Schema STRUCT_SCHEMA = SchemaBuilder.struct()
            .field("first", Schema.INT32_SCHEMA)
            .field("second", Schema.STRING_SCHEMA)
            .field("array", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
            .field("map", SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.STRING_SCHEMA).build())
            .field("nested", FLAT_STRUCT_SCHEMA)
            .build();
    private static final Schema PARENT_STRUCT_SCHEMA = SchemaBuilder.struct()
            .field("nested", FLAT_STRUCT_SCHEMA)
            .build();

    @Test
    public void testFieldsOnStructSchema() {
        Schema schema = SchemaBuilder.struct()
                .field("foo", Schema.BOOLEAN_SCHEMA)
                .field("bar", Schema.INT32_SCHEMA)
                .build();

        assertEquals(2, schema.fields().size());
        // Validate field lookup by name
        Field foo = schema.field("foo");
        assertEquals(0, foo.index());
        Field bar = schema.field("bar");
        assertEquals(1, bar.index());
        // Any other field name should fail
        assertNull(schema.field("other"));
    }


    @Test(expected = DataException.class)
    public void testFieldsOnlyValidForStructs() {
        Schema.INT8_SCHEMA.fields();
    }

    @Test
    public void testValidateValueMatchingType() {
        CopycatSchema.validateValue(Schema.INT8_SCHEMA, (byte) 1);
        CopycatSchema.validateValue(Schema.INT16_SCHEMA, (short) 1);
        CopycatSchema.validateValue(Schema.INT32_SCHEMA, 1);
        CopycatSchema.validateValue(Schema.INT64_SCHEMA, (long) 1);
        CopycatSchema.validateValue(Schema.FLOAT32_SCHEMA, 1.f);
        CopycatSchema.validateValue(Schema.FLOAT64_SCHEMA, 1.);
        CopycatSchema.validateValue(Schema.BOOLEAN_SCHEMA, true);
        CopycatSchema.validateValue(Schema.STRING_SCHEMA, "a string");
        CopycatSchema.validateValue(Schema.BYTES_SCHEMA, "a byte array".getBytes());
        CopycatSchema.validateValue(Schema.BYTES_SCHEMA, ByteBuffer.wrap("a byte array".getBytes()));
        CopycatSchema.validateValue(SchemaBuilder.array(Schema.INT32_SCHEMA).build(), Arrays.asList(1, 2, 3));
        CopycatSchema.validateValue(
                SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.STRING_SCHEMA).build(),
                Collections.singletonMap(1, "value")
        );
        // Struct tests the basic struct layout + complex field types + nested structs
        Struct structValue = new Struct(STRUCT_SCHEMA)
                .put("first", 1)
                .put("second", "foo")
                .put("array", Arrays.asList(1, 2, 3))
                .put("map", Collections.singletonMap(1, "value"))
                .put("nested", new Struct(FLAT_STRUCT_SCHEMA).put("field", 12));
        CopycatSchema.validateValue(STRUCT_SCHEMA, structValue);
    }

    // To avoid requiring excessive numbers of tests, these checks for invalid types use a similar type where possible
    // to only include a single test for each type

    @Test(expected = DataException.class)
    public void testValidateValueMismatchInt8() {
        CopycatSchema.validateValue(Schema.INT8_SCHEMA, 1);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchInt16() {
        CopycatSchema.validateValue(Schema.INT16_SCHEMA, 1);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchInt32() {
        CopycatSchema.validateValue(Schema.INT32_SCHEMA, (long) 1);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchInt64() {
        CopycatSchema.validateValue(Schema.INT64_SCHEMA, 1);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchFloat() {
        CopycatSchema.validateValue(Schema.FLOAT32_SCHEMA, 1.0);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchDouble() {
        CopycatSchema.validateValue(Schema.FLOAT64_SCHEMA, 1.f);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchBoolean() {
        CopycatSchema.validateValue(Schema.BOOLEAN_SCHEMA, 1.f);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchString() {
        // CharSequence is a similar type (supertype of String), but we restrict to String.
        CharBuffer cbuf = CharBuffer.wrap("abc");
        CopycatSchema.validateValue(Schema.STRING_SCHEMA, cbuf);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchBytes() {
        CopycatSchema.validateValue(Schema.BYTES_SCHEMA, new Object[]{1, "foo"});
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchArray() {
        CopycatSchema.validateValue(SchemaBuilder.array(Schema.INT32_SCHEMA).build(), Arrays.asList("a", "b", "c"));
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchArraySomeMatch() {
        // Even if some match the right type, this should fail if any mismatch. In this case, type erasure loses
        // the fact that the list is actually List<Object>, but we couldn't tell if only checking the first element
        CopycatSchema.validateValue(SchemaBuilder.array(Schema.INT32_SCHEMA).build(), Arrays.asList(1, 2, "c"));
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchMapKey() {
        CopycatSchema.validateValue(MAP_INT_STRING_SCHEMA, Collections.singletonMap("wrong key type", "value"));
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchMapValue() {
        CopycatSchema.validateValue(MAP_INT_STRING_SCHEMA, Collections.singletonMap(1, 2));
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchMapSomeKeys() {
        Map<Object, String> data = new HashMap<>();
        data.put(1, "abc");
        data.put("wrong", "it's as easy as one two three");
        CopycatSchema.validateValue(MAP_INT_STRING_SCHEMA, data);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchMapSomeValues() {
        Map<Integer, Object> data = new HashMap<>();
        data.put(1, "abc");
        data.put(2, "wrong".getBytes());
        CopycatSchema.validateValue(MAP_INT_STRING_SCHEMA, data);
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchStructWrongSchema() {
        // Completely mismatching schemas
        CopycatSchema.validateValue(
                FLAT_STRUCT_SCHEMA,
                new Struct(SchemaBuilder.struct().field("x", Schema.INT32_SCHEMA).build()).put("x", 1)
        );
    }

    @Test(expected = DataException.class)
    public void testValidateValueMismatchStructWrongNestedSchema() {
        // Top-level schema  matches, but nested does not.
        CopycatSchema.validateValue(
                PARENT_STRUCT_SCHEMA,
                new Struct(PARENT_STRUCT_SCHEMA)
                        .put("nested", new Struct(SchemaBuilder.struct().field("x", Schema.INT32_SCHEMA).build()).put("x", 1))
        );
    }


    @Test
    public void testPrimitiveEquality() {
        // Test that primitive types, which only need to consider all the type & metadata fields, handle equality correctly
        CopycatSchema s1 = new CopycatSchema(Schema.Type.INT8, false, null, "name", 2, "doc", null, null, null);
        CopycatSchema s2 = new CopycatSchema(Schema.Type.INT8, false, null, "name", 2, "doc", null, null, null);
        CopycatSchema differentType = new CopycatSchema(Schema.Type.INT16, false, null, "name", 2, "doc", null, null, null);
        CopycatSchema differentOptional = new CopycatSchema(Schema.Type.INT8, true, null, "name", 2, "doc", null, null, null);
        CopycatSchema differentDefault = new CopycatSchema(Schema.Type.INT8, false, true, "name", 2, "doc", null, null, null);
        CopycatSchema differentName = new CopycatSchema(Schema.Type.INT8, false, null, "otherName", 2, "doc", null, null, null);
        CopycatSchema differentVersion = new CopycatSchema(Schema.Type.INT8, false, null, "name", 4, "doc", null, null, null);
        CopycatSchema differentDoc = new CopycatSchema(Schema.Type.INT8, false, null, "name", 2, "other doc", null, null, null);

        assertEquals(s1, s2);
        assertNotEquals(s1, differentType);
        assertNotEquals(s1, differentOptional);
        assertNotEquals(s1, differentDefault);
        assertNotEquals(s1, differentName);
        assertNotEquals(s1, differentVersion);
        assertNotEquals(s1, differentDoc);
    }

    @Test
    public void testArrayEquality() {
        // Validate that the value type for the array is tested for equality. This test makes sure the same schema object is
        // never reused to ensure we're actually checking equality
        CopycatSchema s1 = new CopycatSchema(Schema.Type.ARRAY, false, null, null, null, null, null, null, SchemaBuilder.int8().build());
        CopycatSchema s2 = new CopycatSchema(Schema.Type.ARRAY, false, null, null, null, null, null, null, SchemaBuilder.int8().build());
        CopycatSchema differentValueSchema = new CopycatSchema(Schema.Type.ARRAY, false, null, null, null, null, null, null, SchemaBuilder.int16().build());

        assertEquals(s1, s2);
        assertNotEquals(s1, differentValueSchema);
    }

    @Test
    public void testMapEquality() {
        // Same as testArrayEquality, but for both key and value schemas
        CopycatSchema s1 = new CopycatSchema(Schema.Type.MAP, false, null, null, null, null, null, SchemaBuilder.int8().build(), SchemaBuilder.int16().build());
        CopycatSchema s2 = new CopycatSchema(Schema.Type.MAP, false, null, null, null, null, null, SchemaBuilder.int8().build(), SchemaBuilder.int16().build());
        CopycatSchema differentKeySchema = new CopycatSchema(Schema.Type.MAP, false, null, null, null, null, null, SchemaBuilder.string().build(), SchemaBuilder.int16().build());
        CopycatSchema differentValueSchema = new CopycatSchema(Schema.Type.MAP, false, null, null, null, null, null, SchemaBuilder.int8().build(), SchemaBuilder.string().build());

        assertEquals(s1, s2);
        assertNotEquals(s1, differentKeySchema);
        assertNotEquals(s1, differentValueSchema);
    }

    @Test
    public void testStructEquality() {
        // Same as testArrayEquality, but checks differences in fields. Only does a simple check, relying on tests of
        // Field's equals() method to validate all variations in the list of fields will be checked
        CopycatSchema s1 = new CopycatSchema(Schema.Type.STRUCT, false, null, null, null, null,
                Arrays.asList(new Field("field", 0, SchemaBuilder.int8().build()),
                        new Field("field2", 1, SchemaBuilder.int16().build())), null, null);
        CopycatSchema s2 = new CopycatSchema(Schema.Type.STRUCT, false, null, null, null, null,
                Arrays.asList(new Field("field", 0, SchemaBuilder.int8().build()),
                        new Field("field2", 1, SchemaBuilder.int16().build())), null, null);
        CopycatSchema differentField = new CopycatSchema(Schema.Type.STRUCT, false, null, null, null, null,
                Arrays.asList(new Field("field", 0, SchemaBuilder.int8().build()),
                        new Field("different field name", 1, SchemaBuilder.int16().build())), null, null);

        assertEquals(s1, s2);
        assertNotEquals(s1, differentField);
    }

}
