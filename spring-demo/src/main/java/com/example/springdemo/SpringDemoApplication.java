package com.example.springdemo;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import com.example.types.SpringEvent;
import com.example.types.SpringNews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootApplication
public class SpringDemoApplication {

	@Bean
	public Function<Message<SpringEvent>, Message<SpringNews>> event() {
		return (in) -> {
			SpringEvent event = in.getPayload();
			System.out.println("EVENT: " + event);
			// create return CloudEvent
			SpringNews news = new SpringNews();
			news.setWhen(new Date());
			news.setHeadline(event.getReleaseName() + " " + event.getVersion() + " Released");
			Locale loc = new Locale("en", "US");
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, loc);
			String releaseDate = dateFormat.format(event.getReleaseDate());
			String copy = event.getReleaseName() + " version " + event.getVersion() + " was released on " + releaseDate;
			news.setCopy(copy);
			return MessageBuilder.withPayload(news)
					.setHeader("ce-specversion", "1.0")
					.setHeader("ce-type", "com.example.springnews")
					.setHeader("ce-source", "spring.io/spring-news")
					.setHeader("ce-id", in.getHeaders().get("ce-id"))
					.build();
		};
	}

	@Bean
	public Consumer<Message<SpringNews>> news() {
		return (in) -> {
			SpringNews news = in.getPayload();
			System.out.println("NEWS: " + news);
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringDemoApplication.class, args);
	}

}
