package br.com.gestaofrota.infra.config;

import br.com.gestaofrota.application.reports.ReportLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportLimits.class)
public class ReportConfiguration { }
