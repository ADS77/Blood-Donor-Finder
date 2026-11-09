package com.bd.blooddonerfinder.config.elastic_config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@EnableElasticsearchRepositories(basePackages = "com.bd.blooddonerfinder.model.es.documents")
public class ElasticSearchConfig {
    @Bean
    public ElasticsearchClient elasticsearchClient(){
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200,"http")
        ).build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );
        return  new co.elastic.clients.elasticsearch.ElasticsearchClient(transport);
    }

    @Bean
    public ObjectMapper objectMapper(){
        return new ObjectMapper();
    }
}
