package com.citas.medicas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jwt.secret=b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=")
class MedicasApplicationTests {

	@Test
	void contextLoads() {
	}

}
