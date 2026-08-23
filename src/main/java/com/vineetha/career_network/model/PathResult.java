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
public class PathResult {

    private List<Person> people;
    private int hops;
    private boolean connected;
}

