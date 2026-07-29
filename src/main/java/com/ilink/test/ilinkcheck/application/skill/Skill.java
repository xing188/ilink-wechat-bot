package com.ilink.test.ilinkcheck.application.skill;

public interface Skill {

    String id();

    String name();

    String description();

    String execute(String input);
}
