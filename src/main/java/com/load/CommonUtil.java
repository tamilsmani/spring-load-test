package com.load;

import lombok.SneakyThrows;
import org.springframework.core.env.Environment;

public interface CommonUtil {

    String MOVE_TO_OUTBOUND_FOLDER_ENABLED  = "MOVE_TO_OUTBOUND_FOLDER_ENABLED";
    String MOVE_TO_ARCHIEVE_FOLDER_ENABLED = "MOVE_TO_ARCHIEVE_FOLDER_ENABLED";

    @SneakyThrows
    public static String getProperty(Environment environment, String keyName) {
        return environment.getProperty(keyName);
    }
}