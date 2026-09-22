package com.ktb.moyeota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MoyeotaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyeotaApplication.class, args);
    }

}
