package com.vineetha.career_network.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreatePersonRequest {

    private String name;
    private String headline;
    private String location;
    private String email;
    private String company;
    private List<String> skills;
}

