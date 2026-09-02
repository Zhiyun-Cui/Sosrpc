package com.achingsoul.example.common.model;

import java.io.Serializable;

// Objects transferred over RPC must be serializable.

public class User implements Serializable {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        }
}
