package com.ilink.test.ilinkcheck.application.agent.spi.monitor;

public interface AlertNotifier {

    void notify(String userId, String message);
}
