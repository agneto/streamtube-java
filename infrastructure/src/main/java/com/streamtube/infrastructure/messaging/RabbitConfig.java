package com.streamtube.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the video-processing exchange/queue with a dead-letter route, and JSON conversion. */
@Configuration
public class RabbitConfig {

  @Bean
  public DirectExchange videoExchange() {
    return new DirectExchange(VideoQueue.EXCHANGE);
  }

  @Bean
  public DirectExchange videoDeadLetterExchange() {
    return new DirectExchange(VideoQueue.DLX);
  }

  @Bean
  public Queue videoQueue() {
    return QueueBuilder.durable(VideoQueue.QUEUE)
        .withArgument("x-dead-letter-exchange", VideoQueue.DLX)
        .withArgument("x-dead-letter-routing-key", VideoQueue.ROUTING_KEY)
        .build();
  }

  @Bean
  public Queue videoDeadLetterQueue() {
    return QueueBuilder.durable(VideoQueue.DLQ).build();
  }

  @Bean
  public Binding videoBinding() {
    return BindingBuilder.bind(videoQueue()).to(videoExchange()).with(VideoQueue.ROUTING_KEY);
  }

  @Bean
  public Binding videoDeadLetterBinding() {
    return BindingBuilder.bind(videoDeadLetterQueue())
        .to(videoDeadLetterExchange())
        .with(VideoQueue.ROUTING_KEY);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
