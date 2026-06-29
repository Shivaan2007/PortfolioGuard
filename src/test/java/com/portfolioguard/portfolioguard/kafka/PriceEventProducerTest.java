package com.portfolioguard.portfolioguard.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriceEventProducerTest {

    @Mock KafkaTemplate<String, PriceUpdateEvent> kafkaTemplate;
    @InjectMocks PriceEventProducer producer;

    @Test
    void publishPriceUpdate_sendsEventToKafka() {
        producer.publishPriceUpdate("AAPL", 140.0, 150.0, "p1");

        ArgumentCaptor<PriceUpdateEvent> captor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(kafkaTemplate).send(eq("price-updates"), eq("AAPL"), captor.capture());

        PriceUpdateEvent event = captor.getValue();
        assertThat(event.getTicker()).isEqualTo("AAPL");
        assertThat(event.getOldPrice()).isEqualTo(140.0);
        assertThat(event.getNewPrice()).isEqualTo(150.0);
        assertThat(event.getPortfolioId()).isEqualTo("p1");
    }

    @Test
    void publishPriceUpdate_calculatesChangePercent() {
        producer.publishPriceUpdate("AAPL", 100.0, 110.0, "p1");

        ArgumentCaptor<PriceUpdateEvent> captor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(kafkaTemplate).send(eq("price-updates"), eq("AAPL"), captor.capture());

        assertThat(captor.getValue().getChangePercent()).isEqualTo(10.0);
    }

    @Test
    void publishPriceUpdate_negativePriceChange_negativePercent() {
        producer.publishPriceUpdate("TSLA", 200.0, 180.0, "p1");

        ArgumentCaptor<PriceUpdateEvent> captor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(kafkaTemplate).send(eq("price-updates"), eq("TSLA"), captor.capture());

        assertThat(captor.getValue().getChangePercent()).isEqualTo(-10.0);
    }

    @Test
    void publishPriceUpdate_oldPriceZero_doesNotThrowDivisionByZero() {
        // Bug fix: oldPrice = 0 used to cause ArithmeticException / Infinity
        assertThatCode(() -> producer.publishPriceUpdate("NEW", 0.0, 50.0, "p1"))
                .doesNotThrowAnyException();

        ArgumentCaptor<PriceUpdateEvent> captor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(kafkaTemplate).send(eq("price-updates"), eq("NEW"), captor.capture());
        assertThat(captor.getValue().getChangePercent()).isEqualTo(0.0);
    }

    @Test
    void publishPriceUpdate_setsTimestamp() {
        producer.publishPriceUpdate("AAPL", 140.0, 150.0, "p1");

        ArgumentCaptor<PriceUpdateEvent> captor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(kafkaTemplate).send(eq("price-updates"), eq("AAPL"), captor.capture());
        assertThat(captor.getValue().getTimestamp()).isNotNull();
    }
}
