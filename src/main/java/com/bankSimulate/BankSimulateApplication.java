package com.bankSimulate;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankSimulateApplication {

    public static void main(String[] args){
        SpringApplication.run(BankSimulateApplication.class, args);
    }
}
