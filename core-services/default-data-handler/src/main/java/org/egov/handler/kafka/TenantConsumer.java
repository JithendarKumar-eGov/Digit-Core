package org.egov.handler.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.handler.config.ServiceConfiguration;
import org.egov.handler.service.DataHandlerService;
import org.egov.handler.util.OtpUtil;
import org.egov.handler.util.UserUtil;
import org.egov.handler.web.models.DefaultDataRequest;
import org.egov.handler.web.models.TenantRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Objects;

@Slf4j
@Component
public class TenantConsumer {

	private final ObjectMapper mapper;

	private final DataHandlerService dataHandlerService;

	private final UserUtil userUtil;

	private final OtpUtil otpUtil;

	private final ServiceConfiguration serviceConfig;

	@Autowired
	public TenantConsumer(ObjectMapper mapper, DataHandlerService dataHandlerService, UserUtil userUtil, OtpUtil otpUtil, ServiceConfiguration serviceConfig) {
		this.mapper = mapper;
		this.dataHandlerService = dataHandlerService;
		this.userUtil = userUtil;
		this.otpUtil = otpUtil;
		this.serviceConfig = serviceConfig;
	}

	@KafkaListener(topics = {"${kafka.topics.create.tenant}"})
	public void listen(final HashMap<String, Object> record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
		TenantRequest tenantRequest = mapper.convertValue(record, TenantRequest.class);

		// create user only for root tenant
		if (Objects.isNull(tenantRequest.getTenant().getParentId())) {
			log.info("Configuring Tenant: {}", tenantRequest.getTenant().getCode());

			DefaultDataRequest defaultDataRequest = DefaultDataRequest.builder()
					.requestInfo(tenantRequest.getRequestInfo())
					.targetTenantId(tenantRequest.getTenant().getCode())
					.schemaCodes(serviceConfig.getDefaultMdmsSchemaList())
					.locale(serviceConfig.getDefaultLocalizationLocale())
					.modules(serviceConfig.getDefaultLocalizationModuleList())
					.build();

			dataHandlerService.createDefaultData(defaultDataRequest);

			userUtil.createUser(tenantRequest);
			otpUtil.sendOtp(tenantRequest);
		}
	}
}
