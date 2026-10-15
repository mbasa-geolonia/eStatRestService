package com.geolonia.estatrest;

import com.geolonia.estatrest.config.EstatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Packaged as a WAR (see pom.xml) so it can run either way:
 *  - standalone: {@code java -jar estatRest.war} (uses main() below, embedded Tomcat)
 *  - deployed into an external servlet container (e.g. standalone Tomcat's webapps/): the
 *    container discovers this class via SpringBootServletInitializer and calls configure()
 *    instead of main().
 */
@SpringBootApplication
@EnableConfigurationProperties(EstatProperties.class)
public class EstatRestApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(EstatRestApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(EstatRestApplication.class);
    }
}
