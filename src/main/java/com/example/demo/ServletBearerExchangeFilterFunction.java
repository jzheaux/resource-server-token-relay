package com.example.demo;

import java.util.function.Consumer;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * An {@link ExchangeFilterFunction} that adds the
 * <a href="https://tools.ietf.org/html/rfc6750#section-1.2" target="_blank">Bearer Token</a>
 * from an existing {@link AbstractOAuth2Token} tied to the current {@link Authentication}.
 *
 * Suitable for Servlet applications, applying it to a typical {@link org.springframework.web.reactive.function.client.WebClient}
 * configuration:
 *
 * <pre>
 *  @Bean
 *  WebClient webClient() {
 *      ServletBearerExchangeFilterFunction bearer = new ServletBearerExchangeFilterFunction();
 *      return WebClient.builder()
 *              .filter(bearer).build();
 *  }
 * </pre>
 *
 * @author Josh Cummings
 * @since 5.2
 */
public final class ServletBearerExchangeFilterFunction
		implements ExchangeFilterFunction, InitializingBean, DisposableBean {

	private static final String REQUEST_CONTEXT_OPERATOR_KEY = RequestContextSubscriber.class.getName();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		Hooks.onLastOperator(REQUEST_CONTEXT_OPERATOR_KEY,
				Operators.liftPublisher((s, sub) -> createRequestContextSubscriber(sub)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy() throws Exception {
		Hooks.resetOnLastOperator(REQUEST_CONTEXT_OPERATOR_KEY);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
		return oauth2Token()
				.map(token -> bearer(request, token))
				.defaultIfEmpty(request)
				.flatMap(next::exchange);
	}

	private Mono<AbstractOAuth2Token> oauth2Token() {
		return Mono.subscriberContext()
				.flatMap(ctx -> currentAuthentication(ctx))
				.filter(authentication -> authentication.getCredentials() instanceof AbstractOAuth2Token)
				.map(Authentication::getCredentials)
				.cast(AbstractOAuth2Token.class);
	}

	private Mono<Authentication> currentAuthentication(Context ctx) {
		RequestContextDataHolder holder = RequestContextSubscriber.getRequestContext(ctx);
		if (holder == null) {
			return Mono.empty();
		}

		return Mono.justOrEmpty(holder.getAuthentication());
	}

	private ClientRequest bearer(ClientRequest request, AbstractOAuth2Token token) {
		return ClientRequest.from(request)
				.headers(headers -> headers.setBearerAuth(token.getTokenValue()))
				.build();
	}

	private <T> CoreSubscriber<T> createRequestContextSubscriber(CoreSubscriber<T> delegate) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return new RequestContextSubscriber<>(delegate, authentication);
	}

	private static class RequestContextDataHolder {
		private final Authentication authentication;

		RequestContextDataHolder(Authentication authentication) {
			this.authentication = authentication;
		}

		public Authentication getAuthentication() {
			return this.authentication;
		}
	}

	private static class RequestContextSubscriber<T> implements CoreSubscriber<T> {
		private static final String REQUEST_CONTEXT_DATA_HOLDER_ATTR_NAME =
				RequestContextSubscriber.class.getName().concat(".REQUEST_CONTEXT_DATA_HOLDER");

		private CoreSubscriber<T> delegate;
		private final Context context;

		private RequestContextSubscriber(CoreSubscriber<T> delegate,
				Authentication authentication) {

			this.delegate = delegate;
			Context parentContext = this.delegate.currentContext();
			Context context;
			if (authentication == null || parentContext.hasKey(REQUEST_CONTEXT_DATA_HOLDER_ATTR_NAME)) {
				context = parentContext;
			} else {
				context = parentContext.put(REQUEST_CONTEXT_DATA_HOLDER_ATTR_NAME,
						new RequestContextDataHolder(authentication));
			}

			this.context = context;
		}

		@Nullable
		static RequestContextDataHolder getRequestContext(Context ctx) {
			return ctx.getOrDefault(REQUEST_CONTEXT_DATA_HOLDER_ATTR_NAME, null);
		}

		@Override
		public Context currentContext() {
			return this.context;
		}

		@Override
		public void onSubscribe(Subscription s) {
			this.delegate.onSubscribe(s);
		}

		@Override
		public void onNext(T t) {
			this.delegate.onNext(t);
		}

		@Override
		public void onError(Throwable t) {
			this.delegate.onError(t);
		}

		@Override
		public void onComplete() {
			this.delegate.onComplete();
		}
	}
}

