package com.factory.anomaly;

import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;

@SpringBootTest
@ActiveProfiles("test")
class AnomalyServiceApplicationTests {

	@MockBean
	private SensorRedisRepository sensorRedisRepository;

	@Autowired
	private AnomalyRepository anomalyRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void testFetchAnomalies() {
		try {
			System.out.println("--- CALLING FETCH ANOMALIES WITH CONDITION ---");
			Page<AnomalyResponse> page = anomalyRepository.fetchAnomaliesWithCondition(
				null, null, null, PageRequest.of(0, 10)
			);
			System.out.println("Result count: " + page.getTotalElements());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

