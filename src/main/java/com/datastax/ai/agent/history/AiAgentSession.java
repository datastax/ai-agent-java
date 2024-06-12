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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.datastax.ai.agent.base.AiAgent;
import com.datastax.oss.driver.api.core.CqlSession;

import org.springframework.ai.chat.memory.CassandraChatMemory;
import org.springframework.ai.chat.memory.CassandraChatMemoryConfig;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;


public final class AiAgentSession implements AiAgent<Object> {

    public static final String SESSION_ID = AiAgentSession.class.getSimpleName() + "_sessionId";
    public static final String KEYSPACE_NAME = "datastax_ai_agent";
    public static final String TABLE_NAME = "agent_conversations";

    private static final int CHAT_HISTORY_WINDOW_SIZE = 40;
  	private static final int MAX_CONVERSATION_WORDS = 2000;

    private final AiAgent agent;
    private final ChatMemory chatHistory;

    public static AiAgentSession create(AiAgent agent, CqlSession cqlSession) {
        return new AiAgentSession(agent, cqlSession);
    }

    AiAgentSession(AiAgent agent, CqlSession cqlSession) {
        this.agent = agent;

        CassandraChatMemoryConfig memoryConfig = CassandraChatMemoryConfig.builder()
                .withCqlSession(cqlSession)
                .withKeyspaceName(KEYSPACE_NAME)
                .withTableName(TABLE_NAME)
                .build();

        this.chatHistory = CassandraChatMemory.create(memoryConfig);
    }

    @Override
    public Prompt createPrompt(
            UserMessage message,
            Map<String,Object> promptProperties,
            Object chatOptionsBuilder) {

        String sessionId = message.getMetadata().get(SESSION_ID).toString();
        List<Message> history = chatHistory.get(sessionId, CHAT_HISTORY_WINDOW_SIZE);
        AtomicInteger words = new AtomicInteger();

        String conversationStr = history.reversed().stream()
                .filter(msg -> {
                    return MAX_CONVERSATION_WORDS > words.get()
                            ? 0 < words.getAndAdd(new StringTokenizer(msg.getContent()).countTokens())
                            : false;
                })
                .map(msg -> {
                    int cutoff = msg.getContent().length() - (words.get() - MAX_CONVERSATION_WORDS);
                    return msg.getMessageType().name().toLowerCase() + ": " + msg.getContent().substring(0, cutoff);
                })
                .collect(Collectors.joining(System.lineSeparator()));

        promptProperties = new HashMap<>(promptProperties);
        promptProperties.put("conversation", conversationStr);
        message.getMetadata().put(CassandraChatMemory.CONVERSATION_TS, Instant.now());

        return agent.createPrompt(
                message,
                promptProperties(promptProperties),
                chatOptionsBuilder(chatOptionsBuilder));
    }

    @Override
    public Flux<ChatResponse> send(Prompt prompt) {

        UserMessage question = (UserMessage) prompt.getInstructions()
                .stream().filter((m) -> MessageType.USER == m.getMessageType()).findFirst().get();

        Flux<ChatResponse> responseFlux = agent.send(prompt);

        return new MessageAggregator().aggregate(
                responseFlux,
                (answer) -> saveQuestionAnswer(question, answer));
    }

    private void saveQuestionAnswer(UserMessage question, ChatResponse response) {
        String sessionId = question.getMetadata().get(SESSION_ID).toString();
        Instant instant = (Instant) question.getMetadata().get(CassandraChatMemory.CONVERSATION_TS);
        AssistantMessage answer = response.getResult().getOutput();
        answer.getMetadata().put(CassandraChatMemory.CONVERSATION_TS, instant);
        List<Message> conversation = List.of(question, answer);
        chatHistory.add(sessionId, conversation);
    }

}
