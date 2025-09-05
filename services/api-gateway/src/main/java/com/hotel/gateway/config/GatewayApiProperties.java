package com.hotel.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "gateway.api")
@Getter
@Setter
public class GatewayApiProperties {

    private List<String> publicPaths = new ArrayList<>();
    private List<String> internalServicePaths = new ArrayList<>();
    private List<String> internalNetworkRanges = new ArrayList<>();

}