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
package com.alibaba.graphscope.groot.sdk;

import com.alibaba.graphscope.groot.sdk.api.Writer;
import com.alibaba.graphscope.groot.sdk.schema.Edge;
import com.alibaba.graphscope.groot.sdk.schema.Schema;
import com.alibaba.graphscope.groot.sdk.schema.Vertex;
import com.alibaba.graphscope.proto.groot.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GrootClient implements Writer {
    private final ClientGrpc.ClientBlockingStub clientStub;
    private final ClientWriteGrpc.ClientWriteBlockingStub writeStub;
    private final ClientWriteGrpc.ClientWriteStub asyncWriteStub;
    private final ClientBackupGrpc.ClientBackupBlockingStub backupStub;

    private final GrootDdlServiceGrpc.GrootDdlServiceBlockingStub ddlStub;

    private GrootClient(
            ClientGrpc.ClientBlockingStub clientBlockingStub,
            ClientWriteGrpc.ClientWriteBlockingStub clientWriteBlockingStub,
            ClientWriteGrpc.ClientWriteStub clientWriteStub,
            ClientBackupGrpc.ClientBackupBlockingStub clientBackupBlockingStub,
            GrootDdlServiceGrpc.GrootDdlServiceBlockingStub ddlServiceBlockingStub) {
        this.clientStub = clientBlockingStub;
        this.writeStub = clientWriteBlockingStub;
        this.asyncWriteStub = clientWriteStub;
        this.backupStub = clientBackupBlockingStub;
        this.ddlStub = ddlServiceBlockingStub;
    }

    public void close() {}

    public com.alibaba.graphscope.proto.GraphDefPb submitSchema(Schema schema) {
        BatchSubmitRequest request = schema.toProto();
        BatchSubmitResponse response = ddlStub.batchSubmit(request);
        return response.getGraphDef();
    }

    public com.alibaba.graphscope.proto.GraphDefPb submitSchema(
            Schema schema, RequestOptions options) {
        BatchSubmitRequest request = schema.toProto(options);
        BatchSubmitResponse response = ddlStub.batchSubmit(request);
        return response.getGraphDef();
    }

    public com.alibaba.graphscope.proto.GraphDefPb submitSchema(Schema.Builder schema) {
        return submitSchema(schema.build());
    }

    public com.alibaba.graphscope.proto.GraphDefPb submitSchema(
            Schema.Builder schema, RequestOptions options) {
        return submitSchema(schema.build(), options);
    }

    private BatchWriteRequest.Builder getNewWriteBuilder() {
        String clientId =
                writeStub.getClientId(GetClientIdRequest.newBuilder().build()).getClientId();
        return BatchWriteRequest.newBuilder().setClientId(clientId);
    }

    /**
     * Block until this snapshot becomes available.
     * @param snapshotId the snapshot id to be flushed
     */
    public boolean remoteFlush(long snapshotId) {
        RemoteFlushResponse resp =
                this.writeStub.remoteFlush(
                        RemoteFlushRequest.newBuilder().setSnapshotId(snapshotId).build());
        return resp.getSuccess();
    }

    public List<Long> replayRecords(long offset, long timestamp) {
        ReplayRecordsRequest req =
                ReplayRecordsRequest.newBuilder().setOffset(offset).setTimestamp(timestamp).build();
        ReplayRecordsResponse resp = writeStub.replayRecords(req);
        return resp.getSnapshotIdList();
    }

    public List<Long> replayRecordsV2(long offset, long timestamp) {
        ReplayRecordsRequestV2 req =
                ReplayRecordsRequestV2.newBuilder()
                        .setOffset(offset)
                        .setTimestamp(timestamp)
                        .build();
        ReplayRecordsResponseV2 resp = this.clientStub.replayRecordsV2(req);
        return resp.getSnapshotIdList();
    }

    private long modifyVertex(Vertex vertex, WriteTypePb writeType) {
        WriteRequestPb request = vertex.toWriteRequest(writeType);
        return submit(request);
    }

    private long modifyVertex(Vertex vertex, WriteTypePb writeType, RequestOptions options) {
        WriteRequestPb request = vertex.toWriteRequest(writeType);
        return submit(request, options);
    }

    private long modifyVertex(List<Vertex> vertices, WriteTypePb writeType) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        return submit(requests);
    }

    private long modifyVertex(
            List<Vertex> vertices, WriteTypePb writeType, RequestOptions options) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        return submit(requests, options);
    }

    private void modifyVertex(
            Vertex vertex, StreamObserver<BatchWriteResponse> callback, WriteTypePb writeType) {
        WriteRequestPb request = vertex.toWriteRequest(writeType);
        submit(request, callback);
    }

    private void modifyVertex(
            Vertex vertex,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType,
            RequestOptions options) {
        WriteRequestPb request = vertex.toWriteRequest(writeType);
        submit(request, options, callback);
    }

    private void modifyVertex(
            List<Vertex> vertices,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        submit(requests, callback);
    }

    private void modifyVertex(
            List<Vertex> vertices,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType,
            RequestOptions options) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        submit(requests, options, callback);
    }

    private long modifyEdge(Edge edge, WriteTypePb writeType) {
        WriteRequestPb request = edge.toWriteRequest(writeType);
        return submit(request);
    }

    private long modifyEdge(Edge edge, WriteTypePb writeType, RequestOptions options) {
        WriteRequestPb request = edge.toWriteRequest(writeType);
        return submit(request, options);
    }

    private long modifyEdge(List<Edge> edges, WriteTypePb writeType) {
        List<WriteRequestPb> requests = getEdgeWriteRequestPbs(edges, writeType);
        return submit(requests);
    }

    private long modifyEdge(List<Edge> edges, WriteTypePb writeType, RequestOptions options) {
        List<WriteRequestPb> requests = getEdgeWriteRequestPbs(edges, writeType);
        return submit(requests, options);
    }

    private void modifyEdge(
            Edge edge, StreamObserver<BatchWriteResponse> callback, WriteTypePb writeType) {
        WriteRequestPb request = edge.toWriteRequest(writeType);
        submit(request, callback);
    }

    private void modifyEdge(
            Edge edge,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType,
            RequestOptions options) {
        WriteRequestPb request = edge.toWriteRequest(writeType);
        submit(request, options, callback);
    }

    private void modifyEdge(
            List<Edge> edges, StreamObserver<BatchWriteResponse> callback, WriteTypePb writeType) {
        List<WriteRequestPb> requests = getEdgeWriteRequestPbs(edges, writeType);
        submit(requests, callback);
    }

    private void modifyEdge(
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType,
            RequestOptions options) {
        List<WriteRequestPb> requests = getEdgeWriteRequestPbs(edges, writeType);
        submit(requests, options, callback);
    }

    private long modifyVerticesAndEdge(
            List<Vertex> vertices, List<Edge> edges, WriteTypePb writeType) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        requests.addAll(getEdgeWriteRequestPbs(edges, writeType));
        return submit(requests);
    }

    private long modifyVerticesAndEdge(
            List<Vertex> vertices,
            List<Edge> edges,
            WriteTypePb writeType,
            RequestOptions options) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        requests.addAll(getEdgeWriteRequestPbs(edges, writeType));
        return submit(requests, options);
    }

    private void modifyVerticesAndEdge(
            List<Vertex> vertices,
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        requests.addAll(getEdgeWriteRequestPbs(edges, writeType));
        submit(requests, callback);
    }

    private void modifyVerticesAndEdge(
            List<Vertex> vertices,
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            WriteTypePb writeType,
            RequestOptions options) {
        List<WriteRequestPb> requests = getVertexWriteRequestPbs(vertices, writeType);
        requests.addAll(getEdgeWriteRequestPbs(edges, writeType));
        submit(requests, options, callback);
    }

    public long addVerticesAndEdges(List<Vertex> vertices, List<Edge> edges) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.INSERT);
    }

    public long addVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, RequestOptions options) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.INSERT, options);
    }

    public long updateVerticesAndEdges(List<Vertex> vertices, List<Edge> edges) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.UPDATE);
    }

    public long updateVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, RequestOptions options) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.UPDATE, options);
    }

    public long deleteVerticesAndEdges(List<Vertex> vertices, List<Edge> edges) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.DELETE);
    }

    public long deleteVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, RequestOptions options) {
        return modifyVerticesAndEdge(vertices, edges, WriteTypePb.DELETE, options);
    }

    public void addVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.INSERT);
    }

    public void addVerticesAndEdges(
            List<Vertex> vertices,
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.INSERT, options);
    }

    public void updateVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.UPDATE);
    }

    public void updateVerticesAndEdges(
            List<Vertex> vertices,
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.UPDATE, options);
    }

    public void deleteVerticesAndEdges(
            List<Vertex> vertices, List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.DELETE);
    }

    public void deleteVerticesAndEdges(
            List<Vertex> vertices,
            List<Edge> edges,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVerticesAndEdge(vertices, edges, callback, WriteTypePb.DELETE, options);
    }

    /**
     * Add vertex by realtime write
     * @param vertex vertex that contains label and pk properties and other properties
     */
    public long addVertex(Vertex vertex) {
        return modifyVertex(vertex, WriteTypePb.INSERT);
    }

    public long addVertex(Vertex vertex, RequestOptions options) {
        return modifyVertex(vertex, WriteTypePb.INSERT, options);
    }

    public long addVertices(List<Vertex> vertices) {
        return modifyVertex(vertices, WriteTypePb.INSERT);
    }

    public long addVertices(List<Vertex> vertices, RequestOptions options) {
        return modifyVertex(vertices, WriteTypePb.INSERT, options);
    }

    public void addVertex(Vertex vertex, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertex, callback, WriteTypePb.INSERT);
    }

    public void addVertex(
            Vertex vertex, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyVertex(vertex, callback, WriteTypePb.INSERT, options);
    }

    public void addVertices(List<Vertex> vertices, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertices, callback, WriteTypePb.INSERT);
    }

    public void addVertices(
            List<Vertex> vertices,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVertex(vertices, callback, WriteTypePb.INSERT, options);
    }

    /**
     * Update existed vertex by realtime write
     * @param vertex vertex that contains label and pk properties and other properties
     */
    public long updateVertex(Vertex vertex) {
        return modifyVertex(vertex, WriteTypePb.UPDATE);
    }

    public long updateVertex(Vertex vertex, RequestOptions options) {
        return modifyVertex(vertex, WriteTypePb.UPDATE, options);
    }

    public long updateVertices(List<Vertex> vertices) {
        return modifyVertex(vertices, WriteTypePb.UPDATE);
    }

    public long updateVertices(List<Vertex> vertices, RequestOptions options) {
        return modifyVertex(vertices, WriteTypePb.UPDATE, options);
    }

    public void updateVertex(Vertex vertex, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertex, callback, WriteTypePb.UPDATE);
    }

    public void updateVertex(
            Vertex vertex, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyVertex(vertex, callback, WriteTypePb.UPDATE, options);
    }

    public void updateVertices(List<Vertex> vertices, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertices, callback, WriteTypePb.UPDATE);
    }

    public void updateVertices(
            List<Vertex> vertices,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVertex(vertices, callback, WriteTypePb.UPDATE, options);
    }

    /**
     * Delete vertex by its primary key
     * @param vertex vertex that contains label and primary key properties
     */
    public long deleteVertex(Vertex vertex) {
        return modifyVertex(vertex, WriteTypePb.DELETE);
    }

    public long deleteVertex(Vertex vertex, RequestOptions options) {
        return modifyVertex(vertex, WriteTypePb.DELETE, options);
    }

    public long deleteVertices(List<Vertex> vertices) {
        return modifyVertex(vertices, WriteTypePb.DELETE);
    }

    public long deleteVertices(List<Vertex> vertices, RequestOptions options) {
        return modifyVertex(vertices, WriteTypePb.DELETE, options);
    }

    public void deleteVertex(Vertex vertex, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertex, callback, WriteTypePb.DELETE);
    }

    public void deleteVertex(
            Vertex vertex, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyVertex(vertex, callback, WriteTypePb.DELETE, options);
    }

    public void deleteVertices(List<Vertex> vertices, StreamObserver<BatchWriteResponse> callback) {
        modifyVertex(vertices, callback, WriteTypePb.DELETE);
    }

    public void deleteVertices(
            List<Vertex> vertices,
            StreamObserver<BatchWriteResponse> callback,
            RequestOptions options) {
        modifyVertex(vertices, callback, WriteTypePb.DELETE, options);
    }

    public long clearVertexProperty(Vertex vertex) {
        return modifyVertex(vertex, WriteTypePb.CLEAR_PROPERTY);
    }

    public long clearVertexProperty(Vertex vertex, RequestOptions options) {
        return modifyVertex(vertex, WriteTypePb.CLEAR_PROPERTY, options);
    }

    public long clearVertexProperties(List<Vertex> vertices) {
        return modifyVertex(vertices, WriteTypePb.CLEAR_PROPERTY);
    }

    public long clearVertexProperties(List<Vertex> vertices, RequestOptions options) {
        return modifyVertex(vertices, WriteTypePb.CLEAR_PROPERTY, options);
    }

    /**
     * Add edge by realtime write
     * @param edge edge that contains label, src vertex label and pk, dst label and pk, and properties
     */
    public long addEdge(Edge edge) {
        return modifyEdge(edge, WriteTypePb.INSERT);
    }

    public long addEdge(Edge edge, RequestOptions options) {
        return modifyEdge(edge, WriteTypePb.INSERT, options);
    }

    public long addEdges(List<Edge> edges) {
        return modifyEdge(edges, WriteTypePb.INSERT);
    }

    public long addEdges(List<Edge> edges, RequestOptions options) {
        return modifyEdge(edges, WriteTypePb.INSERT, options);
    }

    public void addEdge(Edge edge, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edge, callback, WriteTypePb.INSERT);
    }

    public void addEdge(
            Edge edge, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edge, callback, WriteTypePb.INSERT, options);
    }

    public void addEdges(List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edges, callback, WriteTypePb.INSERT);
    }

    public void addEdges(
            List<Edge> edges, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edges, callback, WriteTypePb.INSERT, options);
    }

    /**
     * Update existed edge by realtime write
     * @param edge edge that contains label, src vertex label and pk, dst label and pk, and properties
     */
    public long updateEdge(Edge edge) {
        return modifyEdge(edge, WriteTypePb.UPDATE);
    }

    public long updateEdge(Edge edge, RequestOptions options) {
        return modifyEdge(edge, WriteTypePb.UPDATE, options);
    }

    public long updateEdges(List<Edge> edges) {
        return modifyEdge(edges, WriteTypePb.UPDATE);
    }

    public long updateEdges(List<Edge> edges, RequestOptions options) {
        return modifyEdge(edges, WriteTypePb.UPDATE, options);
    }

    public void updateEdge(Edge edge, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edge, callback, WriteTypePb.UPDATE);
    }

    public void updateEdge(
            Edge edge, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edge, callback, WriteTypePb.UPDATE, options);
    }

    public void updateEdges(List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edges, callback, WriteTypePb.UPDATE);
    }

    public void updateEdges(
            List<Edge> edges, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edges, callback, WriteTypePb.UPDATE, options);
    }

    /**
     * Delete an edge by realtime write
     * @param edge edge that contains label, src vertex label and pk, dst label and pk, no properties required
     */
    public long deleteEdge(Edge edge) {
        return modifyEdge(edge, WriteTypePb.DELETE);
    }

    public long deleteEdge(Edge edge, RequestOptions options) {
        return modifyEdge(edge, WriteTypePb.DELETE, options);
    }

    public long deleteEdges(List<Edge> edges) {
        return modifyEdge(edges, WriteTypePb.DELETE);
    }

    public long deleteEdges(List<Edge> edges, RequestOptions options) {
        return modifyEdge(edges, WriteTypePb.DELETE, options);
    }

    public void deleteEdge(Edge edge, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edge, callback, WriteTypePb.DELETE);
    }

    public void deleteEdge(
            Edge edge, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edge, callback, WriteTypePb.DELETE, options);
    }

    public void deleteEdges(List<Edge> edges, StreamObserver<BatchWriteResponse> callback) {
        modifyEdge(edges, callback, WriteTypePb.DELETE);
    }

    public void deleteEdges(
            List<Edge> edges, StreamObserver<BatchWriteResponse> callback, RequestOptions options) {
        modifyEdge(edges, callback, WriteTypePb.DELETE, options);
    }

    public long clearEdgeProperty(Edge edge) {
        return modifyEdge(edge, WriteTypePb.CLEAR_PROPERTY);
    }

    public long clearEdgeProperty(Edge edge, RequestOptions options) {
        return modifyEdge(edge, WriteTypePb.CLEAR_PROPERTY, options);
    }

    public long clearEdgeProperties(List<Edge> edges) {
        return modifyEdge(edges, WriteTypePb.CLEAR_PROPERTY);
    }

    public long clearEdgeProperties(List<Edge> edges, RequestOptions options) {
        return modifyEdge(edges, WriteTypePb.CLEAR_PROPERTY, options);
    }

    /**
     * Commit the realtime write transaction.
     * @return The snapshot_id. The data committed would be available after a while, or you could remoteFlush(snapshot_id)
     * and wait for its return.
     */
    private long submit(WriteRequestPb request) {
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addWriteRequests(request);
        return writeStub.batchWrite(batchWriteBuilder.build()).getSnapshotId();
    }

    private long submit(WriteRequestPb request, RequestOptions options) {
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addWriteRequests(request);
        batchWriteBuilder.setRequestOptions(options.toWriteRequest());
        return writeStub.batchWrite(batchWriteBuilder.build()).getSnapshotId();
    }

    private long submit(List<WriteRequestPb> requests) {
        if (requests.isEmpty()) {
            return 0;
        }
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addAllWriteRequests(requests);
        return writeStub.batchWrite(batchWriteBuilder.build()).getSnapshotId();
    }

    private long submit(List<WriteRequestPb> requests, RequestOptions options) {
        if (requests.isEmpty()) {
            return 0;
        }
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addAllWriteRequests(requests);
        batchWriteBuilder.setRequestOptions(options.toWriteRequest());
        return writeStub.batchWrite(batchWriteBuilder.build()).getSnapshotId();
    }

    private void submit(WriteRequestPb request, StreamObserver<BatchWriteResponse> callback) {
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addWriteRequests(request);
        asyncWriteStub.batchWrite(batchWriteBuilder.build(), callback);
    }

    private void submit(
            WriteRequestPb request,
            RequestOptions options,
            StreamObserver<BatchWriteResponse> callback) {
        BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
        batchWriteBuilder.addWriteRequests(request);
        batchWriteBuilder.setRequestOptions(options.toWriteRequest());
        asyncWriteStub.batchWrite(batchWriteBuilder.build(), callback);
    }

    private void submit(
            List<WriteRequestPb> requests, StreamObserver<BatchWriteResponse> callback) {
        if (!requests.isEmpty()) {
            BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
            batchWriteBuilder.addAllWriteRequests(requests);
            asyncWriteStub.batchWrite(batchWriteBuilder.build(), callback);
        }
    }

    private void submit(
            List<WriteRequestPb> requests,
            RequestOptions options,
            StreamObserver<BatchWriteResponse> callback) {
        if (!requests.isEmpty()) {
            BatchWriteRequest.Builder batchWriteBuilder = getNewWriteBuilder();
            batchWriteBuilder.addAllWriteRequests(requests);
            batchWriteBuilder.setRequestOptions(options.toWriteRequest());
            asyncWriteStub.batchWrite(batchWriteBuilder.build(), callback);
        }
    }

    public GraphDefPb getSchema() {
        GetSchemaResponse response =
                this.clientStub.getSchema(GetSchemaRequest.newBuilder().build());
        return response.getGraphDef();
    }

    public GraphDefPb dropSchema() {
        DropSchemaResponse response =
                this.clientStub.dropSchema(DropSchemaRequest.newBuilder().build());
        return response.getGraphDef();
    }

    public GraphDefPb prepareDataLoad(List<DataLoadTargetPb> targets) {
        PrepareDataLoadRequest.Builder builder = PrepareDataLoadRequest.newBuilder();

        for (DataLoadTargetPb target : targets) {
            builder.addDataLoadTargets(target);
        }
        PrepareDataLoadResponse response = this.clientStub.prepareDataLoad(builder.build());
        return response.getGraphDef();
    }

    public void commitDataLoad(Map<Long, DataLoadTargetPb> tableToTarget, String path) {
        CommitDataLoadRequest.Builder builder = CommitDataLoadRequest.newBuilder();
        tableToTarget.forEach(builder::putTableToTarget);
        builder.setPath(path);
        CommitDataLoadResponse response = this.clientStub.commitDataLoad(builder.build());
    }

    public String getMetrics(String roleNames) {
        GetMetricsResponse response =
                this.clientStub.getMetrics(
                        GetMetricsRequest.newBuilder().setRoleNames(roleNames).build());
        return response.getMetricsJson();
    }

    public void ingestData(String path) {
        this.clientStub.ingestData(IngestDataRequest.newBuilder().setDataPath(path).build());
    }

    public void ingestData(String path, Map<String, String> config) {
        IngestDataRequest.Builder builder = IngestDataRequest.newBuilder();
        builder.setDataPath(path);
        if (config != null) {
            builder.putAllConfig(config);
        }
        this.clientStub.ingestData(builder.build());
    }

    public boolean compactDB() {
        CompactDBRequest request = CompactDBRequest.newBuilder().build();
        CompactDBResponse response = this.clientStub.compactDB(request);
        return response.getSuccess();
    }

    public boolean reopenSecondary() {
        ReopenSecondaryRequest request = ReopenSecondaryRequest.newBuilder().build();
        ReopenSecondaryResponse response = this.clientStub.reopenSecondary(request);
        return response.getSuccess();
    }

    public String loadJsonSchema(Path jsonFile) throws IOException {
        String json = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
        return loadSchema(json, 0);
    }

    public String loadJsonSchema(String json) throws IOException {
        return loadSchema(json, 0);
    }

    /**
     * Load schema from string, accept json or yaml format
     * @param str the string represented schema
     * @param schemaType 0 for json, 1 for yaml
     * @return graphdef
     */
    public String loadSchema(String str, int schemaType) {
        LoadSchemaResponse response =
                this.clientStub.loadSchema(
                        LoadSchemaRequest.newBuilder()
                                .setSchemaStr(str)
                                .setSchemaType(schemaType)
                                .build());
        return response.getGraphDef().toString();
    }

    public String loadSchema(String str) {
        return loadSchema(str, 0);
    }

    public int getPartitionNum() {
        GetPartitionNumResponse response =
                this.clientStub.getPartitionNum(GetPartitionNumRequest.newBuilder().build());
        return response.getPartitionNum();
    }

    public int createNewGraphBackup() {
        CreateNewGraphBackupResponse response =
                this.backupStub.createNewGraphBackup(
                        CreateNewGraphBackupRequest.newBuilder().build());
        return response.getBackupId();
    }

    public void deleteGraphBackup(int backupId) {
        this.backupStub.deleteGraphBackup(
                DeleteGraphBackupRequest.newBuilder().setBackupId(backupId).build());
    }

    public void purgeOldGraphBackups(int keepAliveNumber) {
        this.backupStub.purgeOldGraphBackups(
                PurgeOldGraphBackupsRequest.newBuilder()
                        .setKeepAliveNumber(keepAliveNumber)
                        .build());
    }

    public void restoreFromGraphBackup(
            int backupId, String metaRestorePath, String storeRestorePath) {
        this.backupStub.restoreFromGraphBackup(
                RestoreFromGraphBackupRequest.newBuilder()
                        .setBackupId(backupId)
                        .setMetaRestorePath(metaRestorePath)
                        .setStoreRestorePath(storeRestorePath)
                        .build());
    }

    public boolean verifyGraphBackup(int backupId) {
        VerifyGraphBackupResponse response =
                this.backupStub.verifyGraphBackup(
                        VerifyGraphBackupRequest.newBuilder().setBackupId(backupId).build());
        boolean suc = response.getIsOk();
        if (!suc) {
            System.err.println("verify backup [" + backupId + "] failed, " + response.getErrMsg());
        }
        return suc;
    }

    public List<BackupInfoPb> getGraphBackupInfo() {
        GetGraphBackupInfoResponse response =
                this.backupStub.getGraphBackupInfo(GetGraphBackupInfoRequest.newBuilder().build());
        return new ArrayList<>(response.getBackupInfoListList());
    }

    public void clearIngest(String dataPath) {
        this.clientStub.clearIngest(ClearIngestRequest.newBuilder().setDataPath(dataPath).build());
    }

    public static GrootClientBuilder newBuilder() {
        return new GrootClientBuilder();
    }

    public static class GrootClientBuilder {
        private String target;
        private String username;
        private String password;

        private GrootClientBuilder() {}

        public GrootClientBuilder addHost(String host, int port) {
            target = host + ":" + port;
            return this;
        }

        public GrootClientBuilder setHosts(String target) {
            this.target = target;
            return this;
        }

        public GrootClientBuilder setUsername(String username) {
            this.username = username;
            return this;
        }

        public GrootClientBuilder setPassword(String password) {
            this.password = password;
            return this;
        }

        public GrootClient build() {
            ManagedChannel channel =
                    ManagedChannelBuilder.forTarget(target)
                            .defaultLoadBalancingPolicy("round_robin")
                            .usePlaintext()
                            .build();

            ClientGrpc.ClientBlockingStub clientBlockingStub = ClientGrpc.newBlockingStub(channel);
            ClientWriteGrpc.ClientWriteBlockingStub clientWriteBlockingStub =
                    ClientWriteGrpc.newBlockingStub(channel);
            ClientWriteGrpc.ClientWriteStub clientWriteStub = ClientWriteGrpc.newStub(channel);
            ClientBackupGrpc.ClientBackupBlockingStub clientBackupBlockingStub =
                    ClientBackupGrpc.newBlockingStub(channel);
            GrootDdlServiceGrpc.GrootDdlServiceBlockingStub ddlServiceBlockingStub =
                    GrootDdlServiceGrpc.newBlockingStub(channel);
            if (username != null && password != null) {
                BasicAuth basicAuth = new BasicAuth(username, password);
                clientBlockingStub = clientBlockingStub.withCallCredentials(basicAuth);
                clientWriteBlockingStub = clientWriteBlockingStub.withCallCredentials(basicAuth);
                clientWriteStub = clientWriteStub.withCallCredentials(basicAuth);
                clientBackupBlockingStub = clientBackupBlockingStub.withCallCredentials(basicAuth);
                ddlServiceBlockingStub = ddlServiceBlockingStub.withCallCredentials(basicAuth);
            }
            return new GrootClient(
                    clientBlockingStub,
                    clientWriteBlockingStub,
                    clientWriteStub,
                    clientBackupBlockingStub,
                    ddlServiceBlockingStub);
        }
    }

    private List<WriteRequestPb> getVertexWriteRequestPbs(
            List<Vertex> vertices, WriteTypePb writeType) {
        return vertices.stream()
                .map(element -> element.toWriteRequest(writeType))
                .collect(Collectors.toList());
    }

    private List<WriteRequestPb> getEdgeWriteRequestPbs(List<Edge> edges, WriteTypePb writeType) {
        return edges.stream()
                .map(element -> element.toWriteRequest(writeType))
                .collect(Collectors.toList());
    }
}
