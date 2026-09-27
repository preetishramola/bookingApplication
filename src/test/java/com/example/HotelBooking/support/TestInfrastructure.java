package com.example.hotelbooking.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    // Same Dockerfile as docker-compose.yml, so tests run against real PostGIS + pgvector.
    // deleteOnExit=false keeps the built image cached between runs.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() throws Exception {
        String image = new ImageFromDockerfile("hotelbooking-postgres-test:local", false)
                .withFileFromPath(".", Path.of("docker/postgres"))
                .get();
        return new PostgreSQLContainer<>(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new FakeEmbeddingModel();
    }
}
