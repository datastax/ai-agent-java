# AI Agent Starter

Java based using Spring AI, and Vaadin for UIs

This codebase serves as a starter repository for AI Agents.


## Requirements
- OpenAI API key saved as an environment variable `OPENAI_API_KEY`
- Java 21 (or beyond)

## Running the app
Run the project using `./mvnw spring-boot:run` and open [http://localhost:8080](http://localhost:8080) in your browser.

You can also create a GraalVM native image using `./mvnw package -Pnative -Pproduction native:compile` and run the resulting native image.
Note that you must use a Graal JDK in this case.


## Copyright
All work is copyrighted to DataStax, Inc
