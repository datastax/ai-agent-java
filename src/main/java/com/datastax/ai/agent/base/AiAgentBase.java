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
package com.datastax.ai.agent.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import reactor.core.publisher.Flux;


@Configuration
public class AiAgentBase implements AiAgent {

    @Value("classpath:/prompt-templates/system-prompt-qa.txt")
    private Resource systemPrompt;

    private final StreamingChatClient chatClient;

    public static AiAgentBase create(StreamingChatClient chatClient) {
        return new AiAgentBase(chatClient);
    }

    AiAgentBase(StreamingChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Prompt createPrompt(UserMessage userMessage, Map<String,Object> promptProperties) {

        Message systemMessage
                = new SystemPromptTemplate(this.systemPrompt)
                        .createMessage(promptProperties(promptProperties));

        return new Prompt(List.of(systemMessage, userMessage));
    }

    @Override
    public Flux<ChatResponse> send(Prompt prompt) {
        return chatClient.stream(prompt);
    }

    @Override
    public Map<String,Object> promptProperties(Map<String,Object> promptProperties) {
        return new HashMap<>() {{
            putAll(promptProperties);
            put("current_date", java.time.LocalDate.now());
        }};
    }

}