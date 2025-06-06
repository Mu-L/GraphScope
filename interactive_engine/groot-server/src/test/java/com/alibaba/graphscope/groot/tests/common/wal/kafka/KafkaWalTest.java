/**
 * Copyright 2020 Alibaba Group Holding Limited.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.graphscope.groot.tests.common.wal.kafka;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.graphscope.groot.common.config.CommonConfig;
import com.alibaba.graphscope.groot.common.config.Configs;
import com.alibaba.graphscope.groot.common.config.KafkaConfig;
import com.alibaba.graphscope.groot.operation.OperationBatch;
import com.alibaba.graphscope.groot.operation.OperationBlob;
import com.alibaba.graphscope.groot.wal.LogEntry;
import com.alibaba.graphscope.groot.wal.LogReader;
import com.alibaba.graphscope.groot.wal.LogService;
import com.alibaba.graphscope.groot.wal.LogWriter;
import com.alibaba.graphscope.groot.wal.kafka.KafkaLogService;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

public class KafkaWalTest {

    @RegisterExtension
    static final SharedKafkaTestResource sharedKafkaTestResource = new SharedKafkaTestResource();

    @Test
    void testDoubleDestroy() {
        Configs configs =
                Configs.newBuilder()
                        .put(
                                KafkaConfig.KAFKA_SERVERS.getKey(),
                                sharedKafkaTestResource.getKafkaConnectString())
                        .put(KafkaConfig.KAFKA_TOPIC.getKey(), "test_double_destroy")
                        .put(CommonConfig.STORE_NODE_COUNT.getKey(), "1")
                        .build();
        LogService logService = new KafkaLogService(configs);
        logService.init();
        logService.destroy();
        assertThrows(Exception.class, () -> logService.destroy());
    }

    @Test
    void testDoubleInit() {
        Configs configs =
                Configs.newBuilder()
                        .put(
                                KafkaConfig.KAFKA_SERVERS.getKey(),
                                sharedKafkaTestResource.getKafkaConnectString())
                        .put(KafkaConfig.KAFKA_TOPIC.getKey(), "test_double_init")
                        .put(CommonConfig.STORE_NODE_COUNT.getKey(), "1")
                        .build();
        LogService logService = new KafkaLogService(configs);
        logService.init();
        assertThrows(Exception.class, () -> logService.init());
        logService.destroy();
    }

    @Test
    void testLogService() throws IOException {
        Configs configs =
                Configs.newBuilder()
                        .put(
                                KafkaConfig.KAFKA_SERVERS.getKey(),
                                sharedKafkaTestResource.getKafkaConnectString())
                        .put(KafkaConfig.KAFKA_TOPIC.getKey(), "test_logservice")
                        .put(CommonConfig.STORE_NODE_COUNT.getKey(), "1")
                        .build();
        LogService logService = new KafkaLogService(configs);
        logService.init();
        int queueId = 0;
        long snapshotId = 1L;
        LogWriter writer = logService.createWriter();
        LogEntry logEntry =
                new LogEntry(
                        snapshotId,
                        OperationBatch.newBuilder()
                                .addOperationBlob(OperationBlob.MARKER_OPERATION_BLOB)
                                .build());
        assertEquals(writer.append(queueId, logEntry), 0);

        LogReader reader = logService.createReader(queueId, 0);
        ConsumerRecord<LogEntry, LogEntry> record = reader.readNextRecord();
        reader.close();

        assertAll(
                () -> assertEquals(record.offset(), 0),
                () -> assertEquals(record.value().getSnapshotId(), snapshotId));

        OperationBatch operationBatch = record.value().getOperationBatch();
        assertEquals(operationBatch.getOperationCount(), 1);
        assertEquals(operationBatch.getOperationBlob(0), OperationBlob.MARKER_OPERATION_BLOB);

        assertEquals(writer.append(queueId, logEntry), 1);
        assertEquals(writer.append(queueId, logEntry), 2);
        assertEquals(writer.append(queueId, logEntry), 3);

        LogReader readerTail = logService.createReader(queueId, 4);
        assertNull(readerTail.readNextRecord());
        readerTail.close();

        assertThrows(IllegalArgumentException.class, () -> logService.createReader(queueId, 5));
        logService.deleteBeforeOffset(queueId, 2);
        assertThrows(IllegalArgumentException.class, () -> logService.createReader(queueId, 1));
        writer.close();
        logService.destroy();
    }
}
