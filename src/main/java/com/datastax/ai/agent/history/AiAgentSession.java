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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.datastax.ai.agent.base.AiAgent;
import com.datastax.ai.agent.history.ChatHistory.ChatExchange;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.shaded.guava.common.base.Preconditions;


import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;


public final class AiAgentSession implements AiAgent {

    private static final int CHAT_HISTORY_WINDOW_SIZE = 40;
    
    private final AiAgent agent;
    private final ChatHistoryImpl chatHistory;
    private ChatExchange exchange;

    public static AiAgentSession create(AiAgent agent, CqlSession cqlSession) {
        return new AiAgentSession(agent, cqlSession);
    }

    AiAgentSession(AiAgent agent, CqlSession cqlSession) {
        this.agent = agent;
        this.chatHistory = new ChatHistoryImpl(cqlSession);
        this.exchange = new ChatExchange();
    }

    @Override
    public Prompt createPrompt(UserMessage message, Map<String,Object> promptProperties) {
        Prompt prompt = agent.createPrompt(message, promptProperties(promptProperties));
        exchange = new ChatExchange(exchange.sessionId());
        exchange.messages().add(message);
        chatHistory.add(exchange);
        return prompt;
    }

    @Override
    public Flux<ChatResponse> send(Prompt prompt) {
        
        Preconditions.checkArgument(
                prompt.getInstructions().stream().anyMatch((i) -> exchange.messages().contains(i)),
                "user message in prompt doesn't match");

        Flux<ChatResponse> responseFlux = agent.send(prompt);

        return MessageAggregator.aggregate(
                responseFlux,
                (completedMessage) -> {
                    exchange.messages().add(completedMessage);
                    chatHistory.add(exchange);
                });
    }

    @Override
    public Map<String,Object> promptProperties(Map<String,Object> promptProperties) {

        List<ChatExchange> history = chatHistory.getLastN(exchange.sessionId(), CHAT_HISTORY_WINDOW_SIZE);

        String conversation = history.stream()
                .flatMap(e -> e.messages().stream())
                .map(e -> e.getMessageType().name().toLowerCase() + ": " + e.getContent())
                .collect(Collectors.joining(System.lineSeparator()));

        return new HashMap<>() {{
            putAll(agent.promptProperties(promptProperties));
            put("conversation", conversation);
        }};
    }
}
