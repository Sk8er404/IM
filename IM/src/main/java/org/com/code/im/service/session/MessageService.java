package org.com.code.im.service.session;

import org.com.code.im.pojo.Messages;

import java.util.List;

public interface MessageService {
    void insertBatchMsg(List<Messages> messages);
}
