# Generic Prompt for Converting Python Trading Agent to Java

## Overview
This prompt is designed to guide an AI language model in converting a Python-based LLM-driven trading agent for Hyperliquid perpetual futures into a working Java implementation. The original Python codebase is an asynchronous trading system that integrates with the Anthropic Claude API for decision-making, computes technical indicators locally, enforces risk management, and interacts with the Hyperliquid exchange via their SDK.

## Key Components of the Python Codebase
The Python code consists of the following main modules:

1. **main.py**: Entry-point script that orchestrates the trading loop, data gathering, LLM decision-making, and trade execution. Uses asyncio for concurrency.

2. **config_loader.py**: Centralized configuration loading from environment variables, supporting JSON, lists, booleans, and integers.

3. **risk_manager.py**: Enforces trading limits including position sizes, leverage, drawdown circuit breakers, and mandatory stop-losses.

4. **agent/decision_maker.py**: Interfaces with Anthropic Claude API for trade decisions, handles tool calling for indicators, and sanitizes outputs.

5. **trading/hyperliquid_api.py**: Async wrapper around Hyperliquid SDK for placing orders, fetching market data, and managing positions.

6. **indicators/local_indicators.py**: Computes technical indicators (EMA, RSI, MACD, ATR, Bollinger Bands, etc.) from OHLCV candle data.

7. **utils/**: Formatting and prompt utilities for JSON serialization and numeric rounding.

## Conversion Requirements
Convert the entire Python codebase to Java, maintaining equivalent functionality. Key considerations:

### Language and Framework Mappings
- **Async/Await (asyncio)**: Replace with Java's CompletableFuture, ExecutorService, or reactive programming (e.g., Reactor or RxJava) for asynchronous operations.
- **HTTP Requests**: Use Java's HttpClient or libraries like OkHttp/Apache HttpClient instead of aiohttp.
- **JSON Handling**: Use Jackson or Gson for JSON parsing/serialization.
- **Logging**: Replace Python's logging with SLF4J/Logback.
- **Configuration**: Use Java Properties, YAML with SnakeYAML, or environment variable loading similar to Python.
- **Dependencies**: Identify Java equivalents for:
  - Anthropic API: Use Anthropic's Java SDK or REST client.
  - Hyperliquid SDK: Find or create Java bindings for Hyperliquid's API/SDK.
  - Technical Indicators: Implement or use libraries like TA4J or implement custom calculations.
- **Data Structures**: Convert Python dicts to Java Maps/POJOs, lists to ArrayList, etc.
- **Exception Handling**: Maintain similar error handling patterns.
- **Concurrency**: Use Java threads, ExecutorService, or reactive streams for parallel data fetching.

### Architecture Adjustments
- **Main Loop**: Convert the asyncio-based main loop to a Java main method with scheduled executors or reactive streams.
- **LLM Integration**: Adapt the Claude API calls to Java, handling streaming responses and tool calling.
- **Risk Management**: Port the risk checks to Java classes with similar logic.
- **Indicator Computation**: Translate the mathematical computations to Java methods, ensuring numerical precision.
- **API Wrappers**: Create Java classes that wrap Hyperliquid's REST/WebSocket APIs with retry logic.

### Specific Module Conversions
1. **ConfigLoader.java**: Create a class to load configuration from environment variables, supporting type conversion (int, boolean, JSON, lists).

2. **RiskManager.java**: Implement risk validation logic with methods for checking position sizes, leverage, drawdown, etc.

3. **TradingAgent.java**: Handle LLM interactions, prompt construction, response parsing, and tool calling.

4. **HyperliquidApi.java**: Provide methods for order placement, data fetching, and state management using Java HTTP clients.

5. **LocalIndicators.java**: Implement indicator calculation functions (EMA, RSI, MACD, etc.) with Java math libraries.

6. **Utils Classes**: Create formatting and utility classes for JSON defaults, rounding, etc.

7. **Main.java**: Orchestrate the trading loop using Java concurrency primitives.

### Build and Dependencies
- Use Maven or Gradle for dependency management.
- Include dependencies for HTTP clients, JSON processing, logging, and any required SDKs.
- Ensure the Java version supports modern features (Java 11+ recommended).

### Testing and Validation
- Port any existing logic for testing trade decisions, indicator calculations, and API interactions.
- Ensure numerical outputs match Python implementations for indicators and calculations.

### Output Structure
Organize the Java code in a similar package structure:
- `src/main/java/com/hyperliquid/tradingagent/`
  - `Main.java`
  - `config/ConfigLoader.java`
  - `risk/RiskManager.java`
  - `agent/TradingAgent.java`
  - `trading/HyperliquidApi.java`
  - `indicators/LocalIndicators.java`
  - `utils/`

Provide the converted Java code as complete, compilable classes with proper imports, error handling, and documentation.

## Python Code to Convert
[Attach or reference the full Python codebase here, including all files from main.py, config_loader.py, risk_manager.py, agent/decision_maker.py, trading/hyperliquid_api.py, indicators/local_indicators.py, and utils/*.py]

## Instructions for AI
1. Analyze each Python module and identify Java equivalents.
2. Convert async functions to Java's asynchronous patterns.
3. Ensure type safety and add generics where appropriate.
4. Maintain logging and error handling.
5. Test numerical computations for accuracy.
6. Provide the full Java codebase in the specified structure.</content>
<parameter name="filePath">/Users/vinay/Data/Code/Java/hyperliquid-trading-agent/java/ConvertToJava.md
