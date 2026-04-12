package com.ai.tradingagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.tradingagent.agent.TradingAgent;
import com.ai.tradingagent.config.ConfigLoader;
import com.ai.tradingagent.indicators.LocalIndicators;
import com.ai.tradingagent.risk.RiskManager;
import com.ai.tradingagent.trading.CoinDCXApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Entry-point script that wires together the trading agent, data feeds, and API.
 */
@SpringBootApplication
@EnableScheduling
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

}
