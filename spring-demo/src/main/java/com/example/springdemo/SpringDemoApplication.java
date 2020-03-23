package com.example.springdemo;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.example.types.SpringNews;
import com.fasterxml.jackson.databind.JsonNode;
import com.springdeveloper.support.cloudevents.CloudEventMapper;
import com.example.types.SpringEvent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;

@SpringBootApplication
public class SpringDemoApplication {

	@Bean
	public Function<Message<JsonNode>, Message<SpringNews>> event() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringEvent> cloudEvent = CloudEventMapper.convert(in, SpringEvent.class);
			String results = "EVENT: " + cloudEvent.getData();
			System.out.println(results);
			// create return CloudEvent
			SpringEvent event = cloudEvent.getData().get();
			Map<String, Object> headerMap = new HashMap<>();
			headerMap.put("ce-specversion", "1.0");
			headerMap.put("ce-type", "com.example.springnews");
			headerMap.put("ce-source", "spring.io/spring-news");
			headerMap.put("ce-id", cloudEvent.getAttributes().getId());
			MessageHeaders headers = new MessageHeaders(headerMap);
			SpringNews news = new SpringNews();
			news.setWhen(new Date());
			news.setHeadline(event.getReleaseName() + " " + event.getVersion() + " Released");
			Locale loc = new Locale("en", "US");
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, loc);
			String releaseDate = dateFormat.format(event.getReleaseDate());
			String copy = event.getReleaseName() + " version " + event.getVersion() + " was released on " + releaseDate;
			news.setCopy(copy);
			return MessageBuilder.createMessage(news, headers);
		};
	}

	@Bean
	public Function<Message<JsonNode>, String> news() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringNews> cloudEvent = CloudEventMapper.convert(in, SpringNews.class);
			String results = "NEWS: " + cloudEvent.getData();
			System.out.println(results);
			return "OK";
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringDemoApplication.class, args);
	}

}
