package com.hirono.bridge.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.hirono.bridge.repository.green",
    entityManagerFactoryRef = "greenEntityManagerFactory",
    transactionManagerRef = "greenTransactionManager"
)
public class GreenDataSourceConfig {

  @Bean(name = "greenDataSource")
  @ConfigurationProperties(prefix = "bridge.datasource.green")
  public DataSource greenDataSource() {
    return DataSourceBuilder.create().build();
  }

  @Bean(name = "greenEntityManagerFactory")
  public LocalContainerEntityManagerFactoryBean greenEntityManagerFactory(
      @Qualifier("greenDataSource") DataSource dataSource) {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setPackagesToScan("com.hirono.bridge.entity.green");
    em.setPersistenceUnitName("green");

    org.hibernate.jpa.HibernatePersistenceProvider provider = new org.hibernate.jpa.HibernatePersistenceProvider();
    em.setPersistenceProvider(provider);

    Map<String, Object> props = new HashMap<>();
    props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
    props.put("hibernate.hbm2ddl.auto", "none");
    props.put("hibernate.show_sql", "false");
    em.setJpaPropertyMap(props);

    return em;
  }

  @Bean(name = "greenTransactionManager")
  public PlatformTransactionManager greenTransactionManager(
      @Qualifier("greenEntityManagerFactory") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}