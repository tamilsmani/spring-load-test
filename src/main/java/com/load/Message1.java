package com.load;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration("custommsg")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Message1 {

    Message1() {
        System.out.println("message");
    }

}
