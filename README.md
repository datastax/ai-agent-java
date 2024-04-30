# Build your own Java RAG AI Agent

 ⬅ This is the next workshop step after the [step-6](../workshop-step-6).

## Code, moar code, MOAR CODE

 🤩 This step adds the concept
- Vectorising time windows over time-series data

♻️ And introduces the techniques
- metadata and multiple indexes


 👩‍💻  …

 ℹ️ The chat-memory now stores its messages in a UDT list column, so the cache row itself keeps the assistant's answer in the added `assistant` column, written once the streamed response has completed.


 🔎 To see changes this step introduces use `git diff workshop-step-6..workshop-step-7`.

## Build


 🏃🏿 Run the project like:
```
./mvnw clean spring-boot:run
```


## Ask some questions…

 👩‍💻 Open in a browser http://localhost:8080/import
 and enter the full path to your curr_datausage.csv file

Watch the console for progress on csv rows being imported.


## Next…

 💪🏽 The complete code repository for this workshop can be found at http://github.com/datastax/ai-agent-java




***
![java](./src/assets/java.png) ![vaadin](./src/assets/vaadin.png) ![spring](./src/assets/spring.png) ![tika](./src/assets/tika.jpeg) ![openai](./src/assets/openai.png) ![cassandra](./src/assets/cassandra.png) ![tavily](./src/assets/tavily.jpeg)

***
All work is copyrighted to DataStax, Inc