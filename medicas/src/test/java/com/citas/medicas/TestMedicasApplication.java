package com.citas.medicas;

import org.springframework.boot.SpringApplication;

public class TestMedicasApplication {

	public static void main(String[] args) {
		SpringApplication.from(MedicasApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
