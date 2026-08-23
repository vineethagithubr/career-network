package com.vineetha.career_network.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionSuggestion {

    private String id;
    private String name;
    private String headline;
    private String company;
    private long mutualConnections;
    private long sharedSkills;
}

