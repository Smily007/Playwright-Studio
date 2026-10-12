package com.automation.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectPageObject {
    public String className = "";
    public String origin = "";
    public List<String> testNames = new ArrayList<>();
}
