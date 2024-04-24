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
package com.datastax.ai.agent.llmCache;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.DocumentIdTranslator;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.PrimaryKeyTranslator;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;

final class AiAgentSessionVectorConfig {

    static final PrimaryKeyTranslator PRIMARY_KEY_TRANSLATOR
        = (pKeyColumns) -> {
            if (pKeyColumns.isEmpty()) {
                return UUID.randomUUID().toString() + "§¶0";
            }
            Preconditions.checkArgument(2 == pKeyColumns.size());

            String sessionId = pKeyColumns.get(0).toString();

            String conversationTs = pKeyColumns.get(1) instanceof Instant
                    ? String.valueOf(((Instant) pKeyColumns.get(1)).toEpochMilli())
                    : (String) pKeyColumns.get(1);

            return sessionId + "§¶" + conversationTs;
        };

    static final DocumentIdTranslator DOCUMENT_ID_TRANSLATOR
            = (id) -> {
                String[] parts = id.split("§¶");
                Preconditions.checkArgument(2 == parts.length);
                String sessionId = parts[0];
                Instant conversationTs = Instant.ofEpochMilli(Long.parseLong(parts[1]));
                return List.of(sessionId, conversationTs);
            };


    static CassandraVectorStore configureAndCreateStore(CqlSession cqlSession, EmbeddingModel embeddingModel) {

        // matches the primary key of the agent_conversations table that
        //  CassandraChatMemoryRepository created in the previous workshop step,
        //  the store then alters the table adding the columns it needs
        return CassandraVectorStore.builder(embeddingModel)
                .session(cqlSession)
                .keyspace("datastax_ai_agent")
                .table("agent_conversations")
                .partitionKeys(List.of(new SchemaColumn("session_id", DataTypes.TEXT)))
                .clusteringKeys(List.of(new SchemaColumn("message_timestamp", DataTypes.TIMESTAMP)))
                .contentColumnName("prompt_request")
                .addMetadataColumn(new SchemaColumn("assistant", DataTypes.TEXT))
                .indexName("agent_conversations_embedding_idx")
                .primaryKeyTranslator(PRIMARY_KEY_TRANSLATOR)
                .documentIdTranslator(DOCUMENT_ID_TRANSLATOR)
                .initializeSchema(true)
                .build();
    }

    private AiAgentSessionVectorConfig() {}
}
