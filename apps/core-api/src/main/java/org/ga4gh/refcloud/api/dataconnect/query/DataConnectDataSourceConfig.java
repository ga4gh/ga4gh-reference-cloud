package org.ga4gh.refcloud.api.dataconnect.query;

import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataConnectDataSourceConfig {

    public static final String DATA_SOURCE = "dataConnectDataSource";

    // connection pool for the restricted, read-only data_connect_reader role that runs
    // researcher-submitted SQL. defaultCandidate = false keeps it out of by-type autowiring, so
    // Spring Boot still auto-configures the primary DataSource used by JPA; inject it with
    // @Qualifier(DATA_SOURCE). The pool starts lazily on first use.
    @Bean(name = DATA_SOURCE, defaultCandidate = false)
    public HikariDataSource dataConnectDataSource(DataConnectConfig dataConnectConfig) {
        DataConnectConfig.Datasource datasource = dataConnectConfig.datasource();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("data-connect");
        dataSource.setJdbcUrl(datasource.url());
        dataSource.setUsername(datasource.username());
        dataSource.setPassword(datasource.password());
        dataSource.setMaximumPoolSize(datasource.maximumPoolSize());
        dataSource.setReadOnly(true);
        return dataSource;
    }
}
