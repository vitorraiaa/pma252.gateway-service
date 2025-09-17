package store.gateway.security;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AuthorizationFilter implements GlobalFilter {

    private Logger logger = LoggerFactory.getLogger(AuthorizationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_BEARER_HEADER = "Bearer";
    private static final String AUTH_SERVICE_TOKEN_SOLVE = "http://auth:8080/auth/solve";

    @Autowired
    private RouterValidator routerValidator;

    @Autowired
    private WebClient.Builder webClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.debug("filter: entrou no filtro de autorizacao");
        ServerHttpRequest request = exchange.getRequest();

        if (!routerValidator.isSecured.test(request)) {
            logger.debug("filter: rota nao eh segura");
            return chain.filter(exchange);
        }
        logger.debug("filter: rota eh segura");

        if (!isAuthMissing(request)) {
            logger.debug("filter: tem [Authorization] no Header");
            String authorization = request.getHeaders().get(AUTHORIZATION_HEADER).get(0);
            logger.debug(String.format(
                "filter: [Authorization]=[%s]",
                authorization
            ));
            String[] parts = authorization.split(" ");
            if (parts.length != 2) {
                logger.debug("filter: bearer token is invalid");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is not well formatted");
            }
            if (!AUTHORIZATION_BEARER_HEADER.equals(parts[0])) {
                logger.debug("filter: bearer token is invalid");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is not well formatted");
            }
            logger.debug("filter: bearer token is formatted");

            final String jwt = parts[1];

            return requestAuthTokenSolve(exchange, chain, jwt);

        }
        logger.debug("filter: access is denied!");
        // if access is denied
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey(AUTHORIZATION_HEADER);
    }
    
    // este metodo eh responsavel por enviar o token ao Auth Microservice
    // a fim de interpretar o token, a chamada eh feita via Rest.
    private Mono<Void> requestAuthTokenSolve(ServerWebExchange exchange, GatewayFilterChain chain, String jwt) {
        logger.debug("solve: solving jwt: " + jwt);
        return webClient
            .defaultHeader(
                HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE
            )
            .build()
            .post()
            .uri(AUTH_SERVICE_TOKEN_SOLVE)
            .bodyValue(Map.of(
                "jwt", jwt)
            )
            .retrieve()
            .toEntity(Map.class)
            .flatMap(response -> {
                if (response != null && response.hasBody() && response.getBody() != null) {
                    final Map<String, String> map = response.getBody();
                    String idAccount = map.get("idAccount");
                    logger.debug("solve: id account: " + idAccount);
                    ServerWebExchange authorizated = updateRequest(exchange, idAccount);
                    return chain.filter(authorizated);
                } else {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
                }
            });
    }

    private ServerWebExchange updateRequest(ServerWebExchange exchange, String idAccount) {
        logger.debug("original headers: " + exchange.getRequest().getHeaders().toString());
        ServerWebExchange modified = exchange.mutate()
            .request(
                exchange.getRequest()
                    .mutate()
                    .header("id-account", idAccount)
                    .build()
            ).build();
        logger.debug("updated headers: " + modified.getRequest().getHeaders().toString());
        return modified;
    }    

}
