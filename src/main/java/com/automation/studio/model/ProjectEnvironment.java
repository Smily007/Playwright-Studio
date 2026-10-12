package com.automation.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectEnvironment {
    public String name = "";
    public String url = "";

    public ProjectEnvironment() {}

    public ProjectEnvironment(String name, String url) {
        this.name = name;
        this.url = url;
    }

    @Override
    public String toString() {
        return name;
    }
}
