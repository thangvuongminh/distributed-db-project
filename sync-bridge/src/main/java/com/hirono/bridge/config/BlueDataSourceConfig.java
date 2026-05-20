package com.hirono.bridge.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
    basePackages = "com.hirono.bridge.repository.blue",
    entityManagerFactoryRef = "blueEntityManagerFactory",
    transactionManagerRef = "blueTransactionManager"
)
public class BlueDataSourceConfig {

  @Primary
  @Bean(name = "blueDataSource")
  @ConfigurationProperties(prefix = "bridge.datasource.blue")
  public DataSource blueDataSource() {
    return DataSourceBuilder.create().build();
  }

  @Primary
  @Bean(name = "blueEntityManagerFactory")
  public LocalContainerEntityManagerFactoryBean blueEntityManagerFactory(
      @Qualifier("blueDataSource") DataSource dataSource) {
    LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
    em.setDataSource(dataSource);
    em.setPackagesToScan("com.hirono.bridge.entity.blue");
    em.setPersistenceUnitName("blue");

    org.hibernate.jpa.HibernatePersistenceProvider provider = new org.hibernate.jpa.HibernatePersistenceProvider();
    em.setPersistenceProvider(provider);

    Map<String, Object> props = new HashMap<>();
    props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
    props.put("hibernate.hbm2ddl.auto", "none");
    props.put("hibernate.show_sql", "false");
    em.setJpaPropertyMap(props);

    return em;
  }

  @Primary
  @Bean(name = "blueTransactionManager")
  public PlatformTransactionManager blueTransactionManager(
      @Qualifier("blueEntityManagerFactory") EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}