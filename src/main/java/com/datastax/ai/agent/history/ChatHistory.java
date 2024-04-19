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

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;

import com.fasterxml.uuid.Generators;
import org.springframework.ai.chat.messages.Message;

/**
 * Coming in Spring-AI
 *
 * see https://github.com/spring-projects/spring-ai/pull/536
 */
public interface ChatHistory {

    public record ChatExchange(String sessionId, List<Message> messages, Instant timestamp) {

        public ChatExchange() {
            this(Generators.timeBasedGenerator().generate().toString(), new ArrayList<>(), Instant.now());
        }

        public ChatExchange(String sessionId) {
            this(sessionId, Instant.now());
        }

        public ChatExchange(String sessionId, Instant timestamp) {
            this(sessionId, new ArrayList<>(), timestamp);
        }

    }

    void add(ChatExchange exchange);

    List<ChatExchange> get(String sessionId);

    void clear(String sessionId);

}
