package com.load;

import org.springframework.messaging.Message;

import java.io.File;

public interface MessageService {
    Message<File> processMessage(Message<File> message);
}
