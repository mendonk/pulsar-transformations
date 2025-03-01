/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.pulsar.functions.transforms;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.AssertJUnit.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.GenericSchema;
import org.apache.pulsar.client.api.schema.KeyValueSchema;
import org.apache.pulsar.client.api.schema.RecordSchemaBuilder;
import org.apache.pulsar.client.api.schema.SchemaBuilder;
import org.apache.pulsar.client.impl.schema.AutoConsumeSchema;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.api.Record;
import org.testng.annotations.Test;

public class DropFieldStepTest {

  @Test
  void testAvro() throws Exception {
    RecordSchemaBuilder recordSchemaBuilder = SchemaBuilder.record("record");
    recordSchemaBuilder.field("firstName").type(SchemaType.STRING);
    recordSchemaBuilder.field("lastName").type(SchemaType.STRING);
    recordSchemaBuilder.field("age").type(SchemaType.INT32);

    SchemaInfo schemaInfo = recordSchemaBuilder.build(SchemaType.AVRO);
    GenericSchema<GenericRecord> genericSchema = Schema.generic(schemaInfo);

    GenericRecord genericRecord =
        genericSchema
            .newRecordBuilder()
            .set("firstName", "Jane")
            .set("lastName", "Doe")
            .set("age", 42)
            .build();

    Record<GenericObject> record = new Utils.TestRecord<>(genericSchema, genericRecord, "test-key");

    DropFieldStep step =
        new DropFieldStep(new ArrayList<>(), Arrays.asList("firstName", "lastName"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);
    assertEquals(message.getKey(), "test-key");

    GenericData.Record read = Utils.getRecord(message.getSchema(), (byte[]) message.getValue());
    assertEquals(read.get("age"), 42);
    assertNull(read.getSchema().getField("firstName"));
    assertNull(read.getSchema().getField("lastName"));
  }

  @Test
  void testKeyValueAvro() throws Exception {
    DropFieldStep step =
        new DropFieldStep(
            Arrays.asList("keyField1", "keyField2"), Arrays.asList("valueField1", "valueField2"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(Utils.createTestAvroKeyValueRecord(), step);
    KeyValueSchema messageSchema = (KeyValueSchema) message.getSchema();
    KeyValue messageValue = (KeyValue) message.getValue();

    GenericData.Record keyAvroRecord =
        Utils.getRecord(messageSchema.getKeySchema(), (byte[]) messageValue.getKey());
    assertEquals(keyAvroRecord.get("keyField3"), new Utf8("key3"));
    assertNull(keyAvroRecord.getSchema().getField("keyField1"));
    assertNull(keyAvroRecord.getSchema().getField("keyField2"));

    GenericData.Record valueAvroRecord =
        Utils.getRecord(messageSchema.getValueSchema(), (byte[]) messageValue.getValue());
    assertEquals(valueAvroRecord.get("valueField3"), new Utf8("value3"));
    assertNull(valueAvroRecord.getSchema().getField("valueField1"));
    assertNull(valueAvroRecord.getSchema().getField("valueField2"));

    assertEquals(messageSchema.getKeyValueEncodingType(), KeyValueEncodingType.SEPARATED);
  }

  @Test
  void testAvroNotModified() throws Exception {
    RecordSchemaBuilder recordSchemaBuilder = SchemaBuilder.record("record");
    recordSchemaBuilder.field("firstName").type(SchemaType.STRING);
    recordSchemaBuilder.field("lastName").type(SchemaType.STRING);
    recordSchemaBuilder.field("age").type(SchemaType.INT32);

    SchemaInfo schemaInfo = recordSchemaBuilder.build(SchemaType.AVRO);
    GenericSchema<GenericRecord> genericSchema = Schema.generic(schemaInfo);

    GenericRecord genericRecord =
        genericSchema
            .newRecordBuilder()
            .set("firstName", "Jane")
            .set("lastName", "Doe")
            .set("age", 42)
            .build();

    Record<GenericObject> record = new Utils.TestRecord<>(genericSchema, genericRecord, "test-key");

    DropFieldStep step = new DropFieldStep(new ArrayList<>(), Collections.singletonList("other"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);
    assertSame(message.getSchema(), record.getSchema());
    assertSame(message.getValue(), record.getValue());
  }

  @Test
  void testKeyValueAvroNotModified() throws Exception {
    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();

    DropFieldStep step =
        new DropFieldStep(
            Collections.singletonList("otherKey"), Collections.singletonList("otherValue"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);
    KeyValueSchema messageSchema = (KeyValueSchema) message.getSchema();
    KeyValue messageValue = (KeyValue) message.getValue();

    KeyValueSchema recordSchema = (KeyValueSchema) record.getSchema();
    KeyValue recordValue = (KeyValue) record.getValue().getNativeObject();
    assertSame(messageSchema.getKeySchema(), recordSchema.getKeySchema());
    assertSame(messageSchema.getValueSchema(), recordSchema.getValueSchema());
    assertSame(messageValue.getKey(), recordValue.getKey());
    assertSame(messageValue.getValue(), recordValue.getValue());
  }

  @Test
  void testKeyValueAvroCached() throws Exception {
    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();

    DropFieldStep step =
        new DropFieldStep(
            Arrays.asList("keyField1", "keyField2"), Arrays.asList("valueField1", "valueField2"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);
    KeyValueSchema messageSchema = (KeyValueSchema) message.getSchema();

    message = Utils.process(Utils.createTestAvroKeyValueRecord(), step);
    KeyValueSchema newMessageSchema = (KeyValueSchema) message.getSchema();

    // Schema was modified by process operation
    KeyValueSchema recordSchema = (KeyValueSchema) record.getSchema();
    assertNotSame(
        messageSchema.getKeySchema().getNativeSchema().get(),
        recordSchema.getKeySchema().getNativeSchema().get());
    assertNotSame(
        messageSchema.getValueSchema().getNativeSchema().get(),
        recordSchema.getValueSchema().getNativeSchema().get());

    // Multiple process output the same cached schema
    assertSame(
        messageSchema.getKeySchema().getNativeSchema().get(),
        newMessageSchema.getKeySchema().getNativeSchema().get());
    assertSame(
        messageSchema.getValueSchema().getNativeSchema().get(),
        newMessageSchema.getValueSchema().getNativeSchema().get());
  }

  @Test
  void testPrimitives() throws Exception {
    Record<GenericObject> record =
        new Utils.TestRecord<>(
            Schema.STRING,
            AutoConsumeSchema.wrapPrimitiveObject("value", SchemaType.STRING, new byte[] {}),
            "test-key");

    DropFieldStep step =
        new DropFieldStep(Collections.singletonList("key"), Collections.singletonList("value"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);

    assertSame(message.getSchema(), record.getSchema());
    assertSame(message.getValue(), record.getValue().getNativeObject());
  }

  @Test
  void testKeyValuePrimitives() throws Exception {
    Schema<KeyValue<String, Integer>> keyValueSchema =
        Schema.KeyValue(Schema.STRING, Schema.INT32, KeyValueEncodingType.SEPARATED);

    KeyValue<String, Integer> keyValue = new KeyValue<>("key", 42);

    Record<GenericObject> record =
        new Utils.TestRecord<>(
            keyValueSchema,
            AutoConsumeSchema.wrapPrimitiveObject(keyValue, SchemaType.KEY_VALUE, new byte[] {}),
            null);

    DropFieldStep step =
        new DropFieldStep(Collections.singletonList("key"), Collections.singletonList("value"));
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);
    KeyValueSchema messageSchema = (KeyValueSchema) message.getSchema();
    KeyValue messageValue = (KeyValue) message.getValue();

    KeyValueSchema recordSchema = (KeyValueSchema) record.getSchema();
    KeyValue recordValue = ((KeyValue) record.getValue().getNativeObject());
    assertSame(messageSchema.getKeySchema(), recordSchema.getKeySchema());
    assertSame(messageSchema.getValueSchema(), recordSchema.getValueSchema());
    assertSame(messageValue.getKey(), recordValue.getKey());
    assertSame(messageValue.getValue(), recordValue.getValue());
  }
}
