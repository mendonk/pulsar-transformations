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
import static org.testng.Assert.assertSame;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.KeyValueSchema;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.api.Record;
import org.testng.annotations.Test;

public class CastStepTest {

  @Test
  void testKeyValueAvroToString() throws Exception {
    Record<GenericObject> record = Utils.createTestAvroKeyValueRecord();
    CastStep step = new CastStep(SchemaType.STRING, SchemaType.STRING);
    Utils.TestTypedMessageBuilder<?> message = Utils.process(record, step);

    KeyValueSchema messageSchema = (KeyValueSchema) message.getSchema();
    KeyValue messageValue = (KeyValue) message.getValue();

    assertSame(messageSchema.getKeySchema(), Schema.STRING);
    assertEquals(
        messageValue.getKey(),
        "{\"keyField1\": \"key1\", \"keyField2\": \"key2\", \"keyField3\": \"key3\"}");
    assertSame(messageSchema.getValueSchema(), Schema.STRING);
    assertEquals(
        messageValue.getValue(),
        "{\"valueField1\": \"value1\", \"valueField2\": \"value2\", "
            + "\"valueField3\": \"value3\"}");
  }
}
