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

import static org.junit.Assert.assertSame;
import static org.testng.Assert.assertEquals;

import org.apache.avro.generic.GenericData;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.impl.schema.AutoConsumeSchema;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.api.Record;
import org.testng.annotations.Test;

public class UnwrapKeyValueStepTest {

  @Test
  void testKeyValueUnwrapValue() throws Exception {
    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, new UnwrapKeyValueStep(false));

    GenericData.Record read = Utils.getRecord(message.getSchema(), (byte[]) message.getValue());
    assertEquals(
        read.toString(),
        "{\"valueField1\": \"value1\", \"valueField2\": \"value2\", \"valueField3\": "
            + "\"value3\"}");
  }

  @Test
  void testKeyValueUnwrapKey() throws Exception {
    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, new UnwrapKeyValueStep(true));

    GenericData.Record read = Utils.getRecord(message.getSchema(), (byte[]) message.getValue());
    assertEquals(
        read.toString(),
        "{\"keyField1\": \"key1\", \"keyField2\": \"key2\", \"keyField3\": \"key3\"}");
  }

  @Test
  void testPrimitive() throws Exception {
    Record<GenericObject> record =
        new Utils.TestRecord<>(
            Schema.STRING,
            AutoConsumeSchema.wrapPrimitiveObject("test-message", SchemaType.STRING, new byte[] {}),
            "test-key");
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, new UnwrapKeyValueStep(false));

    assertSame(message.getSchema(), record.getSchema());
    assertSame(message.getValue(), record.getValue().getNativeObject());
    assertEquals(message.getKey(), record.getKey().orElse(null));
  }
}
