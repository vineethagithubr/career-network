package com.vineetha.career_network.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Person {

    private String id;
    private String name;
    private String headline;
    private String location;
    private String email;
    private String company;
    private List<String> skills;
}
