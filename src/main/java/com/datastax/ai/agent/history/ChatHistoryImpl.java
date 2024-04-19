/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 */
package com.datastax.ai.agent.history;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.ListType;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.delete.DeleteSelection;
import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumn;
import com.datastax.oss.driver.api.querybuilder.schema.AlterTableAddColumnEnd;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.internal.core.type.DefaultListType;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

final class ChatHistoryImpl implements ChatHistory {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryImpl.class);

    private final CqlSession session;
    private final Config config;

    public static ChatHistoryImpl create(CqlSession session) {
        return new ChatHistoryImpl(session);
    }

    ChatHistoryImpl(CqlSession session) {
        this.session = session;
        this.config = new Config(session);
    }

    @Override
    public void add(ChatExchange exchange) {
        List<Object> primaryKeyValues = config.chatExchangeToPrimaryKeyTranslator.apply(exchange);

        BoundStatementBuilder builder = config.addStmt.boundStatementBuilder();
        for (int k = 0; k < primaryKeyValues.size(); ++k) {
            Config.SchemaColumn keyColumn = config.getPrimaryKeyColumn(k);
            builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
        }

        builder = builder.setList(
                config.schema.messages(),
                exchange.messages().stream().map((msg) -> msg.getContent()).toList(),
                String.class);

        session.execute(builder.build());
    }

    @Override
    public List<ChatExchange> get(String sessionId) {
        return getLastN(sessionId, Integer.MAX_VALUE);
    }

    @Override
    public void clear(String sessionId) {
        ChatExchange dummy = new ChatExchange(sessionId);
        List<Object> primaryKeyValues = config.chatExchangeToPrimaryKeyTranslator.apply(dummy);
        BoundStatementBuilder builder = config.deleteStmt.boundStatementBuilder();
        for (int k = 0; k < primaryKeyValues.size(); ++k) {
            Config.SchemaColumn keyColumn = config.getPrimaryKeyColumn(k);
            builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
        }
        session.execute(builder.build());
    }

    List<ChatExchange> getLastN(String sessionId, int lastN) {
        ChatExchange dummy = new ChatExchange(sessionId);
        List<Object> primaryKeyValues = config.chatExchangeToPrimaryKeyTranslator.apply(dummy);

        BoundStatementBuilder builder = config.getStatement.boundStatementBuilder();
        for (int k = 0; k < primaryKeyValues.size(); ++k) {
            Config.SchemaColumn keyColumn = config.getPrimaryKeyColumn(k);
            // TODO make compatible with configurable ChatExchangeToPrimaryKeyTranslator
            // this assumes there's only one clustering key (for the chatExchange timestamp)
            if (!Config.DEFAULT_EXCHANGE_ID_NAME.equals(keyColumn.name())) {
                builder = builder.set(keyColumn.name(), primaryKeyValues.get(k), keyColumn.javaType());
            }
        }
        builder = builder.setInt("lastN", lastN);
        List<ChatExchange> exchanges = new ArrayList<>();
        for (Row r : session.execute(builder.build())) {
            List<String> msgs = r.getList(Config.DEFAULT_MESSAGES_COLUMN_NAME, String.class);
            exchanges.add(
                    new ChatExchange(
                            r.getUuid(Config.DEFAULT_SESSION_ID_NAME).toString(),
                            List.of(
                                    new UserMessage(msgs.get(0)),
                                    new AssistantMessage(msgs.get(1))),
                            r.get(Config.DEFAULT_EXCHANGE_ID_NAME, Instant.class)));
        }
        return exchanges;
    }

    public static class Config {

        record Schema(
                String keyspace,
                String table,
                List<SchemaColumn> partitionKeys,
                List<SchemaColumn> clusteringKeys,
                String messages) {

        }

        record SchemaColumn(String name, DataType type) {
            GenericType<Object> javaType() {
                return CodecRegistry.DEFAULT.codecFor(type).getJavaType();
            }
        }

        public interface ChatExchangeToPrimaryKeyTranslator extends Function<ChatExchange, List<Object>> {}

        public interface PrimaryKeyToChatExchangeTranslator extends Function<List<Object>, ChatExchange> {}

        public static final String DEFAULT_KEYSPACE_NAME = "datastax_ai_agent";
        public static final String DEFAULT_TABLE_NAME = "agent_conversations";
        public static final String DEFAULT_SESSION_ID_NAME = "session_id";
        public static final String DEFAULT_EXCHANGE_ID_NAME = "exchange_timestamp";
        public static final String DEFAULT_MESSAGES_COLUMN_NAME = "messages";

        private static final ListType DEFAULT_MESSAGES_COLUMN_TYPE = new DefaultListType(DataTypes.TEXT, true);

        private final CqlSession session;

        private final Schema schema = new Schema(
                DEFAULT_KEYSPACE_NAME,
                DEFAULT_TABLE_NAME,
                List.of(new SchemaColumn(DEFAULT_SESSION_ID_NAME, DataTypes.TIMEUUID)),
                List.of(new SchemaColumn(DEFAULT_EXCHANGE_ID_NAME, DataTypes.TIMESTAMP)),
                DEFAULT_MESSAGES_COLUMN_NAME);

        private final ChatExchangeToPrimaryKeyTranslator chatExchangeToPrimaryKeyTranslator
                = (e) -> List.of(UUID.fromString(e.sessionId()), e.timestamp());

        private final PrimaryKeyToChatExchangeTranslator primaryKeyToChatExchangeTranslator
                = (primaryKeys)
                -> new ChatExchange(primaryKeys.get(0).toString(), (Instant) primaryKeys.get(1));

        private final boolean disallowSchemaChanges = false;

        private final PreparedStatement addStmt, getStatement, deleteStmt;

        Config(CqlSession session) {
            this.session = session;
            ensureSchemaExists();
            addStmt = prepareAddStmt();
            getStatement = prepareGetStatement();
            deleteStmt = prepareDeleteStmt();
        }

        private SchemaColumn getPrimaryKeyColumn(int index) {
            return index < this.schema.partitionKeys().size()
                    ? this.schema.partitionKeys().get(index)
                    : this.schema.clusteringKeys().get(index - this.schema.partitionKeys().size());
        }

        private void ensureSchemaExists() {
            if (!disallowSchemaChanges) {
                ensureKeyspaceExists();
                ensureTableExists();
                ensureTableColumnsExist();
                checkSchemaAgreement();
            } else {
                checkSchemaValid();
            }
        }

        private void checkSchemaAgreement() throws IllegalStateException {
            if (!session.checkSchemaAgreement()) {
                logger.warn("Waiting for cluster schema agreement, sleeping 10s…");
                try {
                    Thread.sleep(Duration.ofSeconds(10).toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                if (!session.checkSchemaAgreement()) {
                    logger.error("no cluster schema agreement still, continuing, let's hope this works…");
                }
            }
        }

        void checkSchemaValid() {

            Preconditions.checkState(session.getMetadata().getKeyspace(schema.keyspace).isPresent(),
                    "keyspace %s does not exist", schema.keyspace);

            Preconditions.checkState(session.getMetadata()
                    .getKeyspace(schema.keyspace)
                    .get()
                    .getTable(schema.table)
                    .isPresent(), "table %s does not exist");

            TableMetadata tableMetadata = session.getMetadata()
                    .getKeyspace(schema.keyspace)
                    .get()
                    .getTable(schema.table)
                    .get();

            Preconditions.checkState(tableMetadata.getColumn(schema.messages).isPresent(), "column %s does not exist",
                    schema.messages);

        }

        private void ensureKeyspaceExists() {

            SimpleStatement keyspaceStmt = SchemaBuilder.createKeyspace(schema.keyspace)
                    .ifNotExists()
                    .withSimpleStrategy(1)
                    .build();

            logger.debug("Executing {}", keyspaceStmt.getQuery());
            session.execute(keyspaceStmt);
        }

        private void ensureTableExists() {

            CreateTable createTable = null;

            CreateTableStart createTableStart = SchemaBuilder.createTable(schema.keyspace, schema.table)
                    .ifNotExists();

            for (SchemaColumn partitionKey : schema.partitionKeys) {
                createTable = (null != createTable ? createTable : createTableStart).withPartitionKey(partitionKey.name,
                        partitionKey.type);
            }
            for (SchemaColumn clusteringKey : schema.clusteringKeys) {
                createTable = createTable.withClusteringColumn(clusteringKey.name, clusteringKey.type);
            }

            createTable = createTable.withColumn(schema.messages, DEFAULT_MESSAGES_COLUMN_TYPE);

            session.execute(
                    createTable.withClusteringOrder(DEFAULT_EXCHANGE_ID_NAME, ClusteringOrder.DESC)
                            // set this if you want sessions to expire after a period of time
                            // TODO create option, and append TTL value to select queries (performance)
                            //.withDefaultTimeToLiveSeconds((int) Duration.ofDays(120).toSeconds())

                            // TODO replace when SchemaBuilder.unifiedCompactionStrategy() becomes available
                            .withOption("compaction", Map.of("class", "UnifiedCompactionStrategy"))
                            //.withCompaction(SchemaBuilder.unifiedCompactionStrategy()))
                            .build());
        }

        private void ensureTableColumnsExist() {

            TableMetadata tableMetadata = session.getMetadata()
                    .getKeyspace(schema.keyspace())
                    .get()
                    .getTable(schema.table())
                    .get();

            boolean addContent = tableMetadata.getColumn(schema.messages()).isEmpty();

            if (addContent) {
                AlterTableAddColumn alterTable = SchemaBuilder
                        .alterTable(schema.keyspace(), schema.table())
                        .addColumn(schema.messages(), DEFAULT_MESSAGES_COLUMN_TYPE);

                SimpleStatement stmt = ((AlterTableAddColumnEnd) alterTable).build();
                logger.debug("Executing {}", stmt.getQuery());
                session.execute(stmt);
            }
        }

        private PreparedStatement prepareAddStmt() {
            RegularInsert stmt = null;
            InsertInto stmtStart = QueryBuilder.insertInto(schema.keyspace(), schema.table());
            for (var c : schema.partitionKeys()) {
                stmt = (null != stmt ? stmt : stmtStart)
                        .value(c.name(), QueryBuilder.bindMarker(c.name()));
            }
            for (var c : schema.clusteringKeys()) {
                stmt = stmt.value(c.name(), QueryBuilder.bindMarker(c.name()));
            }
            stmt = stmt.value(schema.messages(), QueryBuilder.bindMarker(schema.messages()));
            return session.prepare(stmt.build());
        }

        private PreparedStatement prepareGetStatement() {
            Select stmt = QueryBuilder.selectFrom(schema.keyspace, schema.table).all();
            // TODO make compatible with configurable ChatExchangeToPrimaryKeyTranslator
            // this assumes there's only one clustering key (for the chatExchange timestamp)
            for (var c : schema.partitionKeys()) {
                stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
            }
            stmt = stmt.limit(QueryBuilder.bindMarker("lastN"));
            return session.prepare(stmt.build());
        }

        private PreparedStatement prepareDeleteStmt() {
            Delete stmt = null;
            DeleteSelection stmtStart = QueryBuilder.deleteFrom(schema.keyspace, schema.table);
            for (var c : schema.partitionKeys()) {
                stmt = (null != stmt ? stmt : stmtStart)
                        .whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
            }
            for (var c : schema.clusteringKeys()) {
                stmt = stmt.whereColumn(c.name()).isEqualTo(QueryBuilder.bindMarker(c.name()));
            }
            return session.prepare(stmt.build());
        }

    }

}
