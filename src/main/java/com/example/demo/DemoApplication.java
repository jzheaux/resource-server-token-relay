package com.example.demo;

import java.time.Instant;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class DemoApplication {

	@RestController
	static class HelloController {
		@Autowired
		WebClient rest;

		@GetMapping("/hello/{name}")
		public String hello(@PathVariable(name="name") String name) {
			return "Hello, " + name + "!";
		}

		@GetMapping("/hello-hello-hello")
		public String helloHelloHello() {
			return this.rest.get()
					.uri("http://localhost:8080/hello/one")
					.retrieve()
					.bodyToMono(String.class)
					.flatMap(result -> rest.get()
							.uri("http://localhost:8080/hello/two")
							.retrieve()
							.bodyToMono(String.class)
							.flatMap(r -> rest.get()
									.uri("http://localhost:8080/hello/three")
									.retrieve()
									.bodyToMono(String.class)))
					.block();

		}
	}

	@Bean
	ExchangeFilterFunction bearer() {
		return new ServletBearerExchangeFilterFunction();
	}

	@Bean
	WebClient rest(ExchangeFilterFunction bearer) {
		return WebClient.builder()
				.filter(bearer)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder() {
		return token -> new Jwt(token,
				Instant.now(), Instant.now().plusSeconds(86400),
				Collections.singletonMap("alg", "none"),
				Collections.singletonMap("sub", "bob"));
	}

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
