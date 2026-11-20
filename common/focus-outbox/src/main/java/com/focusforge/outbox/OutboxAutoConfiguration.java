package com.focusforge.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Transactional outbox for any service that depends on focus-outbox.
 * <p>
 * {@code @AutoConfigurationPackage} adds this package to the JPA entity and
 * repository scan (which otherwise only covers the application's own package),
 * so it must run before the JPA auto-configurations. Each service still owns
 * the {@code outbox_events} table in its own Flyway migrations.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@EnableScheduling
@Import({Outbox.class, OutboxRelay.class})
public class OutboxAutoConfiguration {
}
