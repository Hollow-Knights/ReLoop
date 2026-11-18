package br.com.fiap.reloop.persistence.postgres;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Profile("postgres")
@EntityScan(basePackageClasses = DatabaseRows.class)
@EnableJpaRepositories(basePackageClasses = JpaStores.class, considerNestedRepositories = true)
public class PostgresConfiguration { }
