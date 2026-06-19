package com.example.BackendArchitectureLab.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiAggregatorController {

    private static final Map<String, String> SERVICE_PATHS = Map.of(
        "iam-service", "/api-docs/BackendArchitectureLab",
        "project-skill-service", "/v3/api-docs/BackendArchitectureLab",
        "job-service", "/v3/api-docs/BackendArchitectureLab",
        "ai-service", "/v3/api-docs/BackendArchitectureLab",
        "alert-service", "/v3/api-docs/BackendArchitectureLab"
    );

    private static final Map<String, String> SERVICE_PREFIXES = Map.of(
        "iam-service", "/api",
        "project-skill-service", "/api",
        "job-service", "/api",
        "ai-service", "/api",
        "alert-service", "/api"
    );

    private static final Logger log = LoggerFactory.getLogger(OpenApiAggregatorController.class);
    private static final ObjectMapper swaggerMapper = Json.mapper();

    @Autowired
    private DiscoveryClient discoveryClient;
    private final WebClient webClient = WebClient.create();

    @GetMapping(value = "/v3/api-docs-merged", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getMergedOpenApi() {
        List<Mono<OpenAPI>> fetches = new ArrayList<>();
        for (var entry : SERVICE_PATHS.entrySet()) {
            String serviceName = entry.getKey();
            String path = entry.getValue();
            var instances = discoveryClient.getInstances(serviceName);
            if (!instances.isEmpty()) {
                var instance = instances.getFirst();
                String url = "http://" + instance.getHost() + ":" + instance.getPort() + path;
                log.info("Fetching OpenAPI spec from {}: {}", serviceName, url);
                fetches.add(webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(json -> {
                        try {
                            OpenAPI spec = swaggerMapper.readValue(json, OpenAPI.class);
                            String prefix = SERVICE_PREFIXES.getOrDefault(serviceName, "");
                            if (!prefix.isEmpty() && spec.getPaths() != null) {
                                Paths prefixedPaths = new Paths();
                                spec.getPaths().forEach((pathKey, item) ->
                                    prefixedPaths.addPathItem(prefix + pathKey, item));
                                spec.setPaths(prefixedPaths);
                            }
                            return spec;
                        }
                        catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(spec -> log.info("Successfully fetched spec from {}", serviceName))
                    .doOnError(e -> log.warn("Failed to fetch spec from {}: {}", serviceName, e.getMessage()))
                    .onErrorResume(e -> Mono.empty()));
            } else {
                log.warn("No instances found for service: {}", serviceName);
            }
        }
        if (fetches.isEmpty()) {
            return Mono.just(serializeApi(createDefaultApi()));
        }
        return Flux.merge(fetches)
            .reduce(new OpenAPI(), (merged, spec) -> {
                merge(merged, spec);
                return merged;
            })
            .map(merged -> {
                if (merged.getInfo() == null) {
                    merged.setInfo(new Info().title("BackendArchitectureLab").version("1.0.0"));
                }
                if (merged.getPaths() == null) {
                    merged.setPaths(new Paths());
                }
                if (merged.getSecurity() != null && !merged.getSecurity().isEmpty()
                        && (merged.getComponents() == null || merged.getComponents().getSecuritySchemes() == null)) {
                    if (merged.getComponents() == null) {
                        merged.setComponents(new Components());
                    }
                    merged.getComponents().addSecuritySchemes("Bearer Authentication",
                            new io.swagger.v3.oas.models.security.SecurityScheme()
                                    .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT"));
                }
                return serializeApi(merged);
            });
    }

    private OpenAPI createDefaultApi() {
        return new OpenAPI()
            .info(new Info().title("BackendArchitectureLab").version("1.0.0"))
            .paths(new Paths());
    }

    private String serializeApi(OpenAPI api) {
        try {
            return swaggerMapper.writeValueAsString(api);
        } catch (Exception e) {
            log.error("Failed to serialize merged OpenAPI spec", e);
            return "{}";
        }
    }

    private void merge(OpenAPI target, OpenAPI source) {
        if (source.getInfo() != null && target.getInfo() == null) {
            target.setInfo(source.getInfo());
        }
        if (source.getPaths() != null) {
            if (target.getPaths() == null) {
                target.setPaths(new Paths());
            }
            target.getPaths().putAll(source.getPaths());
        }
        if (source.getComponents() != null) {
            if (target.getComponents() == null) {
                target.setComponents(new Components());
            }
            if (source.getComponents().getSchemas() != null) {
                if (target.getComponents().getSchemas() == null) {
                    target.getComponents().setSchemas(new LinkedHashMap<>());
                }
                target.getComponents().getSchemas().putAll(source.getComponents().getSchemas());
            }
            if (source.getComponents().getSecuritySchemes() != null) {
                if (target.getComponents().getSecuritySchemes() == null) {
                    target.getComponents().setSecuritySchemes(new LinkedHashMap<>());
                }
                target.getComponents().getSecuritySchemes().putAll(source.getComponents().getSecuritySchemes());
            }
            if (source.getComponents().getRequestBodies() != null) {
                if (target.getComponents().getRequestBodies() == null) {
                    target.getComponents().setRequestBodies(new LinkedHashMap<>());
                }
                target.getComponents().getRequestBodies().putAll(source.getComponents().getRequestBodies());
            }
            if (source.getComponents().getResponses() != null) {
                if (target.getComponents().getResponses() == null) {
                    target.getComponents().setResponses(new LinkedHashMap<>());
                }
                target.getComponents().getResponses().putAll(source.getComponents().getResponses());
            }
            if (source.getComponents().getParameters() != null) {
                if (target.getComponents().getParameters() == null) {
                    target.getComponents().setParameters(new LinkedHashMap<>());
                }
                target.getComponents().getParameters().putAll(source.getComponents().getParameters());
            }
            if (source.getComponents().getHeaders() != null) {
                if (target.getComponents().getHeaders() == null) {
                    target.getComponents().setHeaders(new LinkedHashMap<>());
                }
                target.getComponents().getHeaders().putAll(source.getComponents().getHeaders());
            }
            if (source.getComponents().getLinks() != null) {
                if (target.getComponents().getLinks() == null) {
                    target.getComponents().setLinks(new LinkedHashMap<>());
                }
                target.getComponents().getLinks().putAll(source.getComponents().getLinks());
            }
            if (source.getComponents().getCallbacks() != null) {
                if (target.getComponents().getCallbacks() == null) {
                    target.getComponents().setCallbacks(new LinkedHashMap<>());
                }
                target.getComponents().getCallbacks().putAll(source.getComponents().getCallbacks());
            }
            if (source.getComponents().getExtensions() != null) {
                if (target.getComponents().getExtensions() == null) {
                    target.getComponents().setExtensions(new LinkedHashMap<>());
                }
                target.getComponents().getExtensions().putAll(source.getComponents().getExtensions());
            }
        }
        if (source.getTags() != null) {
            if (target.getTags() == null) {
                target.setTags(new ArrayList<>());
            }
            for (var tag : source.getTags()) {
                if (target.getTags().stream().noneMatch(t -> t.getName().equals(tag.getName()))) {
                    target.getTags().add(tag);
                }
            }
        }
        if (source.getSecurity() != null) {
            if (target.getSecurity() == null) {
                target.setSecurity(new ArrayList<>());
            }
            target.getSecurity().addAll(source.getSecurity());
        }
        if (source.getExtensions() != null) {
            if (target.getExtensions() == null) {
                target.setExtensions(new LinkedHashMap<>());
            }
            target.getExtensions().putAll(source.getExtensions());
        }
    }
}
