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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.schema.SchemaType;

@Slf4j
public class MergeKeyValueStep implements TransformStep {

  private final Map<org.apache.avro.Schema, Map<org.apache.avro.Schema, org.apache.avro.Schema>>
      schemaCache = new ConcurrentHashMap<>();

  @Override
  public void process(TransformContext transformContext) {
    Schema<?> keySchema = transformContext.getKeySchema();
    if (keySchema == null) {
      return;
    }
    if (keySchema.getSchemaInfo().getType() == SchemaType.AVRO
        && transformContext.getValueSchema().getSchemaInfo().getType() == SchemaType.AVRO) {
      GenericRecord avroKeyRecord = (GenericRecord) transformContext.getKeyObject();
      org.apache.avro.Schema avroKeySchema = avroKeyRecord.getSchema();

      GenericRecord avroValueRecord = (GenericRecord) transformContext.getValueObject();
      org.apache.avro.Schema avroValueSchema = avroValueRecord.getSchema();

      List<String> valueSchemaFieldNames =
          avroValueSchema
              .getFields()
              .stream()
              .map(org.apache.avro.Schema.Field::name)
              .collect(Collectors.toList());
      List<org.apache.avro.Schema.Field> fields =
          avroKeySchema
              .getFields()
              .stream()
              .filter(field -> !valueSchemaFieldNames.contains(field.name()))
              .map(
                  f ->
                      new org.apache.avro.Schema.Field(
                          f.name(), f.schema(), f.doc(), f.defaultVal(), f.order()))
              .collect(Collectors.toList());
      fields.addAll(
          avroValueSchema
              .getFields()
              .stream()
              .map(
                  f ->
                      new org.apache.avro.Schema.Field(
                          f.name(), f.schema(), f.doc(), f.defaultVal(), f.order()))
              .collect(Collectors.toList()));

      Map<org.apache.avro.Schema, org.apache.avro.Schema> schemaCacheKey =
          schemaCache.computeIfAbsent(avroKeySchema, s -> new ConcurrentHashMap<>());
      org.apache.avro.Schema modified =
          schemaCacheKey.computeIfAbsent(
              avroValueSchema,
              schema ->
                  org.apache.avro.Schema.createRecord(
                      avroValueSchema.getName(),
                      null,
                      avroValueSchema.getNamespace(),
                      false,
                      fields));
      GenericRecord newRecord = new GenericData.Record(modified);
      for (String fieldName : valueSchemaFieldNames) {
        newRecord.put(fieldName, avroValueRecord.get(fieldName));
      }
      for (org.apache.avro.Schema.Field field : avroKeySchema.getFields()) {
        newRecord.put(field.name(), avroKeyRecord.get(field.name()));
      }
      transformContext.setValueObject(newRecord);
      transformContext.setValueModified(true);
    }
  }
}
