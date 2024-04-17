# Build your own Java RAG AI Agent

 ⬅ This is the next workshop step after the [requirements step](../workshop-intro-requirements).

## Code, moar code, MOAR CODE

 🤩 The step introduces the initial basic concepts of an AI Agent
- LLM requests, and 
- Prompt Engineering

 
 ♻️ This step introduces the following technologies and techniques
- Java 21
- Maven build system
- OpenAI
- Spring-AI for GenAI and RAG framework,
- Spring Boot for simple http server and IoC containerisation,
- Vaadin for simple java-based web UI
- Decorator and Delegation pattern to "chain" AI capabilities together.


## Configure and Build

 🐢 Configure the project like:
```
cp credentials.txt.sample credentials.txt

open credentials.txt   # fill in your api keys; the file is gitignored so they are never committed

source credentials.txt
```

 ☕️ Build the project like:
```
./mvnw clean install
```

 🏃🏿 Run the project like:
```
./mvnw clean spring-boot:run
```

 🧙🏻‍♀️ If you need or want to debug the project, do:
```
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
```

## Ask some questions…

 👩‍💻 Open in a browser http://localhost:8080
 and ask your chatbot some questions.

It's pretty limited, the application remains stateless and has a static prompt.

 🔍 Explore where these limitations in its answers are.

 ℹ️ The prompt being sent to OpenAI is logged as `info` in your terminal.

## Next… 

 💪🏽 To move on to [step-1](../workshop-step-1) do the following:
```
git switch workshop-step-1
```



***
![java](./src/assets/java.png) ![vaadin](./src/assets/vaadin.png) ![spring](./src/assets/spring.png) ![tika](./src/assets/tika.jpeg) ![openai](./src/assets/openai.png) ![cassandra](./src/assets/cassandra.png) ![tavily](./src/assets/tavily.jpeg)

*** 
All work is copyrighted to DataStax, Inc
