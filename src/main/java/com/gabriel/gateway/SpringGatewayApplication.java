package com.gabriel.gateway;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.security.core.userdetails.User;


@SpringBootApplication
@EnableConfigurationProperties(UriConfiguration.class)
@RestController
public class SpringGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringGatewayApplication.class, args);
	}
	

	@RequestMapping("/fallback")
	public Mono<String> fallback() {
		final Logger logger = LoggerFactory.getLogger(SpringGatewayApplication.class);
		logger.info("Fallback circuit break is active for *.circuitbreaker.com host.");
		return Mono.just("fallback");
	}

	
	@Bean
	public RouteLocator myRoutes(RouteLocatorBuilder builder, UriConfiguration uriConfiguration) {
		String httpUri = uriConfiguration.getHttpbin();
		return builder.routes()
			.route("path_route", p -> p
				.path("/get")
				.filters(f -> f.addRequestHeader("Hello", "World"))
				.uri(httpUri))
			.route("host_route", r -> r.host("*.myhost.org")
					.uri(httpUri))			
			.route("rewrite_route", r -> r.host("*.rewrite.org")
					.filters(f -> f.rewritePath("/foo/(?<segment>.*)", "/${segment}"))
					.uri(httpUri))			
			.route("fallback",p -> p
				.host("*.circuitbreaker.com")
				.filters(f -> f
					.circuitBreaker(config -> config
						.setName("mycmd")
						.setFallbackUri("forward:/fallback")))
				.uri(httpUri))
			.route(r -> r
					.host("*.limited.org")
					.and()
					.path("/anything/**")
					.filters(f -> f.requestRateLimiter(c -> c
							.setRateLimiter(redisRateLimiter())))
					.uri(httpUri))
			.build();
	}

	@Bean
	RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(1, 2);
	}
	
	@Bean
	SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) throws Exception {
		return http.httpBasic().and()
				.csrf().disable()
				.authorizeExchange()
				.pathMatchers("/anything/**").authenticated()
				.anyExchange().permitAll()
				.and()
				.build();
	}
	
	@Bean
	public MapReactiveUserDetailsService reactiveUserDetailsService() {
		UserDetails user = User.withDefaultPasswordEncoder().username("user").password("password").roles("USER").build();
		return new MapReactiveUserDetailsService(user);
	}
	
}


// tag::uri-configuration[]
@ConfigurationProperties
class UriConfiguration {
	
	private String httpbin = "http://httpbin.org:80";

	public String getHttpbin() {
		return httpbin;
	}

	public void setHttpbin(String httpbin) {
		this.httpbin = httpbin;
	}
}
	
	


