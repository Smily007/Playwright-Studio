package com.automation.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {
    public String name = "NewTest";
    public List<TestStep> steps = new ArrayList<>();

    public TestCase() {}
    public TestCase(String name) { this.name = name; }

    public void renumber() {
        for (int i = 0; i < steps.size(); i++) steps.get(i).stepNumber = i + 1;
    }

    @Override public String toString() { return name; }
}
